package com.jobhunter.ai.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One AI call (or cached result). {@code cacheKey} = sha256(prompt checksum | model | inputs). */
@Entity
@Table(name = "ai_analyses")
public class AiAnalysis extends BaseEntity {

    public enum Status { RUNNING, SUCCEEDED, FAILED, INVALID_OUTPUT, REFUSED }

    @Column(name = "user_id") private Long userId;
    @Column(name = "prompt_version_id", nullable = false) private Long promptVersionId;
    @Column(name = "prompt_key", nullable = false, length = 32) private String promptKey;
    @Column(nullable = false, length = 16) private String provider;
    @Column(nullable = false, length = 64) private String model;
    @Column(name = "subject_type", nullable = false, length = 16) private String subjectType;
    @Column(name = "subject_id", nullable = false) private Long subjectId;
    @Column(name = "cache_key", nullable = false, length = 64) private String cacheKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(columnDefinition = "json") private String output;
    @Column(name = "input_tokens") private Integer inputTokens;
    @Column(name = "cached_input_tokens") private Integer cachedInputTokens;
    @Column(name = "output_tokens") private Integer outputTokens;
    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6) private BigDecimal costUsd = BigDecimal.ZERO;
    @Column(name = "latency_ms") private Integer latencyMs;
    @Column(nullable = false) private int attempts = 1;
    @Column(length = 1000) private String error;

    protected AiAnalysis() {
    }

    public AiAnalysis(Long userId, Long promptVersionId, String promptKey, String provider, String model,
                      String subjectType, Long subjectId, String cacheKey) {
        this.userId = userId;
        this.promptVersionId = promptVersionId;
        this.promptKey = promptKey;
        this.provider = provider;
        this.model = model;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.cacheKey = cacheKey;
        this.status = Status.RUNNING;
    }

    public void complete(Status status, String provider, String model, String output, int input, int cached, int out,
                         BigDecimal cost, long latencyMs, int attempts, String error) {
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.output = output;
        this.inputTokens = input;
        this.cachedInputTokens = cached;
        this.outputTokens = out;
        this.costUsd = cost;
        this.latencyMs = (int) latencyMs;
        this.attempts = attempts;
        this.error = error == null ? null : error.substring(0, Math.min(1000, error.length()));
    }

    public Long getUserId() { return userId; }
    public String getPromptKey() { return promptKey; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getSubjectType() { return subjectType; }
    public Long getSubjectId() { return subjectId; }
    public String getCacheKey() { return cacheKey; }
    public Status getStatus() { return status; }
    public String getOutput() { return output; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getCachedInputTokens() { return cachedInputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Integer getLatencyMs() { return latencyMs; }
    public String getError() { return error; }
}
