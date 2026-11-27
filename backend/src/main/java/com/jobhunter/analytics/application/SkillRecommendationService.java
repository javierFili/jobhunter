package com.jobhunter.analytics.application;

import com.jobhunter.ai.application.AiGateway;
import com.jobhunter.ai.domain.AiAnalysis;
import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.ai.infrastructure.AiAnalysisRepository;
import com.jobhunter.profiles.application.SkillService;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.domain.ConsentType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI-GENERATED learning recommendations, clearly separated from observed data: the model only receives the observed
 * demand statistics and the user's skills (no contact data) and must cite the observed numbers.
 */
@Service
public class SkillRecommendationService {

    public static final String DISCLAIMER = "Recomendación generada por IA a partir de los datos observados por la app; "
            + "no es una garantía de empleabilidad ni un estudio de mercado completo.";

    private final AnalyticsService analytics;
    private final SkillService skills;
    private final AiGateway gateway;
    private final AiAnalysisRepository analyses;

    public SkillRecommendationService(AnalyticsService analytics, SkillService skills, AiGateway gateway, AiAnalysisRepository analyses) {
        this.analytics = analytics;
        this.skills = skills;
        this.gateway = gateway;
        this.analyses = analyses;
    }

    public void generate(long userId, String region, int days) {
        List<AnalyticsService.SkillDemand> demand = analytics.skillDemand(userId, region, days, 40);
        long observed = analytics.jobsObserved(region, days);
        if (demand.isEmpty() || observed < 10) {
            throw ApiException.badRequest("NOT_ENOUGH_DATA", "Hay muy pocas ofertas observadas (" + observed
                    + ") para recomendar; ejecuta más búsquedas primero");
        }
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("region", region == null || region.isBlank() ? "ALL" : region);
        market.put("days", days);
        market.put("jobsObserved", observed);
        market.put("technologies", demand.stream().map(d -> Map.of("name", d.name(), "jobs", d.jobs())).toList());
        var mine = skills.list(userId).stream().map(s -> Map.of("name", s.name(), "evidence", String.valueOf(s.evidenceLevel()),
                "yearsProfessional", s.yearsProfessional())).toList();
        String marketJson = Json.write(market);
        gateway.run(new AiGateway.Task<>("SKILL_RECOMMENDATION", userId, "USER", userId,
                Map.of("market", marketJson, "skills", Json.write(mine)), java.util.HexFormat.of().formatHex(com.jobhunter.jobs.domain.JobText.sha256(marketJson + "|" + Json.write(mine))), ConsentType.AI_PROCESS_PROFILE,
                AiOutputs.SkillRecommendations.class, false));
    }

    public record Result(Instant generatedAt, String provider, String model, Long basedOnJobs,
                         List<AiOutputs.SkillRecommendation> recommendations, String disclaimer) {
    }

    public Optional<Result> latest(long userId, String region, int days) {
        return analyses.findFirstBySubjectTypeAndSubjectIdAndPromptKeyAndUserIdOrderByIdDesc("USER", userId, "SKILL_RECOMMENDATION", userId)
                .filter(a -> a.getStatus() == AiAnalysis.Status.SUCCEEDED)
                .map(a -> {
                    var out = Json.read(a.getOutput(), AiOutputs.SkillRecommendations.class);
                    return new Result(a.getCreatedAt(), a.getProvider(), a.getModel(), analytics.jobsObserved(region, days),
                            out.recommendations(), DISCLAIMER);
                });
    }
}
