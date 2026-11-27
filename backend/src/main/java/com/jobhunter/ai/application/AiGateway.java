package com.jobhunter.ai.application;

import com.jobhunter.ai.domain.AiAnalysis;
import com.jobhunter.ai.domain.AiCallException;
import com.jobhunter.ai.domain.AiProvider;
import com.jobhunter.ai.domain.AiRequest;
import com.jobhunter.ai.domain.AiResponse;
import com.jobhunter.ai.domain.PromptVersion;
import com.jobhunter.ai.domain.ProviderCode;
import com.jobhunter.ai.domain.TaskRoute;
import com.jobhunter.ai.infrastructure.AiAnalysisRepository;
import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.integrations.application.UsageService;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.application.UserService;
import com.jobhunter.users.domain.ConsentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single entry point for every AI call:
 * route (DeepSeek/Claude + optional fallback) → consent (when personal data is sent) → cache lookup → budget guard
 * → provider call with bounded retries on transient errors → JSON parse + schema/constraint validation with ONE repair
 * attempt → usage + cost accounting → cached {@link AiAnalysis}. The AI never triggers side effects: callers only get
 * validated data back.
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private static final int MAX_TRANSIENT_RETRIES = 2;

    private final Map<ProviderCode, AiProvider> providers;
    private final PromptRegistry prompts;
    private final TaskRouter router;
    private final AiAnalysisRepository analyses;
    private final IntegrationService integrations;
    private final UsageService usage;
    private final UserService users;
    private final Validator validator;
    private final Clock clock;

    public AiGateway(List<AiProvider> providers, PromptRegistry prompts, TaskRouter router, AiAnalysisRepository analyses,
                     IntegrationService integrations, UsageService usage, UserService users, Validator validator, Clock clock) {
        this.providers = providers.stream().collect(Collectors.toMap(AiProvider::provider, Function.identity()));
        this.prompts = prompts;
        this.router = router;
        this.analyses = analyses;
        this.integrations = integrations;
        this.usage = usage;
        this.users = users;
        this.validator = validator;
        this.clock = clock;
    }

    /**
     * @param consentType   non-null when the inputs contain the user's personal data (profile/CV)
     * @param cacheMaterial deterministic fingerprint of all inputs (hashes), part of the cache key
     */
    public record Task<T>(String promptKey, Long userId, String subjectType, long subjectId, Map<String, String> vars,
                          String cacheMaterial, ConsentType consentType, Class<T> outputType, boolean bypassCache) {
    }

    public record Result<T>(AiAnalysis analysis, T output, boolean cached) {
    }

    public <T> Result<T> run(Task<T> task) {
        TaskRoute route = router.route(task.promptKey());
        PromptVersion prompt = prompts.active(task.promptKey());
        List<String> reasons = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate(route.getProvider(), route.getModel(), route.getEffort(), route.getThinking()));
        if (route.getFallbackProvider() != null) {
            candidates.add(new Candidate(route.getFallbackProvider(), route.getFallbackModel(), route.getEffort(), route.getThinking()));
        }
        String lastCode = "AI_UNAVAILABLE";
        for (Candidate c : candidates) {
            if (!integrations.isUsable(c.provider().integration())) {
                reasons.add(c.provider().integration().displayName() + " está desactivada o sin credenciales");
                lastCode = "INTEGRATION_DISABLED";
                continue;
            }
            if (task.consentType() != null && (task.userId() == null
                    || !users.hasConsent(task.userId(), task.consentType(), c.provider().consentName()))) {
                reasons.add("Sin tu consentimiento para enviar datos personales a " + c.provider().integration().displayName());
                lastCode = "CONSENT_REQUIRED";
                continue;
            }
            String cacheKey = cacheKey(prompt, c, task.cacheMaterial());
            if (!task.bypassCache()) {
                var cached = analyses.findFirstByCacheKeyAndStatusOrderByIdDesc(cacheKey, AiAnalysis.Status.SUCCEEDED);
                if (cached.isPresent()) {
                    return new Result<>(cached.get(), Json.read(cached.get().getOutput(), task.outputType()), true);
                }
            }
            try {
                return call(task, prompt, route, c, cacheKey);
            } catch (ApiException e) {
                reasons.add(e.getMessage());
                lastCode = e.code();
            }
        }
        HttpStatus status = switch (lastCode) {
            case "CONSENT_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "BUDGET_EXCEEDED" -> HttpStatus.PAYMENT_REQUIRED;
            case "INTEGRATION_DISABLED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        throw new ApiException(status, lastCode, String.join(" · ", reasons));
    }

    record Candidate(ProviderCode provider, String model, String effort, String thinking) {
    }

    private <T> Result<T> call(Task<T> task, PromptVersion prompt, TaskRoute route, Candidate c, String cacheKey) {
        var config = integrations.config(c.provider().integration());
        String system = prompt.getSystemTemplate();
        String user = PromptRegistry.render(prompt.getUserTemplate(), task.vars());
        BigDecimal estimate = AiPricing.estimate(config, c.model(), system.length() + user.length(), route.getMaxOutputTokens());
        usage.checkBudget(c.provider().integration(), 1, estimate);

        AiAnalysis analysis = analyses.save(new AiAnalysis(task.userId(), prompt.getId(), prompt.getPromptKey(),
                c.provider().name(), c.model(), task.subjectType(), task.subjectId(), cacheKey));
        AiProvider provider = providers.get(c.provider());
        Map<String, String> creds = integrations.credentials(c.provider().integration()).orElseThrow();
        AiRequest request = new AiRequest(c.model(), system, user, prompt.getOutputSchema(), route.getMaxOutputTokens(),
                "ON".equals(c.thinking()), c.effort(),
                prompt.getTemperature() == null ? null : prompt.getTemperature().doubleValue(), Duration.ofMinutes(4));

        int attempts = 0;
        int in = 0, cached = 0, out = 0;
        long latency = 0;
        BigDecimal cost = BigDecimal.ZERO;
        boolean repaired = false;
        String lastError = null;
        String modelUsed = c.model();
        int transientRetries = 0;
        while (true) {
            attempts++;
            AiResponse response;
            try {
                response = provider.complete(request, creds);
            } catch (AiCallException e) {
                usage.record(c.provider().integration(), new UsageService.Usage(task.userId(), task.promptKey(), 1, 0, 0, 0,
                        null, BigDecimal.ZERO, false, e.status(), false, 0, String.valueOf(analysis.getId())));
                if (e.retryable() && transientRetries < MAX_TRANSIENT_RETRIES) {
                    transientRetries++;
                    sleep(Duration.ofSeconds(transientRetries == 1 ? 1 : 4));
                    continue;
                }
                integrations.recordError(c.provider().integration(), e.getMessage());
                finish(analysis, AiAnalysis.Status.FAILED, c, modelUsed, null, in, cached, out, cost, latency, attempts, e.getMessage());
                throw new ApiException(HttpStatus.BAD_GATEWAY, e.status() == 402 ? "INSUFFICIENT_BALANCE" : "AI_UNAVAILABLE",
                        c.provider().integration().displayName() + ": " + e.getMessage());
            }
            BigDecimal callCost = AiPricing.cost(c.provider(), config, c.model(), response, clock.instant());
            cost = cost.add(callCost);
            in += response.inputTokens();
            cached += response.cachedInputTokens();
            out += response.outputTokens();
            latency += response.latencyMs();
            if (response.modelUsed() != null) modelUsed = response.modelUsed();
            usage.record(c.provider().integration(), new UsageService.Usage(task.userId(), task.promptKey(), 1,
                    response.inputTokens(), response.cachedInputTokens(), response.outputTokens(), null, callCost, false,
                    200, !response.refused(), (int) response.latencyMs(), String.valueOf(analysis.getId())));
            integrations.recordSuccess(c.provider().integration());

            if (response.refused()) {
                finish(analysis, AiAnalysis.Status.REFUSED, c, modelUsed, null, in, cached, out, cost, latency, attempts,
                        "El modelo rechazó la solicitud (stop_reason=refusal)");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_REFUSED", c.provider().integration().displayName()
                        + " rechazó la solicitud");
            }
            String error;
            T parsed = null;
            String content = response.content() == null ? "" : response.content().trim();
            if (content.isEmpty()) {
                error = "Respuesta vacía (JSON inválido descartado por el proveedor)";
            } else {
                try {
                    parsed = Json.MAPPER.readValue(stripFences(content), task.outputType());
                    Set<ConstraintViolation<T>> violations = validator.validate(parsed);
                    error = violations.isEmpty() ? null : violations.stream()
                            .map(v -> v.getPropertyPath() + ": " + v.getMessage()).limit(10).collect(Collectors.joining("; "));
                } catch (RuntimeException e) {
                    error = "JSON inválido: " + e.getMessage();
                }
            }
            if (error == null) {
                finish(analysis, AiAnalysis.Status.SUCCEEDED, c, modelUsed, Json.write(parsed), in, cached, out, cost,
                        latency, attempts, null);
                return new Result<>(analysis, parsed, false);
            }
            lastError = error;
            if (repaired) break;
            repaired = true;
            request = new AiRequest(request.model(), request.staticSystem(), request.userContent()
                    + "\n\nYour previous answer was rejected: " + truncate(error, 500)
                    + "\nReturn ONLY a valid json object that satisfies the schema.", request.outputSchema(),
                    request.maxOutputTokens(), request.thinking(), request.effort(), request.temperature(), request.timeout());
        }
        finish(analysis, AiAnalysis.Status.INVALID_OUTPUT, c, modelUsed, null, in, cached, out, cost, latency, attempts, lastError);
        throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_OUTPUT",
                c.provider().integration().displayName() + " devolvió una respuesta inválida: " + truncate(lastError, 200));
    }

    private void finish(AiAnalysis a, AiAnalysis.Status status, Candidate c, String model, String output, int in, int cached,
                        int out, BigDecimal cost, long latency, int attempts, String error) {
        a.complete(status, c.provider().name(), model, output, in, cached, out, cost, latency, attempts, error);
        analyses.save(a);
        if (status != AiAnalysis.Status.SUCCEEDED) log.warn("AI {} {} {}: {}", a.getPromptKey(), c.provider(), status, error);
    }

    static String cacheKey(PromptVersion p, Candidate c, String material) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((p.getChecksum() + "|" + c.provider() + "|" + c.model()
                    + "|" + c.effort() + "|" + c.thinking() + "|" + material).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Some models wrap JSON in ```json fences despite instructions. */
    static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
