package com.jobhunter.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "api_usage_logs")
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "integration_code", nullable = false, length = 24)
    private IntegrationCode code;
    @Column(name = "user_id")
    private Long userId;
    @Column(nullable = false, length = 32)
    private String operation;
    @Column(name = "request_count", nullable = false)
    private int requestCount;
    @Column(name = "input_tokens")
    private Integer inputTokens;
    @Column(name = "cached_tokens")
    private Integer cachedTokens;
    @Column(name = "output_tokens")
    private Integer outputTokens;
    @Column(name = "result_count")
    private Integer resultCount;
    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd;
    @Column(name = "cost_is_estimate", nullable = false)
    private boolean costIsEstimate;
    @Column(name = "http_status")
    private Integer httpStatus;
    @Column(nullable = false)
    private boolean success;
    @Column(name = "latency_ms")
    private Integer latencyMs;
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ApiUsageLog() {
    }

    public ApiUsageLog(IntegrationCode code, Long userId, String operation, int requestCount, Integer inputTokens,
                       Integer cachedTokens, Integer outputTokens, Integer resultCount, BigDecimal costUsd,
                       boolean costIsEstimate, Integer httpStatus, boolean success, Integer latencyMs,
                       String correlationId, Instant occurredAt) {
        this.code = code;
        this.userId = userId;
        this.operation = operation;
        this.requestCount = requestCount;
        this.inputTokens = inputTokens;
        this.cachedTokens = cachedTokens;
        this.outputTokens = outputTokens;
        this.resultCount = resultCount;
        this.costUsd = costUsd == null ? BigDecimal.ZERO : costUsd;
        this.costIsEstimate = costIsEstimate;
        this.httpStatus = httpStatus;
        this.success = success;
        this.latencyMs = latencyMs;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public IntegrationCode getCode() { return code; }
    public String getOperation() { return operation; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Instant getOccurredAt() { return occurredAt; }
}
