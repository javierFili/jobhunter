package com.jobhunter.optimizer.application;

import com.jobhunter.ai.application.AiGateway;
import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.ai.domain.UntrustedText;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.matching.application.JobAnalysisService;
import com.jobhunter.optimizer.domain.FactChecker;
import com.jobhunter.optimizer.domain.OptimizedResumeAssembler;
import com.jobhunter.profiles.application.ProfileSnapshotService;
import com.jobhunter.profiles.application.SkillDictionary;
import com.jobhunter.resumes.application.ResumeService;
import com.jobhunter.resumes.domain.Resume;
import com.jobhunter.resumes.domain.ResumeLabels;
import com.jobhunter.resumes.domain.ResumeVersion;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.application.UserService;
import com.jobhunter.users.domain.ConsentType;
import org.springframework.stereotype.Service;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * "Optimizar mi CV" for one job: verified facts (no contact data) + job requirements → AI proposal (route CV_OPTIMIZE,
 * consent AI_PROCESS_PROFILE) → {@link FactChecker} + {@link OptimizedResumeAssembler} → new DRAFT version
 * (origin OPTIMIZED) with its validation report. Approval stays blocked while SUGGESTED bullets remain.
 */
@Service
public class ResumeOptimizerService {

    private final ResumeService resumes;
    private final ProfileSnapshotService snapshots;
    private final JobQueryService jobs;
    private final JobAnalysisService analysis;
    private final AiGateway gateway;
    private final SkillDictionary dictionary;
    private final UserService users;

    public ResumeOptimizerService(ResumeService resumes, ProfileSnapshotService snapshots, JobQueryService jobs,
                                  JobAnalysisService analysis, AiGateway gateway, SkillDictionary dictionary, UserService users) {
        this.resumes = resumes;
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.analysis = analysis;
        this.gateway = gateway;
        this.dictionary = dictionary;
        this.users = users;
    }

    public ResumeVersion optimize(long userId, long jobId, long resumeId, String language, Long profileId) {
        if (!ResumeLabels.LANGUAGES.contains(language)) throw ApiException.badRequest("VALIDATION_FAILED", "Idioma no soportado");
        Resume resume = resumes.get(userId, resumeId);
        JobOffer job = jobs.get(jobId);
        var sc = snapshots.build(userId, profileId != null ? profileId : resume.getProfileId());
        if (sc.snapshot().experiences().isEmpty() && sc.snapshot().projects().isEmpty()) {
            throw ApiException.badRequest("PROFILE_EMPTY", "Tu perfil no tiene experiencias ni proyectos verificados");
        }
        analysis.analyze(userId, jobId, false); // cached; gives structured requirements
        var reqs = analysis.requirements(jobId).stream()
                .map(r -> Map.of("kind", r.getKind(), "importance", r.getImportance(), "text", r.getText())).toList();
        Map<String, String> vars = Map.of(
                "language", language,
                "profile", Json.write(sc.snapshot()),
                "requirements", Json.write(reqs),
                "posting", UntrustedText.wrap("job_posting", job.getDescriptionText(), 12_000));
        var result = gateway.run(new AiGateway.Task<>("CV_OPTIMIZE", userId, "RESUME", resumeId, vars,
                sc.snapshot().hash() + "|" + HexFormat.of().formatHex(job.getContentHash()) + "|" + language,
                ConsentType.AI_PROCESS_PROFILE, AiOutputs.CvOptimize.class, false));
        FactChecker checker = new FactChecker(sc.snapshot(),
                text -> dictionary.find(text).stream().map(SkillDictionary.Match::skillId).toList());
        var assembled = OptimizedResumeAssembler.assemble(users.get(userId).getFullName(), sc.snapshot(), sc.contact(),
                language, result.output(), checker, result.analysis().getProvider(), result.analysis().getModel(), jobId);
        String note = "Optimizado para «" + truncate(job.getTitle(), 80) + "» (" + truncate(job.getCompanyName(), 60) + ")"
                + (result.output().injectionSuspected() ? " · la oferta contenía instrucciones para la IA" : "");
        return resumes.addGeneratedVersion(userId, resumeId, ResumeVersion.Origin.OPTIMIZED, jobId, language,
                assembled.document(), result.analysis().getId(), Json.write(assembled.report()), note);
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n) + "…";
    }

    public List<String> languages() {
        return ResumeLabels.LANGUAGES;
    }
}
