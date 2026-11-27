package com.jobhunter.integrations.api;

import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.integrations.application.UsageService;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.integrations.domain.IntegrationSetting;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin panel: one row per integration with on/off, write-only credentials, limits, status, usage and test. */
@RestController
@RequestMapping("/api/v1/admin/integrations")
public class IntegrationAdminController {

    private final IntegrationService integrations;
    private final UsageService usage;
    private final Map<IntegrationCode, IntegrationProbe> probes;

    public IntegrationAdminController(IntegrationService integrations, UsageService usage, List<IntegrationProbe> probes) {
        this.integrations = integrations;
        this.usage = usage;
        this.probes = probes.stream().collect(Collectors.toMap(IntegrationProbe::code, Function.identity()));
    }

    public record UsageDto(long requestsToday, long requestsThisMonth, BigDecimal costThisMonthUsd) {
    }

    public record IntegrationDto(String code, String displayName, String category, boolean enabled,
                                 boolean requiresCredentials, List<IntegrationService.CredentialState> credentials,
                                 JsonNode config, Integer dailyLimit, Integer monthlyLimit,
                                 BigDecimal monthlyBudgetUsd, String status, Instant lastSuccessAt,
                                 Instant lastErrorAt, String lastError, UsageDto usage, boolean testable,
                                 int version) {
    }

    public record UpdateRequest(Boolean enabled, JsonNode config, Integer dailyLimit, Integer monthlyLimit,
                                BigDecimal monthlyBudgetUsd, Integer version) {
    }

    @GetMapping
    public List<IntegrationDto> list() {
        return Arrays.stream(IntegrationCode.values()).map(c -> dto(integrations.setting(c))).toList();
    }

    @GetMapping("/{code}")
    public IntegrationDto get(@PathVariable String code) {
        return dto(integrations.setting(parse(code)));
    }

    @PutMapping("/{code}")
    public IntegrationDto update(@PathVariable String code, @RequestBody UpdateRequest body) {
        return dto(integrations.update(CurrentUser.id(), parse(code), body.enabled(), body.config(), body.dailyLimit(),
                body.monthlyLimit(), body.monthlyBudgetUsd(), body.version()));
    }

    /** Write-only: the response never contains the values, only "configured" + last 4 characters. */
    @PutMapping("/{code}/secret")
    public IntegrationDto setSecret(@PathVariable String code, @RequestBody Map<String, String> body) {
        return dto(integrations.setCredentials(CurrentUser.id(), parse(code), body));
    }

    @DeleteMapping("/{code}/secret")
    public IntegrationDto deleteSecret(@PathVariable String code) {
        return dto(integrations.clearCredentials(CurrentUser.id(), parse(code)));
    }

    @PostMapping("/{code}/test")
    public IntegrationProbe.ProbeResult test(@PathVariable String code) {
        IntegrationCode c = parse(code);
        IntegrationProbe probe = probes.get(c);
        if (probe == null) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_TESTABLE", "Esta integración no tiene prueba de conexión");
        }
        var creds = integrations.credentials(c);
        if (creds.isEmpty()) {
            return IntegrationProbe.ProbeResult.fail("Faltan credenciales");
        }
        IntegrationProbe.ProbeResult result;
        try {
            result = probe.probe(creds.get());
        } catch (RuntimeException e) {
            result = IntegrationProbe.ProbeResult.fail("Error: " + e.getMessage());
        }
        if (result.ok()) integrations.recordSuccess(c);
        else integrations.recordError(c, result.message());
        return result;
    }

    private IntegrationDto dto(IntegrationSetting s) {
        IntegrationCode c = s.getCode();
        return new IntegrationDto(c.name(), c.displayName(), c.category().name(), s.isEnabled(), c.requiresCredentials(),
                integrations.credentialState(c), Json.tree(s.getConfig() == null ? "{}" : s.getConfig()),
                s.getDailyLimit(), s.getMonthlyLimit(), s.getMonthlyBudgetUsd(), s.getStatus().name(),
                s.getLastSuccessAt(), s.getLastErrorAt(), s.getLastError(),
                new UsageDto(usage.requestsToday(c), usage.requestsThisMonth(c), usage.costThisMonth(c)),
                probes.containsKey(c), s.getVersion());
    }

    private static IntegrationCode parse(String code) {
        try {
            return IntegrationCode.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Integración");
        }
    }
}
