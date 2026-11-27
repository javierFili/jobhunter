package com.jobhunter.ai.api;

import com.jobhunter.ai.application.PromptRegistry;
import com.jobhunter.ai.application.TaskRouter;
import com.jobhunter.ai.domain.PromptVersion;
import com.jobhunter.ai.domain.ProviderCode;
import com.jobhunter.ai.domain.TaskRoute;
import com.jobhunter.ai.infrastructure.ClaudeAiProvider;
import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Admin: AI routing per task (DeepSeek or Claude), available models and prompt versions. */
@RestController
@RequestMapping("/api/v1/admin")
public class AiAdminController {

    private final TaskRouter router;
    private final PromptRegistry prompts;
    private final IntegrationService integrations;
    private final ClaudeAiProvider claude;

    public AiAdminController(TaskRouter router, PromptRegistry prompts, IntegrationService integrations, ClaudeAiProvider claude) {
        this.router = router;
        this.prompts = prompts;
        this.integrations = integrations;
        this.claude = claude;
    }

    public record RouteDto(String promptKey, String provider, String model, String effort, String thinking,
                           String fallbackProvider, String fallbackModel, int maxOutputTokens, boolean implemented, int version) {
        static RouteDto from(TaskRoute r) {
            return new RouteDto(r.getPromptKey(), r.getProvider().name(), r.getModel(), r.getEffort(), r.getThinking(),
                    r.getFallbackProvider() == null ? null : r.getFallbackProvider().name(), r.getFallbackModel(),
                    r.getMaxOutputTokens(), PromptRegistry.KEYS.contains(r.getPromptKey()), r.getVersion());
        }
    }

    @GetMapping("/ai/routes")
    public List<RouteDto> routes() {
        return router.all().stream().map(RouteDto::from).toList();
    }

    @PutMapping("/ai/routes/{promptKey}")
    public RouteDto updateRoute(@PathVariable String promptKey, @RequestBody TaskRouter.RouteCommand body) {
        return RouteDto.from(router.update(CurrentUser.id(), promptKey.toUpperCase(), body));
    }

    public record ModelsDto(String provider, List<String> models, String source) {
    }

    /** Claude: live list from GET /v1/models when a key is configured; otherwise the configured list. */
    @GetMapping("/ai/models")
    public ModelsDto models(@RequestParam String provider) {
        ProviderCode p;
        try {
            p = ProviderCode.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Proveedor desconocido");
        }
        List<String> configured = new ArrayList<>();
        integrations.config(p.integration()).path("models").forEach(n -> configured.add(n.asString()));
        if (p == ProviderCode.CLAUDE) {
            var creds = integrations.credentials(p.integration());
            if (creds.isPresent()) {
                try {
                    return new ModelsDto(p.name(), claude.models(creds.get().get("apiKey")), "API");
                } catch (RuntimeException ignored) {
                    // fall back to configuration
                }
            }
        }
        return new ModelsDto(p.name(), configured, "CONFIG");
    }

    public record PromptVersionDto(String promptKey, int version, boolean active, String systemTemplate, String userTemplate,
                                   JsonNode outputSchema, BigDecimal temperature, String checksum, String notes, Instant createdAt) {
        static PromptVersionDto from(PromptVersion p) {
            return new PromptVersionDto(p.getPromptKey(), p.getVersion(), p.isActive(), p.getSystemTemplate(), p.getUserTemplate(),
                    Json.tree(p.getOutputSchema()), p.getTemperature(), p.getChecksum(), p.getNotes(), p.getCreatedAt());
        }
    }

    public record NewPromptVersion(String systemTemplate, String userTemplate, JsonNode outputSchema, BigDecimal temperature,
                                   String notes) {
    }

    @GetMapping("/prompts")
    public List<PromptVersionDto> activePrompts() {
        return PromptRegistry.KEYS.stream().map(k -> PromptVersionDto.from(prompts.active(k))).toList();
    }

    @GetMapping("/prompts/{key}/versions")
    public List<PromptVersionDto> versions(@PathVariable String key) {
        return prompts.versions(key.toUpperCase()).stream().map(PromptVersionDto::from).toList();
    }

    @PostMapping("/prompts/{key}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PromptVersionDto newVersion(@PathVariable String key, @RequestBody NewPromptVersion body) {
        if (body.systemTemplate() == null || body.userTemplate() == null || body.outputSchema() == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "systemTemplate, userTemplate y outputSchema son obligatorios");
        }
        return PromptVersionDto.from(prompts.createVersion(CurrentUser.id(), key.toUpperCase(), body.systemTemplate(),
                body.userTemplate(), body.outputSchema().toString(), body.temperature(), body.notes()));
    }

    @PostMapping("/prompts/{key}/versions/{version}/activate")
    public PromptVersionDto activate(@PathVariable String key, @PathVariable int version) {
        return PromptVersionDto.from(prompts.activate(key.toUpperCase(), version));
    }
}
