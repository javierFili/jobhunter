package com.jobhunter.matching.application;

import com.jobhunter.ai.application.AiGateway;
import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.ai.domain.UntrustedText;
import com.jobhunter.jobs.application.FxService;
import com.jobhunter.jobs.application.JobQuery;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.application.MatchLookup;
import com.jobhunter.jobs.domain.JobClassification;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.JobRequirement;
import com.jobhunter.matching.domain.DeterministicScorer;
import com.jobhunter.matching.domain.DeterministicScorer.Dimension;
import com.jobhunter.matching.domain.JobMatch;
import com.jobhunter.matching.infrastructure.JobMatchRepository;
import com.jobhunter.profiles.application.ProfessionalProfileService;
import com.jobhunter.profiles.application.ProfileSnapshotService;
import com.jobhunter.profiles.application.ProfileSnapshotService.Snapshot;
import com.jobhunter.searches.domain.SearchCompletedEvent;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.domain.ConsentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compatibility of jobs with the user's VERIFIED profile: deterministic dimensions always; optional AI semantic
 * adjustment (requires consent to send profile facts — never contact data — to the chosen provider).
 */
@Service
public class MatchingService implements MatchLookup {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);
    private static final TypeReference<List<JobClassification.RequiredLanguage>> LANGS = new TypeReference<>() {
    };
    static final int MAX_BATCH = 300;

    private final JobMatchRepository matches;
    private final JobQueryService jobs;
    private final JobAnalysisService analysis;
    private final ProfileSnapshotService snapshots;
    private final ProfessionalProfileService profiles;
    private final FxService fx;
    private final AiGateway gateway;
    private final TransactionTemplate tx;

    public MatchingService(JobMatchRepository matches, JobQueryService jobs, JobAnalysisService analysis,
                           ProfileSnapshotService snapshots, ProfessionalProfileService profiles, FxService fx,
                           AiGateway gateway, PlatformTransactionManager txManager) {
        this.matches = matches;
        this.jobs = jobs;
        this.analysis = analysis;
        this.snapshots = snapshots;
        this.profiles = profiles;
        this.fx = fx;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(txManager);
    }

    record Context(long userId, Snapshot snapshot, DeterministicScorer.ProfileInput input, long profileKey,
                   Map<String, BigDecimal> rates) {
    }

    Context context(long userId, Long profileId) {
        var sc = snapshots.build(userId, profileId);
        Snapshot s = sc.snapshot();
        Map<String, BigDecimal> rates = fx.rates();
        Map<Long, DeterministicScorer.SkillFact> skills = new HashMap<>();
        s.skills().forEach(f -> {
            long id = Long.parseLong(f.ref().substring("skill:".length()));
            skills.put(id, new DeterministicScorer.SkillFact(id, f.name(), f.evidence(), f.yearsProfessional()));
        });
        Map<String, String> langs = new HashMap<>();
        s.languages().forEach(l -> langs.put(l.code(), l.level()));
        var p = s.preferences();
        BigDecimal minSalary = FxService.usdPerMonth(p.minSalary(), p.salaryCurrency(), p.salaryPeriod(), rates);
        var input = new DeterministicScorer.ProfileInput(skills, s.totalProfessionalYears(), langs, p.seniority(), minSalary,
                p.contractTypes(), p.engagementTypes(), p.countriesExclude());
        return new Context(userId, s, input, s.profileId() == null ? 0L : s.profileId(), rates);
    }

    DeterministicScorer.JobInput jobInput(JobOffer o, List<JobRequirement> reqs, Map<String, BigDecimal> rates) {
        List<DeterministicScorer.SkillRequirement> skillReqs = new ArrayList<>();
        boolean fromAnalysis = reqs.stream().anyMatch(r -> "SKILL".equals(r.getKind()));
        BigDecimal minYears = null;
        List<DeterministicScorer.LanguageRequirement> langs = new ArrayList<>();
        if (fromAnalysis) {
            for (JobRequirement r : reqs) {
                switch (r.getKind()) {
                    case "SKILL" -> skillReqs.add(new DeterministicScorer.SkillRequirement(r.getSkillId(), r.getText(),
                            "MANDATORY".equals(r.getImportance()), r.getMinYears()));
                    case "EXPERIENCE" -> minYears = r.getMinYears();
                    case "LANGUAGE" -> langs.add(new DeterministicScorer.LanguageRequirement(r.getLanguageCode(), r.getLevel(),
                            "MANDATORY".equals(r.getImportance())));
                    default -> { }
                }
            }
        } else {
            // no analysis yet: technologies detected by the dictionary, importance unknown
            o.getSkillIds().forEach(id -> skillReqs.add(new DeterministicScorer.SkillRequirement(id, "skill:" + id, false, null)));
            List<JobClassification.RequiredLanguage> required = Json.read(o.getRequiredLanguages(), LANGS);
            if (required != null) required.forEach(l -> langs.add(new DeterministicScorer.LanguageRequirement(l.code(), l.level(), true)));
        }
        BigDecimal maxUsd = FxService.usdPerMonth(o.getSalaryMax(), o.getSalaryCurrency(), o.getSalaryPeriod(), rates);
        return new DeterministicScorer.JobInput(o.getBoliviaEligibility(), o.getEligibilityEvidence(), o.getWorkModality(),
                o.getCountry(), o.getSeniority(), minYears, skillReqs, fromAnalysis, langs, o.getSalaryMinUsdMonth(), maxUsd,
                o.getContractType(), o.getEngagementType());
    }

    /** Computes (and stores) the match. With {@code useAi} the job is analyzed (cached) and semantically evaluated. */
    public JobMatch match(long userId, Long profileId, long offerId, boolean useAi) {
        Context ctx = context(userId, profileId);
        if (useAi) {
            analysis.analyze(userId, offerId, false);
        }
        JobOffer offer = jobs.get(offerId);
        List<JobRequirement> reqs = analysis.requirements(offerId);
        DeterministicScorer.Score score = DeterministicScorer.score(ctx.input(), jobInput(offer, reqs, ctx.rates()));
        if (!useAi) return store(ctx, offer, score, null, null);

        Map<String, Object> det = new LinkedHashMap<>();
        det.put("total", score.total());
        det.put("dimensions", score.dimensions());
        det.put("missingMandatory", score.missingMandatory());
        Map<String, String> vars = Map.of(
                "profile", Json.write(ctx.snapshot()),
                "deterministic", Json.write(det),
                "requirements", Json.write(reqs.stream().map(r -> Map.of("kind", r.getKind(), "importance", r.getImportance(),
                        "text", r.getText())).toList()),
                "posting", UntrustedText.wrap("job_posting", offer.getDescriptionText(), JobAnalysisService.MAX_POSTING_CHARS));
        var result = gateway.run(new AiGateway.Task<>("MATCH_SEMANTIC", userId, "JOB_OFFER", offerId, vars,
                ctx.snapshot().hash() + "|" + HexFormat.of().formatHex(offer.getContentHash()), ConsentType.AI_PROCESS_PROFILE,
                AiOutputs.SemanticMatch.class, false));
        return store(ctx, offer, score, result.output(), result.analysis().getId());
    }

    private JobMatch store(Context ctx, JobOffer offer, DeterministicScorer.Score score, AiOutputs.SemanticMatch ai, Long aiId) {
        Map<String, Dimension> dims = new LinkedHashMap<>(score.dimensions());
        List<String> strengths = new ArrayList<>(score.strengths());
        List<String> gaps = new ArrayList<>(score.gaps());
        String classification = score.classification();
        String recommendation = score.recommendation();
        int total = score.total();
        if (ai != null) {
            Dimension skills = dims.get("skills");
            int adj = Math.max(-15, Math.min(15, ai.skillsAdjustment()));
            if (adj > 0 && !score.missingMandatory().isEmpty()) adj = 0; // AI cannot compensate missing mandatory skills
            dims.put("skills", new Dimension("skills", clamp(skills.score() + adj), skills.reason()
                    + (adj == 0 ? "" : " · ajuste semántico IA " + (adj > 0 ? "+" : "") + adj)));
            Dimension exp = dims.get("experience");
            int relevance = Math.max(0, Math.min(100, ai.experienceRelevance()));
            dims.put("experience", new Dimension("experience", clamp((exp.score() + relevance) / 2),
                    exp.reason() + " · relevancia de tu experiencia según IA: " + relevance + "/100"));
            total = DeterministicScorer.total(dims);
            ai.strengths().forEach(x -> strengths.add("IA: " + x));
            ai.gaps().forEach(x -> gaps.add("IA: " + x));
            classification = DeterministicScorer.classify(offer.getBoliviaEligibility(), offer.getWorkModality(), total,
                    score.missingMandatory().isEmpty(), ai.injectionSuspected());
            recommendation = "NOT_ELIGIBLE".equals(classification) ? "SKIP"
                    : "NEEDS_REVIEW".equals(classification) ? "REVIEW" : ai.recommendation();
            dims.put("ai", new Dimension("ai", relevance, ai.rationale()));
        }
        final int finalTotal = total;
        final String finalClass = classification;
        final String finalRec = recommendation;
        return tx.execute(st -> {
            JobMatch m = matches.findByUserIdAndProfileKeyAndJobOfferId(ctx.userId(), ctx.profileKey(), offer.getId())
                    .orElseGet(() -> new JobMatch(ctx.userId(), ctx.profileKey(), offer.getId()));
            m.update(ctx.snapshot().hash(), offer.getContentHash(), finalTotal, s(dims, "skills"), s(dims, "experience"),
                    s(dims, "language"), s(dims, "seniority"), s(dims, "location"), s(dims, "compensation"), s(dims, "contract"),
                    finalClass, finalRec, Json.write(dedupe(strengths)), Json.write(dedupe(gaps)), Json.write(dims.values()),
                    ai == null ? "DETERMINISTIC" : "AI_ASSISTED", aiId);
            return matches.save(m);
        });
    }

    /** Deterministic re-scoring (free) of a batch of jobs, keeping AI-assisted matches whose inputs did not change. */
    public int scoreBatch(long userId, Long profileId, Collection<Long> offerIds) {
        if (offerIds.isEmpty()) return 0;
        Context ctx = context(userId, profileId);
        Map<Long, JobMatch> existing = matches.findByUserIdAndProfileKeyAndJobOfferIdIn(userId, ctx.profileKey(), offerIds)
                .stream().collect(Collectors.toMap(JobMatch::getJobOfferId, m -> m));
        int n = 0;
        for (Long id : offerIds.stream().limit(MAX_BATCH).toList()) {
            try {
                JobOffer offer = jobs.get(id);
                JobMatch prev = existing.get(id);
                if (prev != null && "AI_ASSISTED".equals(prev.getMethod()) && prev.getProfileSnapshotHash().equals(ctx.snapshot().hash())
                        && java.util.Arrays.equals(prev.getJobContentHash(), offer.getContentHash())) {
                    continue;
                }
                store(ctx, offer, DeterministicScorer.score(ctx.input(), jobInput(offer, analysis.requirements(id), ctx.rates())), null, null);
                n++;
            } catch (RuntimeException e) {
                log.warn("Scoring job {} failed: {}", id, e.getMessage());
            }
        }
        return n;
    }

    public int recompute(long userId, Long profileId) {
        Page<JobOffer> recent = jobs.search(userId, JobQuery.all(), null, 0, MAX_BATCH);
        return scoreBatch(userId, profileId, recent.getContent().stream().map(JobOffer::getId).toList());
    }

    @EventListener
    public void onSearchCompleted(SearchCompletedEvent e) {
        scoreBatch(e.userId(), e.profileId(), e.offerIds());
    }

    public Optional<JobMatch> get(long userId, Long profileId, long offerId) {
        return matches.findByUserIdAndProfileKeyAndJobOfferId(userId, profileKey(userId, profileId), offerId);
    }

    public Page<JobMatch> recommended(long userId, Long profileId, Set<String> classifications, int page, int size) {
        Set<String> cls = classifications == null || classifications.isEmpty()
                ? Set.of("HIGH_MATCH", "MEDIUM_MATCH", "NEEDS_REVIEW") : classifications;
        if (!DeterministicScorer.CLASSIFICATIONS.containsAll(cls)) throw ApiException.badRequest("VALIDATION_FAILED", "Clasificación no válida");
        return matches.recommended(userId, profileKey(userId, profileId), cls,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @Override
    public Map<Long, MatchSummary> forOffers(long userId, Collection<Long> offerIds) {
        long key = profileKey(userId, null);
        return matches.findByUserIdAndProfileKeyAndJobOfferIdIn(userId, key, offerIds).stream()
                .collect(Collectors.toMap(JobMatch::getJobOfferId, m -> new MatchSummary(m.getScoreTotal(), m.getClassification())));
    }

    long profileKey(long userId, Long profileId) {
        if (profileId != null) return profiles.get(userId, profileId).getId();
        return profiles.active(userId).map(p -> p.getId()).orElse(0L);
    }

    private static Integer s(Map<String, Dimension> d, String k) {
        Dimension x = d.get(k);
        return x == null ? null : x.score();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static List<String> dedupe(List<String> in) {
        return in.stream().distinct().limit(12).toList();
    }
}
