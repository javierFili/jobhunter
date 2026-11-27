package com.jobhunter.coverletters.application;

import com.jobhunter.ai.application.AiGateway;
import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.ai.domain.UntrustedText;
import com.jobhunter.coverletters.domain.CoverLetter;
import com.jobhunter.coverletters.infrastructure.CoverLetterRepository;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.matching.application.JobAnalysisService;
import com.jobhunter.optimizer.domain.FactChecker;
import com.jobhunter.profiles.application.ProfileSnapshotService;
import com.jobhunter.profiles.application.SkillDictionary;
import com.jobhunter.resumes.domain.ResumeLabels;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.application.UserService;
import com.jobhunter.users.domain.ConsentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application texts (5 formats, 5 languages) generated from VERIFIED facts only and checked paragraph by paragraph.
 * Substantive unverifiable claims become suggestions that block approval. The user's name/contact is appended
 * locally (never sent to the AI). Every edit creates a new version.
 */
@Service
public class CoverLetterService {

    public record Suggestion(String text, String note) {
    }

    public record Report(int groundedCount, int suggestedCount, List<FactChecker.Violation> violations) {
    }

    private static final TypeReference<List<Suggestion>> SUGGESTIONS = new TypeReference<>() {
    };

    private final CoverLetterRepository letters;
    private final ProfileSnapshotService snapshots;
    private final JobQueryService jobs;
    private final JobAnalysisService analysis;
    private final AiGateway gateway;
    private final SkillDictionary dictionary;
    private final UserService users;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CoverLetterService(CoverLetterRepository letters, ProfileSnapshotService snapshots, JobQueryService jobs,
                              JobAnalysisService analysis, AiGateway gateway, SkillDictionary dictionary, UserService users,
                              PlatformTransactionManager txManager, Clock clock) {
        this.letters = letters;
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.analysis = analysis;
        this.gateway = gateway;
        this.dictionary = dictionary;
        this.users = users;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public CoverLetter generate(long userId, long jobId, String format, String language, String tone, Long profileId) {
        if (!CoverLetter.FORMATS.contains(format)) throw ApiException.badRequest("VALIDATION_FAILED", "Formato no válido");
        if (!ResumeLabels.LANGUAGES.contains(language)) throw ApiException.badRequest("VALIDATION_FAILED", "Idioma no soportado");
        String t = tone == null ? "NEUTRAL" : tone.toUpperCase();
        if (!CoverLetter.TONES.contains(t)) throw ApiException.badRequest("VALIDATION_FAILED", "Tono no válido");
        JobOffer job = jobs.get(jobId);
        var sc = snapshots.build(userId, profileId);
        analysis.analyze(userId, jobId, false);
        var reqs = analysis.requirements(jobId).stream()
                .map(r -> Map.of("kind", r.getKind(), "importance", r.getImportance(), "text", r.getText())).toList();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("format", format);
        vars.put("tone", t);
        vars.put("language", language);
        vars.put("company", oneLine(job.getCompanyName()));
        vars.put("title", oneLine(job.getTitle()));
        vars.put("profile", Json.write(sc.snapshot()));
        vars.put("requirements", Json.write(reqs));
        vars.put("posting", UntrustedText.wrap("job_posting", job.getDescriptionText(), 10_000));
        var result = gateway.run(new AiGateway.Task<>("COVER_LETTER", userId, "COVER_LETTER", jobId, vars,
                sc.snapshot().hash() + "|" + HexFormat.of().formatHex(job.getContentHash()) + "|" + format + "|" + language + "|" + t,
                ConsentType.AI_PROCESS_PROFILE, AiOutputs.CoverLetter.class, true));

        FactChecker checker = new FactChecker(sc.snapshot(),
                text -> dictionary.find(text).stream().map(SkillDictionary.Match::skillId).toList());
        List<Suggestion> suggestions = new ArrayList<>();
        List<FactChecker.Violation> violations = new ArrayList<>();
        int grounded = 0;
        List<String> paragraphs = new ArrayList<>();
        for (var p : result.output().paragraphs()) {
            var r = checker.check("letter", null, p.text(), p.sourceRefs(), false);
            var substantive = r.violations().stream().filter(v -> !v.rule().equals("NO_SOURCE")).toList();
            if (substantive.isEmpty()) {
                grounded++;
            } else {
                violations.addAll(substantive);
                suggestions.add(new Suggestion(p.text(), r.note()));
            }
            paragraphs.add(p.text().trim());
        }
        String name = users.get(userId).getFullName();
        StringBuilder body = new StringBuilder();
        if (!result.output().greeting().isBlank()) body.append(result.output().greeting().trim()).append("\n\n");
        body.append(String.join("\n\n", paragraphs));
        if (!result.output().closing().isBlank()) body.append("\n\n").append(result.output().closing().trim()).append("\n").append(name);
        String subject = "FORMAL_LETTER".equals(format) || "EMAIL_INTRO".equals(format) || "FREELANCE_PROPOSAL".equals(format)
                ? result.output().subject() : null;
        Report report = new Report(grounded, suggestions.size(), violations);
        String provider = result.analysis().getProvider();
        String model = result.analysis().getModel();
        Long aiId = result.analysis().getId();
        return tx.execute(s -> {
            CoverLetter c = letters.save(new CoverLetter(userId, null, jobId, format, language, t, 1, null, subject,
                    body.toString(), Json.write(suggestions), Json.write(report), aiId, provider, model));
            c.setGroup(c.getId());
            return c;
        });
    }

    @Transactional(readOnly = true)
    public Page<CoverLetter> list(long userId, Long jobId, String format, String status, int page, int size) {
        return letters.latestPerGroup(userId, jobId, format, status, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @Transactional(readOnly = true)
    public CoverLetter get(long userId, long id) {
        return letters.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Carta"));
    }

    @Transactional(readOnly = true)
    public List<CoverLetter> versions(long userId, long id) {
        return letters.findByLetterGroupIdOrderByVersionNoDesc(get(userId, id).getLetterGroupId());
    }

    /** User edit → new DRAFT version; the user is responsible for edited content, so suggestions are cleared. */
    @Transactional
    public CoverLetter edit(long userId, long id, String subject, String body) {
        CoverLetter base = get(userId, id);
        if (body == null || body.isBlank() || body.length() > 20_000) {
            throw ApiException.badRequest("VALIDATION_FAILED", "El texto es obligatorio (máx. 20.000 caracteres)");
        }
        if (subject != null && subject.length() > 300) throw ApiException.badRequest("VALIDATION_FAILED", "Asunto demasiado largo");
        int next = letters.maxVersion(base.getLetterGroupId()) + 1;
        return letters.save(new CoverLetter(userId, base.getLetterGroupId(), base.getJobOfferId(), base.getFormat(),
                base.getLanguage(), base.getTone(), next, base.getId(), subject == null ? base.getSubject() : subject.trim(),
                body.trim(), "[]", null, null, base.getProvider(), base.getModel()));
    }

    @Transactional
    public CoverLetter approve(long userId, long id) {
        CoverLetter c = get(userId, id);
        if (!suggestions(c).isEmpty()) {
            throw ApiException.badRequest("UNCONFIRMED_SUGGESTIONS",
                    "Hay frases sugeridas sin revisar. Edita el texto (acepta o quita cada sugerencia) y guarda una nueva versión antes de aprobar");
        }
        c.approve(clock.instant());
        return c;
    }

    @Transactional
    public void delete(long userId, long id) {
        CoverLetter c = get(userId, id);
        letters.deleteGroup(c.getLetterGroupId(), userId);
    }

    public static List<Suggestion> suggestions(CoverLetter c) {
        List<Suggestion> s = Json.read(c.getSuggestions(), SUGGESTIONS);
        return s == null ? List.of() : s;
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").replaceAll("[<>]", "");
    }
}
