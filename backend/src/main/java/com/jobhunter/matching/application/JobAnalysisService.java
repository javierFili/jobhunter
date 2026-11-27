package com.jobhunter.matching.application;

import com.jobhunter.ai.application.AiGateway;
import com.jobhunter.ai.domain.AiAnalysis;
import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.ai.domain.UntrustedText;
import com.jobhunter.ai.infrastructure.AiAnalysisRepository;
import com.jobhunter.jobs.domain.JobClassification;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.JobRequirement;
import com.jobhunter.jobs.infrastructure.JobOfferRepository;
import com.jobhunter.jobs.infrastructure.JobRequirementRepository;
import com.jobhunter.profiles.application.SkillDictionary;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.profiles.infrastructure.SkillRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * AI analysis of one job posting (no personal data is sent → no consent needed). Results are cached by job content
 * hash + prompt version; requirements are stored in job_requirements. Eligibility is only upgraded from UNKNOWN when
 * the model's evidence quote appears VERBATIM in the posting (the AI cannot invent eligibility).
 */
@Service
public class JobAnalysisService {

    static final int MAX_POSTING_CHARS = 12_000;

    private final AiGateway gateway;
    private final JobOfferRepository offers;
    private final JobRequirementRepository requirements;
    private final SkillDictionary dictionary;
    private final SkillRepository skills;
    private final AiAnalysisRepository analyses;
    private final TransactionTemplate tx;

    public JobAnalysisService(AiGateway gateway, JobOfferRepository offers, JobRequirementRepository requirements,
                              SkillDictionary dictionary, SkillRepository skills, AiAnalysisRepository analyses,
                              PlatformTransactionManager txManager) {
        this.gateway = gateway;
        this.offers = offers;
        this.requirements = requirements;
        this.dictionary = dictionary;
        this.skills = skills;
        this.analyses = analyses;
        this.tx = new TransactionTemplate(txManager);
    }

    public AiGateway.Result<AiOutputs.JobAnalysis> analyze(Long userId, long offerId, boolean force) {
        JobOffer o = offers.findById(offerId).orElseThrow(() -> ApiException.notFound("Oferta"));
        Map<String, String> vars = Map.of(
                "title", oneLine(o.getTitle()),
                "company", oneLine(o.getCompanyName()),
                "location", oneLine(o.getLocationText()),
                "posting", UntrustedText.wrap("job_posting", o.getDescriptionText(), MAX_POSTING_CHARS));
        var result = gateway.run(new AiGateway.Task<>("JOB_ANALYSIS", userId, "JOB_OFFER", offerId, vars,
                HexFormat.of().formatHex(o.getContentHash()), null, AiOutputs.JobAnalysis.class, force));
        tx.executeWithoutResult(s -> apply(offerId, result.output(), result.analysis().getId()));
        return result;
    }

    public Optional<AiAnalysis> latest(long offerId) {
        return analyses.findFirstBySubjectTypeAndSubjectIdAndPromptKeyOrderByIdDesc("JOB_OFFER", offerId, "JOB_ANALYSIS");
    }

    public List<JobRequirement> requirements(long offerId) {
        return requirements.findByJobOfferId(offerId);
    }

    void apply(long offerId, AiOutputs.JobAnalysis a, Long analysisId) {
        JobOffer o = offers.findById(offerId).orElseThrow();
        requirements.deleteByJobOfferId(offerId);
        List<JobRequirement> rows = new ArrayList<>();
        for (var s : a.mandatorySkills()) rows.add(skillRow(offerId, s, "MANDATORY", analysisId));
        for (var s : a.preferredSkills()) rows.add(skillRow(offerId, s, "PREFERRED", analysisId));
        for (var l : a.languages()) {
            rows.add(new JobRequirement(offerId, "LANGUAGE", l.mandatory() ? "MANDATORY" : "PREFERRED", null,
                    "Idioma " + l.code() + (l.level() == null ? "" : " " + l.level()), null, l.code().toLowerCase(Locale.ROOT),
                    l.level(), "AI", analysisId));
        }
        if (a.minYearsExperience() != null) {
            rows.add(new JobRequirement(offerId, "EXPERIENCE", "MANDATORY", null, a.minYearsExperience() + " años de experiencia",
                    a.minYearsExperience(), null, null, "AI", analysisId));
        }
        requirements.saveAll(rows);

        String quote = a.location().evidenceQuote();
        boolean verbatim = quote != null && quote.length() >= 8 && contains(o.getTitle() + "\n" + o.getLocationText() + "\n"
                + o.getDescriptionText(), quote);
        String langs = a.languages().isEmpty() ? null : Json.write(a.languages().stream()
                .map(l -> new JobClassification.RequiredLanguage(l.code().toLowerCase(Locale.ROOT), l.level(), "IA")).toList());
        o.applyAiAnalysis(verbatim ? a.location().boliviaEligibility() : null, verbatim ? "IA (cita textual): «" + quote + "»" : null,
                a.seniority(), "UNKNOWN".equals(a.contractType()) ? null : a.contractType(),
                "UNKNOWN".equals(a.engagementType()) ? null : a.engagementType(), langs);
    }

    private JobRequirement skillRow(long offerId, AiOutputs.SkillReq s, String importance, Long analysisId) {
        Long skillId = resolveSkill(s.name());
        return new JobRequirement(offerId, "SKILL", importance, skillId, s.name(), s.minYears(), null, null, "AI", analysisId);
    }

    /** Catalogue id for a technology name: exact normalized name first, then the alias-aware dictionary. */
    Long resolveSkill(String name) {
        Optional<Skill> exact = skills.findByNormalizedName(Skill.normalize(name));
        if (exact.isPresent()) return exact.get().getId();
        var found = dictionary.find(name);
        return found.isEmpty() ? null : found.get(0).skillId();
    }

    static boolean contains(String haystack, String needle) {
        return norm(haystack).contains(norm(needle));
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC);
        return n.toLowerCase(Locale.ROOT).replaceAll("[\\s\"'«»“”‘’]+", " ").trim();
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").replaceAll("[<>]", "");
    }
}
