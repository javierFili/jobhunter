package com.jobhunter.optimizer.domain;

import com.jobhunter.profiles.application.ProfileSnapshotService.Snapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factuality validator for AI-generated resume/letter text (AI_DESIGN §6.3). It never trusts the model:
 * references must exist, projects never become employment, technologies must be verified skills (professional evidence
 * inside employment entries), numbers must appear in the cited facts, and years of experience cannot exceed the
 * computed ones. Anything that fails becomes SUGGESTED with a human-readable note — it is never silently accepted.
 */
public class FactChecker {

    public record Violation(String rule, String section, String ref, String text, String message) {
    }

    public record Result(boolean grounded, List<String> validRefs, String note, List<Violation> violations) {
    }

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d.,])\\d+(?:[.,]\\d+)?%?(?![\\p{L}\\d])");
    private static final Pattern YEARS = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*\\+?\\s*(?:years?|yrs?|años?|anni|anno|jahre|jahren|ans?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final Snapshot snapshot;
    private final Set<String> refs;
    private final Map<String, String> refTexts = new HashMap<>();
    private final Map<Long, String> skillEvidence = new HashMap<>();
    private final Function<String, List<Long>> techFinder;
    private final BigDecimal maxYears;

    public FactChecker(Snapshot snapshot, Function<String, List<Long>> techFinder) {
        this.snapshot = snapshot;
        this.refs = snapshot.allRefs();
        this.techFinder = techFinder;
        BigDecimal max = snapshot.totalProfessionalYears() == null ? BigDecimal.ZERO : snapshot.totalProfessionalYears();
        for (var e : snapshot.experiences()) {
            String years = (e.startDate() == null ? "" : e.startDate().getYear() + " ")
                    + (e.endDate() == null ? "" : e.endDate().getYear() + " ");
            refTexts.put(e.ref(), String.join(" ", nz(e.company()), nz(e.title()), nz(e.description()), nz(e.location()), years));
            e.achievements().forEach(a -> refTexts.put(a.ref(), a.text()));
        }
        for (var p : snapshot.projects()) {
            refTexts.put(p.ref(), String.join(" ", nz(p.name()), nz(p.role()), nz(p.description())));
            p.highlights().forEach(h -> refTexts.put(h.ref(), h.text()));
        }
        for (var s : snapshot.skills()) {
            long id = Long.parseLong(s.ref().substring("skill:".length()));
            skillEvidence.put(id, s.evidence());
            refTexts.put(s.ref(), s.name() + " " + (s.yearsProfessional() == null ? "" : s.yearsProfessional().toPlainString()));
            if (s.yearsProfessional() != null && s.yearsProfessional().compareTo(max) > 0) max = s.yearsProfessional();
        }
        snapshot.educations().forEach(e -> refTexts.put(e.ref(), String.join(" ", nz(e.institution()), nz(e.degree()), nz(e.field()))));
        snapshot.certifications().forEach(c -> refTexts.put(c.ref(), String.join(" ", nz(c.name()), nz(c.issuer()))));
        snapshot.languages().forEach(l -> refTexts.put(l.ref(), l.code() + " " + l.level()));
        if (snapshot.summary() != null) refTexts.put("profile:summary", snapshot.summary());
        if (snapshot.headline() != null) refTexts.put("profile:headline", snapshot.headline());
        this.maxYears = max;
    }

    /**
     * @param employment true when the text lives inside an employment entry (projects and non-professional skills
     *                   are then not acceptable evidence)
     */
    public Result check(String section, String entryRef, String text, List<String> claimedRefs, boolean employment) {
        List<Violation> violations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> valid = new LinkedHashSet<>();
        for (String r : claimedRefs == null ? List.<String>of() : claimedRefs) {
            if (!refs.contains(r)) {
                violations.add(new Violation("UNKNOWN_REF", section, r, text, "La referencia " + r + " no existe en tu perfil verificado"));
            } else if (employment && (r.startsWith("prj:") || r.startsWith("hl:"))) {
                violations.add(new Violation("PROJECT_AS_EMPLOYMENT", section, r, text,
                        "Un proyecto no puede usarse como evidencia de empleo"));
                notes.add("usa un proyecto como si fuera empleo");
            } else {
                valid.add(r);
            }
        }
        if (entryRef != null && refs.contains(entryRef)) valid.add(entryRef);
        boolean hasRealSource = valid.stream().anyMatch(r -> !r.equals(entryRef))
                || (entryRef != null && claimedRefs != null && claimedRefs.contains(entryRef));
        if (!hasRealSource) {
            violations.add(new Violation("NO_SOURCE", section, entryRef, text, "La frase no cita ningún hecho verificado"));
            notes.add("no cita ningún hecho de tu perfil");
        }

        // technologies
        for (Long skillId : techFinder.apply(text)) {
            String evidence = skillEvidence.get(skillId);
            if (evidence == null) {
                violations.add(new Violation("UNVERIFIED_TECHNOLOGY", section, "skill:" + skillId, text,
                        "Menciona una tecnología que no está en tu perfil verificado"));
                notes.add("menciona una tecnología que no está en tu perfil verificado");
            } else if (employment && !"PROFESSIONAL".equals(evidence)) {
                violations.add(new Violation("UNVERIFIED_TECHNOLOGY", section, "skill:" + skillId, text,
                        "Tecnología sin experiencia profesional dentro de un empleo"));
                notes.add("atribuye a un empleo una tecnología que solo usaste fuera del trabajo");
            }
        }

        // years of experience
        Matcher y = YEARS.matcher(text);
        while (y.find()) {
            BigDecimal claimed = new BigDecimal(y.group(1).replace(',', '.'));
            if (claimed.compareTo(maxYears.add(new BigDecimal("0.5"))) > 0) {
                violations.add(new Violation("YEARS_EXCEEDED", section, null, text,
                        "Afirma " + claimed.stripTrailingZeros().toPlainString() + " años; tu perfil muestra " + maxYears));
                notes.add("afirma más años de experiencia de los calculados (" + maxYears + ")");
            }
        }

        // numbers and metrics must appear in the cited facts
        StringBuilder sources = new StringBuilder();
        valid.forEach(r -> sources.append(' ').append(refTexts.getOrDefault(r, "")));
        // compare whole numbers (token by token), never substrings: "2" must not be "found" inside "2022"
        Set<String> sourceNumbers = new java.util.HashSet<>();
        Matcher sm = NUMBER.matcher(sources);
        while (sm.find()) sourceNumbers.add(normalizeNumber(sm.group()));
        Matcher n = NUMBER.matcher(text);
        while (n.find()) {
            String num = n.group();
            if (YEARS.matcher(text.substring(n.start())).lookingAt()) continue; // handled by the years rule
            if (!sourceNumbers.contains(normalizeNumber(num))) {
                violations.add(new Violation("UNSUPPORTED_NUMBER", section, null, text,
                        "El número «" + num + "» no aparece en los hechos citados"));
                notes.add("incluye la cifra «" + num + "», que no aparece en tus datos");
            }
        }
        boolean grounded = notes.isEmpty() && violations.stream().noneMatch(v -> v.rule().equals("NO_SOURCE"));
        String note = notes.isEmpty() ? null : capitalize(String.join("; ", new LinkedHashSet<>(notes)));
        return new Result(grounded, List.copyOf(valid), note, violations);
    }

    static String normalizeNumber(String n) {
        return n.replace("%", "").replace(',', '.');
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
