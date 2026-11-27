package com.jobhunter.integrations.application;

import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.integrations.domain.IntegrationSetting;
import com.jobhunter.integrations.infrastructure.IntegrationSettingRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import com.jobhunter.shared.crypto.SecretCipher;
import com.jobhunter.shared.json.Json;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * On/off switches, write-only credentials and limits of every external integration.
 * Credential resolution: environment variable first (locks editing in the panel), then the encrypted DB value.
 * Secrets never leave this service except to the connector that needs them.
 */
@Service
public class IntegrationService {

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {
    };

    private final IntegrationSettingRepository repository;
    private final SecretCipher cipher;
    private final Environment env;
    private final AuditService audit;
    private final Clock clock;

    public IntegrationService(IntegrationSettingRepository repository, SecretCipher cipher, Environment env,
                              AuditService audit, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.env = env;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public IntegrationSetting setting(IntegrationCode code) {
        return repository.findByCode(code).orElseGet(() -> repository.save(new IntegrationSetting(code)));
    }

    @Transactional(readOnly = true)
    public List<IntegrationSetting> all() {
        return repository.findAll();
    }

    /** Field name → configured? (and whether the value comes from the environment). */
    public record CredentialState(String name, String label, boolean required, boolean configured, boolean fromEnv,
                                  String last4) {
    }

    @Transactional(readOnly = true)
    public List<CredentialState> credentialState(IntegrationCode code) {
        IntegrationSetting s = repository.findByCode(code).orElse(null);
        Map<String, String> stored = s == null ? Map.of() : decrypt(s);
        return code.credentialFields().stream().map(f -> {
            String fromEnv = envValue(f);
            boolean configured = fromEnv != null || stored.containsKey(f.name());
            String value = fromEnv != null ? fromEnv : stored.get(f.name());
            return new CredentialState(f.name(), f.label(), f.required(), configured, fromEnv != null, last4(value));
        }).toList();
    }

    /** Credentials for a connector/provider. Empty when a required field is missing. */
    @Transactional(readOnly = true)
    public Optional<Map<String, String>> credentials(IntegrationCode code) {
        IntegrationSetting s = repository.findByCode(code).orElse(null);
        Map<String, String> stored = s == null ? Map.of() : decrypt(s);
        Map<String, String> result = new LinkedHashMap<>();
        for (IntegrationCode.Field f : code.credentialFields()) {
            String value = Optional.ofNullable(envValue(f)).orElse(stored.get(f.name()));
            if (value != null && !value.isBlank()) result.put(f.name(), value);
            else if (f.required()) return Optional.empty();
        }
        return Optional.of(result);
    }

    /** Enabled AND has every required credential. */
    @Transactional(readOnly = true)
    public boolean isUsable(IntegrationCode code) {
        return repository.findByCode(code).map(IntegrationSetting::isEnabled).orElse(false)
                && credentials(code).isPresent();
    }

    @Transactional
    public IntegrationSetting update(long actorId, IntegrationCode code, Boolean enabled, JsonNode config,
                                     Integer dailyLimit, Integer monthlyLimit, BigDecimal monthlyBudgetUsd,
                                     Integer version) {
        IntegrationSetting s = setting(code);
        s.checkVersion(version);
        if (Boolean.TRUE.equals(enabled) && code.requiresCredentials() && credentials(code).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CREDENTIALS_REQUIRED",
                    "Agrega las credenciales de " + code.displayName() + " antes de activarla");
        }
        if (enabled != null && enabled != s.isEnabled()) {
            s.setEnabled(enabled);
            audit.record(actorId, "INTEGRATION_TOGGLED", "INTEGRATION", code.name(), Map.of("enabled", enabled));
        }
        if (config != null) {
            if (!config.isObject()) throw ApiException.badRequest("VALIDATION_FAILED", "config debe ser un objeto JSON");
            s.setConfig(Json.write(config));
        }
        if (dailyLimit != null || monthlyLimit != null || monthlyBudgetUsd != null) {
            if ((dailyLimit != null && dailyLimit < 0) || (monthlyLimit != null && monthlyLimit < 0)
                    || (monthlyBudgetUsd != null && monthlyBudgetUsd.signum() < 0)) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Los límites no pueden ser negativos");
            }
            s.setLimits(dailyLimit != null ? dailyLimit : s.getDailyLimit(),
                    monthlyLimit != null ? monthlyLimit : s.getMonthlyLimit(),
                    monthlyBudgetUsd != null ? monthlyBudgetUsd : s.getMonthlyBudgetUsd());
            audit.record(actorId, "SETTINGS_CHANGED", "INTEGRATION", code.name(), null);
        }
        return s;
    }

    /** Write-only. Partial updates keep previously stored fields. Values are never logged or returned. */
    @Transactional
    public IntegrationSetting setCredentials(long actorId, IntegrationCode code, Map<String, String> values) {
        if (code.credentialFields().isEmpty()) {
            throw ApiException.badRequest("NO_CREDENTIALS", code.displayName() + " no usa credenciales");
        }
        if (!cipher.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT, "MASTER_KEY_MISSING",
                    "Configura JOBHUNTER_MASTER_KEY en el servidor para poder guardar credenciales");
        }
        IntegrationSetting s = setting(code);
        Map<String, String> merged = new LinkedHashMap<>(decrypt(s));
        for (IntegrationCode.Field f : code.credentialFields()) {
            String v = values.get(f.name());
            if (v != null && !v.isBlank()) {
                if (v.length() > 500) throw ApiException.badRequest("VALIDATION_FAILED", "Credencial demasiado larga");
                merged.put(f.name(), v.trim());
            }
        }
        values.keySet().stream().filter(k -> code.credentialFields().stream().noneMatch(f -> f.name().equals(k)))
                .findFirst().ifPresent(k -> {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Campo desconocido: " + k);
                });
        String primary = code.credentialFields().get(code.credentialFields().size() - 1).name();
        s.setSecret(cipher.encrypt(Json.write(merged), aad(code)), last4(merged.get(primary)));
        audit.record(actorId, "SECRET_UPDATED", "INTEGRATION", code.name(), Map.of("fields", merged.keySet()));
        return s;
    }

    @Transactional
    public IntegrationSetting clearCredentials(long actorId, IntegrationCode code) {
        IntegrationSetting s = setting(code);
        s.clearSecret();
        if (code.requiresCredentials()) s.setEnabled(false);
        audit.record(actorId, "SECRET_DELETED", "INTEGRATION", code.name(), null);
        return s;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(IntegrationCode code) {
        setting(code).recordSuccess(clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(IntegrationCode code, String message) {
        setting(code).recordError(clock.instant(), message);
    }

    @Transactional(readOnly = true)
    public JsonNode config(IntegrationCode code) {
        return repository.findByCode(code).map(s -> Json.tree(s.getConfig() == null ? "{}" : s.getConfig()))
                .orElseGet(() -> Json.tree("{}"));
    }

    private Map<String, String> decrypt(IntegrationSetting s) {
        if (s.getSecretCiphertext() == null || !cipher.isConfigured()) return Map.of();
        try {
            return Json.read(cipher.decrypt(s.getSecretCiphertext(), aad(s.getCode())), MAP);
        } catch (IllegalStateException e) {
            return Map.of(); // wrong/rotated master key: behave as not configured
        }
    }

    private String envValue(IntegrationCode.Field f) {
        String v = env.getProperty(f.envVar());
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String aad(IntegrationCode code) {
        return "integration:" + code.name();
    }

    static String last4(String value) {
        return value == null || value.length() < 8 ? (value == null ? null : "••••") : value.substring(value.length() - 4);
    }
}
