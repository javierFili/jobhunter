package com.jobhunter.ai.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Immutable prompt version (template + output schema). Editing creates a new version; old analyses keep theirs. */
@Entity
@Table(name = "ai_prompt_versions")
public class PromptVersion extends BaseEntity {

    @Column(name = "prompt_key", nullable = false, length = 32, updatable = false) private String promptKey;
    @Column(nullable = false, updatable = false) private int version;
    @Column(name = "system_template", nullable = false, columnDefinition = "text", updatable = false) private String systemTemplate;
    @Column(name = "user_template", nullable = false, columnDefinition = "text", updatable = false) private String userTemplate;
    @Column(name = "output_schema", nullable = false, columnDefinition = "json", updatable = false) private String outputSchema;
    @Column(precision = 3, scale = 2, updatable = false) private BigDecimal temperature;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false, length = 64, updatable = false) private String checksum;
    @Column(length = 500, updatable = false) private String notes;
    @Column(name = "created_by", updatable = false) private Long createdBy;

    protected PromptVersion() {
    }

    public PromptVersion(String promptKey, int version, String systemTemplate, String userTemplate, String outputSchema,
                         BigDecimal temperature, String checksum, String notes, Long createdBy) {
        this.promptKey = promptKey;
        this.version = version;
        this.systemTemplate = systemTemplate;
        this.userTemplate = userTemplate;
        this.outputSchema = outputSchema;
        this.temperature = temperature;
        this.checksum = checksum;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void setActive(boolean active) { this.active = active; }

    public String getPromptKey() { return promptKey; }
    public int getVersion() { return version; }
    public String getSystemTemplate() { return systemTemplate; }
    public String getUserTemplate() { return userTemplate; }
    public String getOutputSchema() { return outputSchema; }
    public BigDecimal getTemperature() { return temperature; }
    public boolean isActive() { return active; }
    public String getChecksum() { return checksum; }
    public String getNotes() { return notes; }
}
