package com.jobhunter.ai.application;

import com.jobhunter.ai.domain.ProviderCode;
import com.jobhunter.ai.domain.TaskRoute;
import com.jobhunter.ai.infrastructure.TaskRouteRepository;
import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-task provider/model selection (DeepSeek or Claude) editable from the admin panel. */
@Service
public class TaskRouter {

    static final Set<String> EFFORTS = Set.of("LOW", "MEDIUM", "HIGH", "XHIGH", "MAX");

    private final TaskRouteRepository repository;
    private final IntegrationService integrations;
    private final AuditService audit;

    public TaskRouter(TaskRouteRepository repository, IntegrationService integrations, AuditService audit) {
        this.repository = repository;
        this.integrations = integrations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TaskRoute> all() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public TaskRoute route(String promptKey) {
        return repository.findByPromptKey(promptKey).orElseThrow(() -> ApiException.notFound("Ruta de IA"));
    }

    public record RouteCommand(ProviderCode provider, String model, String effort, String thinking,
                               ProviderCode fallbackProvider, String fallbackModel, Integer maxOutputTokens, Integer version) {
    }

    @Transactional
    public TaskRoute update(long actorId, String promptKey, RouteCommand c) {
        TaskRoute r = route(promptKey);
        r.checkVersion(c.version());
        if (c.provider() == null || c.model() == null || c.model().isBlank() || c.model().length() > 64
                || !c.model().matches("[A-Za-z0-9._:-]+")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Proveedor y modelo son obligatorios");
        }
        if (!integrations.isUsable(c.provider().integration())) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "INTEGRATION_DISABLED",
                    c.provider().integration().displayName() + " está desactivada o sin credenciales");
        }
        if (c.fallbackProvider() != null && (c.fallbackModel() == null || c.fallbackModel().isBlank())) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Indica el modelo de respaldo");
        }
        String effort = c.effort() == null || c.effort().isBlank() ? null : c.effort().toUpperCase();
        if (effort != null && !EFFORTS.contains(effort)) throw ApiException.badRequest("VALIDATION_FAILED", "Esfuerzo no válido");
        String thinking = c.thinking() == null ? null : c.thinking().toUpperCase();
        if (thinking != null && !Set.of("ON", "OFF").contains(thinking)) throw ApiException.badRequest("VALIDATION_FAILED", "thinking debe ser ON u OFF");
        int max = c.maxOutputTokens() == null ? r.getMaxOutputTokens() : c.maxOutputTokens();
        if (max < 256 || max > 64000) throw ApiException.badRequest("VALIDATION_FAILED", "maxOutputTokens entre 256 y 64000");
        r.update(c.provider(), c.model().trim(), effort, thinking, c.fallbackProvider(), c.fallbackModel(), max, actorId);
        audit.record(actorId, "AI_ROUTE_CHANGED", "AI_ROUTE", promptKey, Map.of("provider", c.provider().name(), "model", c.model()));
        return r;
    }
}
