package com.jobhunter.optimizer.domain;

import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.profiles.application.ProfileSnapshotService.Contact;
import com.jobhunter.profiles.application.ProfileSnapshotService.Snapshot;
import com.jobhunter.resumes.domain.ResumeDocument;
import com.jobhunter.resumes.domain.ResumeDocument.Bullet;
import com.jobhunter.resumes.domain.ResumeDocument.BulletStatus;
import com.jobhunter.resumes.domain.ResumeLabels;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the optimized resume: the SKELETON (companies, titles, dates, education, certifications, languages, skill
 * names) always comes from verified facts; from the AI only the headline, summary, bullet wording and ordering are
 * taken — each checked by {@link FactChecker}. Failing bullets stay visible as SUGGESTED with a note.
 */
public final class OptimizedResumeAssembler {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<String> CATEGORY_ORDER = List.of("PROGRAMMING_LANGUAGE", "FRAMEWORK", "DATABASE", "CLOUD",
            "TOOL", "METHODOLOGY", "OTHER");

    public record Report(String provider, String model, Long jobOfferId, int groundedCount, int suggestedCount,
                         List<FactChecker.Violation> violations) {
    }

    public record Assembled(ResumeDocument document, Report report) {
    }

    private OptimizedResumeAssembler() {
    }

