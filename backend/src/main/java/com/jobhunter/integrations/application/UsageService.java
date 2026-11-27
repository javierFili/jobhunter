package com.jobhunter.integrations.application;

import com.jobhunter.integrations.domain.ApiUsageLog;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.integrations.domain.IntegrationSetting;
import com.jobhunter.integrations.infrastructure.ApiUsageLogRepository;
import com.jobhunter.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Usage accounting and budget/limit guard, checked BEFORE every paid or rate-limited call. */
@Service
public class UsageService {

    private final ApiUsageLogRepository repository;
    private final IntegrationService integrations;
    private final Clock clock;

    public UsageService(ApiUsageLogRepository repository, IntegrationService integrations, Clock clock) {
        this.repository = repository;
        this.integrations = integrations;
        this.clock = clock;
    }

    public record Usage(Long userId, String operation, int requests, Integer inputTokens, Integer cachedTokens,
                        Integer outputTokens, Integer results, BigDecimal costUsd, boolean costIsEstimate,
                        Integer httpStatus, boolean success, Integer latencyMs, String correlationId) {

        public static Usage request(Long userId, String operation, int results, Integer httpStatus, boolean success,
                                    long latencyMs) {
            return new Usage(userId, operation, 1, null, null, null, results, BigDecimal.ZERO, false, httpStatus,
                    success, (int) latencyMs, null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(IntegrationCode code, Usage u) {
        repository.save(new ApiUsageLog(code, u.userId(), u.operation(), u.requests(), u.inputTokens(),
                u.cachedTokens(), u.outputTokens(), u.results(), u.costUsd(), u.costIsEstimate(), u.httpStatus(),
                u.success(), u.latencyMs(), u.correlationId(), clock.instant()));
    }

    /**
     * Throws if the integration is off, lacks credentials, or would exceed its daily/monthly request limit or
     * monthly USD budget. {@code estimatedCostUsd} is the worst-case cost of the call about to be made.
     */
    @Transactional(readOnly = true)
    public void checkBudget(IntegrationCode code, int requests, BigDecimal estimatedCostUsd) {
        IntegrationSetting s = integrations.setting(code);
        if (!s.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "INTEGRATION_DISABLED", code.displayName() + " está desactivada");
        }
        if (integrations.credentials(code).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CREDENTIALS_REQUIRED", "Faltan credenciales de " + code.displayName());
        }
        if (s.getDailyLimit() != null && requestsToday(code) + requests > s.getDailyLimit()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LIMIT_REACHED",
                    "Límite diario de " + code.displayName() + " alcanzado (" + s.getDailyLimit() + ")");
        }
        if (s.getMonthlyLimit() != null && requestsThisMonth(code) + requests > s.getMonthlyLimit()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LIMIT_REACHED",
                    "Límite mensual de " + code.displayName() + " alcanzado (" + s.getMonthlyLimit() + ")");
        }
        if (s.getMonthlyBudgetUsd() != null && estimatedCostUsd != null
                && costThisMonth(code).add(estimatedCostUsd).compareTo(s.getMonthlyBudgetUsd()) > 0) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "BUDGET_EXCEEDED",
                    "Presupuesto mensual de " + code.displayName() + " agotado (US$" + s.getMonthlyBudgetUsd() + ")");
        }
    }

    @Transactional(readOnly = true)
    public long requestsToday(IntegrationCode code) {
        return repository.requestsSince(code, startOfDay());
    }

    @Transactional(readOnly = true)
    public long requestsThisMonth(IntegrationCode code) {
        return repository.requestsSince(code, startOfMonth());
    }

    @Transactional(readOnly = true)
    public BigDecimal costThisMonth(IntegrationCode code) {
        return repository.costSince(code, startOfMonth());
    }

    @Transactional(readOnly = true)
    public List<ApiUsageLogRepository.UsageAggregate> aggregate(Instant from, Instant to) {
        return repository.aggregate(from, to);
    }

    private Instant startOfDay() {
        return LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant startOfMonth() {
        return LocalDate.now(clock).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
