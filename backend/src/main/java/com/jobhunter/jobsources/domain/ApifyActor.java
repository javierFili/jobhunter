package com.jobhunter.jobsources.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An Apify Actor configured from the admin panel. Disabled by default; enabling requires a written assessment
 * (documentation, pricing, data quality, platform terms). Never configured with login cookies or CAPTCHA solving.
 */
@Entity
@Table(name = "apify_actors")
public class ApifyActor extends VersionedEntity {

    @Column(name = "actor_id", nullable = false, length = 120) private String actorId;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(length = 40) private String platform;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "input_template", nullable = false, columnDefinition = "json") private String inputTemplate;
    @Column(name = "field_mapping", nullable = false, columnDefinition = "json") private String fieldMapping;
    @Column(name = "max_items", nullable = false) private int maxItems;
    @Column(name = "max_charge_usd", nullable = false, precision = 8, scale = 2) private BigDecimal maxChargeUsd;
    @Column(name = "timeout_secs", nullable = false) private int timeoutSecs;
    @Column(name = "memory_mb") private Integer memoryMb;
    @Column(name = "pricing_model", length = 32) private String pricingModel;
    @Column(name = "assessment_notes", columnDefinition = "text") private String assessmentNotes;
    @Column(name = "assessed_at") private Instant assessedAt;

    protected ApifyActor() {
    }

    public ApifyActor(String actorId) {
        this.actorId = actorId;
    }

    public void update(String displayName, String platform, String inputTemplate, String fieldMapping, int maxItems,
                       BigDecimal maxChargeUsd, int timeoutSecs, Integer memoryMb, String pricingModel,
                       String assessmentNotes, Instant now) {
        this.displayName = displayName;
        this.platform = platform;
        this.inputTemplate = inputTemplate;
        this.fieldMapping = fieldMapping;
        this.maxItems = maxItems;
        this.maxChargeUsd = maxChargeUsd;
        this.timeoutSecs = timeoutSecs;
        this.memoryMb = memoryMb;
        this.pricingModel = pricingModel;
        if (assessmentNotes != null && !assessmentNotes.equals(this.assessmentNotes)) {
            this.assessmentNotes = assessmentNotes;
            this.assessedAt = now;
        }
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String sourceCode() { return "apify:" + getId(); }

    public String getActorId() { return actorId; }
    public String getDisplayName() { return displayName; }
    public String getPlatform() { return platform; }
    public boolean isEnabled() { return enabled; }
    public String getInputTemplate() { return inputTemplate; }
    public String getFieldMapping() { return fieldMapping; }
    public int getMaxItems() { return maxItems; }
    public BigDecimal getMaxChargeUsd() { return maxChargeUsd; }
    public int getTimeoutSecs() { return timeoutSecs; }
    public Integer getMemoryMb() { return memoryMb; }
    public String getPricingModel() { return pricingModel; }
    public String getAssessmentNotes() { return assessmentNotes; }
    public Instant getAssessedAt() { return assessedAt; }
}