    public static Assembled assemble(String fullName, Snapshot s, Contact c, String language, AiOutputs.CvOptimize ai,
                                     FactChecker checker, String provider, String model, Long jobOfferId) {
        List<FactChecker.Violation> violations = new ArrayList<>();
        int[] counts = new int[2];

        // headline & summary: only kept when grounded; otherwise the verified profile text is used
        String headline = s.headline();
        var h = checker.check("headline", null, ai.headline(), List.of(), false);
        var substantive = h.violations().stream().filter(v -> !v.rule().equals("NO_SOURCE")).toList();
        if (substantive.isEmpty()) {
            headline = ai.headline();
        } else {
            violations.addAll(substantive);
        }
        String summary = s.summary();
        var sum = checker.check("summary", null, ai.summary().text(), ai.summary().sourceRefs(), false);
        if (sum.grounded()) {
            summary = ai.summary().text();
        } else {
            violations.addAll(sum.violations());
            violations.add(new FactChecker.Violation("DROPPED_ENTRY", "summary", null, ai.summary().text(),
                    "Se mantuvo tu resumen verificado porque el propuesto no pudo verificarse: " + sum.note()));
        }

        List<ResumeDocument.Link> links = new ArrayList<>();
        if (c.linkedinUrl() != null) links.add(new ResumeDocument.Link("LinkedIn", c.linkedinUrl()));
        if (c.githubUrl() != null) links.add(new ResumeDocument.Link("GitHub", c.githubUrl()));
        if (c.portfolioUrl() != null) links.add(new ResumeDocument.Link("Portfolio", c.portfolioUrl()));
        if (c.websiteUrl() != null) links.add(new ResumeDocument.Link("Web", c.websiteUrl()));
        String location = c.city() == null ? c.country() : (c.country() == null ? c.city() : c.city() + ", " + c.country());
        var basics = new ResumeDocument.Basics(fullName, headline, c.email(), c.phone(), location, links);

        // experiences: all verified employment, chronological as in the profile; AI bullets checked
        Map<String, AiOutputs.EntryBullets> aiExp = new LinkedHashMap<>();
        for (var e : ai.experiences()) {
            if (e.ref().startsWith("prj:")) {
                violations.add(new FactChecker.Violation("PROJECT_AS_EMPLOYMENT", "experience", e.ref(), null,
                        "La IA intentó presentar un proyecto como experiencia laboral; se descartó"));
            } else if (s.experiences().stream().noneMatch(x -> x.ref().equals(e.ref()))) {
                violations.add(new FactChecker.Violation("DROPPED_ENTRY", "experience", e.ref(), null,
                        "Experiencia inexistente en tu perfil; se descartó"));
            } else {
                aiExp.put(e.ref(), e);
            }
        }
        List<ResumeDocument.ExperienceEntry> experience = new ArrayList<>();
        for (var e : s.experiences()) {
            List<Bullet> bullets = new ArrayList<>();
            var proposal = aiExp.get(e.ref());
            if (proposal != null && !proposal.bullets().isEmpty()) {
                for (var b : proposal.bullets()) bullets.add(bullet(checker, "experience", e.ref(), b, true, violations, counts));
            } else {
                if (e.description() != null) bullets.add(grounded(e.description(), e.ref(), counts));
                e.achievements().forEach(a -> bullets.add(grounded(a.text(), a.ref(), counts)));
            }
            experience.add(new ResumeDocument.ExperienceEntry(e.ref(), e.company(), e.title(),
                    e.location() != null ? e.location() : e.country(), ym(e.startDate()), ym(e.endDate()), e.current(), bullets));
        }

        // projects: AI order (verified prj refs only); when the AI lists none, all verified projects are kept
        List<ResumeDocument.ProjectEntry> projects = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (var p : ai.projects()) {
            var fact = s.projects().stream().filter(x -> x.ref().equals(p.ref())).findFirst().orElse(null);
            if (fact == null) {
                violations.add(new FactChecker.Violation("DROPPED_ENTRY", "projects", p.ref(), null, "Proyecto inexistente en tu perfil; se descartó"));
                continue;
            }
            if (!used.add(fact.ref())) continue;
            List<Bullet> bullets = new ArrayList<>();
            for (var b : p.bullets()) bullets.add(bullet(checker, "projects", fact.ref(), b, false, violations, counts));
            projects.add(new ResumeDocument.ProjectEntry(fact.ref(), fact.kind(), fact.name(), fact.role(), fact.url(), bullets));
        }
        if (projects.isEmpty()) {
            for (var p : s.projects()) {
                List<Bullet> bullets = new ArrayList<>();
                if (p.description() != null) bullets.add(grounded(p.description(), p.ref(), counts));
                p.highlights().forEach(hl -> bullets.add(grounded(hl.text(), hl.ref(), counts)));
                projects.add(new ResumeDocument.ProjectEntry(p.ref(), p.kind(), p.name(), p.role(), p.url(), bullets));
            }
        }

        // skills: verified (non-LEARNING) skills, relevance order from the AI, grouped by category
        Map<String, Integer> rank = new LinkedHashMap<>();
        for (int i = 0; i < ai.skillOrder().size(); i++) rank.putIfAbsent(ai.skillOrder().get(i), i);
        Map<String, List<ResumeDocument.SkillEntry>> grouped = new LinkedHashMap<>();
        CATEGORY_ORDER.forEach(cat -> grouped.put(cat, new ArrayList<>()));
        s.skills().stream().filter(sk -> !"LEARNING".equals(sk.evidence()))
                .sorted((a, b) -> Integer.compare(rank.getOrDefault(a.ref(), 1000), rank.getOrDefault(b.ref(), 1000)))
                .forEach(sk -> grouped.computeIfAbsent(sk.category() == null ? "OTHER" : sk.category(), k -> new ArrayList<>())
                        .add(new ResumeDocument.SkillEntry(sk.ref(), sk.name(), sk.evidence())));
        // categories ordered by their best-ranked skill
        List<Map.Entry<String, List<ResumeDocument.SkillEntry>>> groups = new ArrayList<>(grouped.entrySet().stream()
                .filter(en -> !en.getValue().isEmpty()).toList());
        groups.sort((a, b) -> Integer.compare(rank.getOrDefault(a.getValue().get(0).ref(), 1000), rank.getOrDefault(b.getValue().get(0).ref(), 1000)));
        List<ResumeDocument.SkillGroup> skills = groups.stream()
                .map(en -> new ResumeDocument.SkillGroup(ResumeLabels.get(language, en.getKey()), en.getValue())).toList();

        var education = s.educations().stream().map(e -> new ResumeDocument.EducationEntry(e.ref(), e.institution(), e.degree(),
                e.field(), ym(e.startDate()), ym(e.endDate()), e.status())).toList();
        var certifications = s.certifications().stream().map(x -> new ResumeDocument.CertificationEntry(x.ref(), x.name(),
                x.issuer(), ym(x.issuedAt()), x.url())).toList();
        var languages = s.languages().stream().map(l -> new ResumeDocument.LanguageEntry(l.ref(), l.code(), l.level())).toList();

        var doc = new ResumeDocument(basics, summary, experience, projects, education, certifications, skills, languages);
        return new Assembled(doc, new Report(provider, model, jobOfferId, counts[0], counts[1], violations));
    }

    private static Bullet bullet(FactChecker checker, String section, String entryRef, AiOutputs.RefText b, boolean employment,
                                 List<FactChecker.Violation> violations, int[] counts) {
        var r = checker.check(section, entryRef, b.text(), b.sourceRefs(), employment);
        violations.addAll(r.violations());
        if (r.grounded()) {
            counts[0]++;
            return new Bullet(b.text(), r.validRefs(), BulletStatus.GROUNDED, null);
        }
        counts[1]++;
        return new Bullet(b.text(), r.validRefs(), BulletStatus.SUGGESTED, r.note());
    }

    private static Bullet grounded(String text, String ref, int[] counts) {
        counts[0]++;
        return new Bullet(text, List.of(ref), BulletStatus.GROUNDED, null);
    }

    private static String ym(LocalDate d) {
        return d == null ? null : d.format(YM);
    }
}
