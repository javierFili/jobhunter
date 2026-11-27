package com.jobhunter.jobsources.application;

import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobs.infrastructure.JobSourceRepository;
import com.jobhunter.jobsources.domain.ApifyActor;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.infrastructure.ApifyActorRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import com.jobhunter.shared.json.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Which sources exist, whether each one can be used right now (and why not), plus admin config of sources/actors. */
@Service
public class SourceCatalogService {

    private final JobSourceRepository sources;
    private final ApifyActorRepository actors;
    private final ConnectorRegistry registry;
    private final IntegrationService integrations;
    private final AuditService audit;
    private final Clock clock;

    public SourceCatalogService(JobSourceRepository sources, ApifyActorRepository actors, ConnectorRegistry registry,
                                IntegrationService integrations, AuditService audit, Clock clock) {
        this.sources = sources;
        this.actors = actors;
        this.registry = registry;
        this.integrations = integrations;
        this.audit = audit;
        this.clock = clock;
    }

    public record SourceView(String code, String type, String displayName, String integrationCode, boolean usable,
                             String reason, JobSourceConnector.Capabilities capabilities, String attributionText,
                             String termsUrl, String termsNotes) {
    }

    @Transactional(readOnly = true)
    public List<SourceView> list() {
        List<SourceView> result = new ArrayList<>();
        for (JobSource s : sources.findAll()) {
            if (s.getCode().startsWith("apify:")) {
                ApifyActor actor = actorForSource(s.getCode());
                String reason = !integrations.isUsable(IntegrationCode.APIFY) ? "Apify está desactivado"
                        : actor == null || !actor.isEnabled() ? "Actor desactivado" : !s.isEnabled() ? "Fuente desactivada" : null;
                result.add(new SourceView(s.getCode(), s.getType(), s.getDisplayName(), "APIFY", reason == null, reason,
                        new JobSourceConnector.Capabilities(true, true, false, false, false, false, false),
                        s.getAttributionText(), s.getTermsUrl(), s.getTermsNotes()));
                continue;
            }
            JobSourceConnector c = registry.get(s.getCode()).orElse(null);
            if (c == null) continue;
            String reason = !s.isEnabled() ? "Fuente desactivada"
                    : !integrations.setting(c.integration()).isEnabled() ? c.integration().displayName() + " está desactivada"
                    : integrations.credentials(c.integration()).isEmpty() ? "Faltan credenciales" : null;
            result.add(new SourceView(s.getCode(), s.getType(), s.getDisplayName(), c.integration().name(), reason == null,
                    reason, c.capabilities(), s.getAttributionText(), s.getTermsUrl(), s.getTermsNotes()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<JobSource> adminList() {
        return sources.findAll();
    }

    @Transactional
    public JobSource updateSource(long actorId, String code, Boolean enabled, Integer minInterval, Integer dailyCap) {
        JobSource s = sources.findByCode(code).orElseThrow(() -> ApiException.notFound("Fuente"));
        s.update(enabled, minInterval, dailyCap);
        audit.record(actorId, "SETTINGS_CHANGED", "JOB_SOURCE", code, null);
        return s;
    }

    // ---- Apify actors ----

    public record ActorCommand(String actorId, String displayName, String platform, Object inputTemplate,
                               Object fieldMapping, Integer maxItems, BigDecimal maxChargeUsd, Integer timeoutSecs,
                               Integer memoryMb, String pricingModel, String assessmentNotes, Boolean enabled,
                               Integer version) {
    }

    @Transactional(readOnly = true)
    public List<ApifyActor> actors() {
        return actors.findAll();
    }

    @Transactional
    public ApifyActor createActor(long actorUserId, ActorCommand c) {
        if (c.actorId() == null || !c.actorId().matches("^[A-Za-z0-9_.-]{1,60}[~/][A-Za-z0-9_.-]{1,60}$|^[A-Za-z0-9]{10,30}$")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "actorId debe ser 'usuario~nombre-actor' o un ID de Apify");
        }
        String actorId = c.actorId().replace('/', '~');
        if (actors.existsByActorId(actorId)) throw ApiException.conflict("Ese Actor ya está registrado");
        ApifyActor a = new ApifyActor(actorId);
        apply(a, c);
        a.setEnabled(false); // never enabled on creation: requires an explicit second step
        a = actors.save(a);
        JobSource source = new JobSource(a.sourceCode(), "APIFY_ACTOR", "Apify · " + a.getDisplayName());
        sources.save(source);
        audit.record(actorUserId, "APIFY_ACTOR_CREATED", "APIFY_ACTOR", a.getId(), Map.of("actorId", actorId));
        return a;
    }

    @Transactional
    public ApifyActor updateActor(long actorUserId, long id, ActorCommand c) {
        ApifyActor a = actors.findById(id).orElseThrow(() -> ApiException.notFound("Actor"));
        a.checkVersion(c.version());
        apply(a, c);
        if (c.enabled() != null && c.enabled() != a.isEnabled()) {
            if (c.enabled() && (a.getAssessmentNotes() == null || a.getAssessmentNotes().isBlank())) {
                throw ApiException.badRequest("ASSESSMENT_REQUIRED",
                        "Antes de activar el Actor documenta la evaluación: documentación, precio, calidad de datos y términos de la plataforma");
            }
            a.setEnabled(c.enabled());
            audit.record(actorUserId, "APIFY_ACTOR_TOGGLED", "APIFY_ACTOR", id, Map.of("enabled", c.enabled()));
        }
        return a;
    }

    @Transactional
    public void deleteActor(long actorUserId, long id) {
        ApifyActor a = actors.findById(id).orElseThrow(() -> ApiException.notFound("Actor"));
        sources.findByCode(a.sourceCode()).ifPresent(s -> s.update(false, null, null));
        actors.delete(a);
        audit.record(actorUserId, "APIFY_ACTOR_DELETED", "APIFY_ACTOR", id, null);
    }

    @Transactional(readOnly = true)
    public ApifyActor actorForSource(String sourceCode) {
        try {
            return actors.findById(Long.parseLong(sourceCode.substring("apify:".length()))).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void apply(ApifyActor a, ActorCommand c) {
        String input = c.inputTemplate() == null ? (a.getInputTemplate() == null ? "{}" : a.getInputTemplate()) : Json.write(c.inputTemplate());
        String mapping = c.fieldMapping() == null ? (a.getFieldMapping() == null ? "{}" : a.getFieldMapping()) : Json.write(c.fieldMapping());
        if (!Json.tree(input).isObject() || !Json.tree(mapping).isObject()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "inputTemplate y fieldMapping deben ser objetos JSON");
        }
        int maxItems = c.maxItems() != null ? c.maxItems() : (a.getMaxItems() == 0 ? 50 : a.getMaxItems());
        BigDecimal maxCharge = c.maxChargeUsd() != null ? c.maxChargeUsd() : (a.getMaxChargeUsd() == null ? new BigDecimal("0.50") : a.getMaxChargeUsd());
        int timeout = c.timeoutSecs() != null ? c.timeoutSecs() : (a.getTimeoutSecs() == 0 ? 300 : a.getTimeoutSecs());
        if (maxItems < 1 || maxItems > 500 || maxCharge.signum() <= 0 || maxCharge.compareTo(new BigDecimal("20")) > 0
                || timeout < 30 || timeout > 3600) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Límites del Actor fuera de rango (items 1-500, costo 0-20 USD, timeout 30-3600 s)");
        }
        a.update(c.displayName() != null ? c.displayName() : (a.getDisplayName() == null ? a.getActorId() : a.getDisplayName()),
                c.platform() != null ? c.platform() : a.getPlatform(), input, mapping, maxItems, maxCharge, timeout,
                c.memoryMb() != null ? c.memoryMb() : a.getMemoryMb(),
                c.pricingModel() != null ? c.pricingModel() : a.getPricingModel(),
                c.assessmentNotes() != null ? c.assessmentNotes() : a.getAssessmentNotes(), clock.instant());
    }
}
