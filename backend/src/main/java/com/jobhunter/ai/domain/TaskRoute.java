package com.jobhunter.ai.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Which provider/model handles each AI task (user-selectable: DeepSeek or Claude), with an optional fallback. */
@Entity
@Table(name = "ai_task_routes")
public class TaskRoute extends VersionedEntity {

    @Column(name = "prompt_key", nullable = false, length = 32, updatable = false) private String promptKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ProviderCode provider;
    @Column(nullable = false, length = 64) private String model;
    @Column(length = 8) private String effort;
    @Column(length = 8) private String thinking;
    @Enumerated(EnumType.STRING) @Column(name = "fallback_provider", length = 16) private ProviderCode fallbackProvider;
    @Column(name = "fallback_model", length = 64) private String fallbackModel;
    @Column(name = "max_output_tokens", nullable = false) private int maxOutputTokens;
    @Column(name = "updated_by") private Long updatedBy;

    protected TaskRoute() {
    }

    public void update(ProviderCode provider, String model, String effort, String thinking, ProviderCode fallbackProvider,
                       String fallbackModel, int maxOutputTokens, Long updatedBy) {
        this.provider = provider;
        this.model = model;
        this.effort = effort;
        this.thinking = thinking;
        this.fallbackProvider = fallbackProvider;
        this.fallbackModel = fallbackProvider == null ? null : fallbackModel;
        this.maxOutputTokens = maxOutputTokens;
        this.updatedBy = updatedBy;
    }

    public String getPromptKey() { return promptKey; }
    public ProviderCode getProvider() { return provider; }
    public String getModel() { return model; }
    public String getEffort() { return effort; }
    public String getThinking() { return thinking; }
    public ProviderCode getFallbackProvider() { return fallbackProvider; }
    public String getFallbackModel() { return fallbackModel; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
}
