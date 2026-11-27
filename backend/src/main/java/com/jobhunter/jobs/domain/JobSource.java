package com.jobhunter.jobs.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "job_sources")
public class JobSource extends BaseEntity {

    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(nullable = false, length = 24)
    private String type;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "terms_url", length = 500)
    private String termsUrl;
    @Column(name = "terms_notes", columnDefinition = "text")
    private String termsNotes;
    @Column(name = "attribution_text", length = 200)
    private String attributionText;
    @Column(name = "attribution_required", nullable = false)
    private boolean attributionRequired;
    @Column(name = "commercial_use_allowed", nullable = false)
    private boolean commercialUseAllowed;
    @Column(name = "min_interval_minutes", nullable = false)
    private int minIntervalMinutes;
    @Column(name = "daily_request_cap")
    private Integer dailyRequestCap;
    @Column(name = "last_fetch_at")
    private Instant lastFetchAt;

    protected JobSource() {
    }

    public JobSource(String code, String type, String displayName) {
        this.code = code;
        this.type = type;
        this.displayName = displayName;
        this.enabled = true;
        this.minIntervalMinutes = 60;
    }

    public void update(Boolean enabled, Integer minIntervalMinutes, Integer dailyRequestCap) {
        if (enabled != null) this.enabled = enabled;
        if (minIntervalMinutes != null) this.minIntervalMinutes = Math.max(1, minIntervalMinutes);
        if (dailyRequestCap != null) this.dailyRequestCap = dailyRequestCap <= 0 ? null : dailyRequestCap;
    }

    public void touchFetch(Instant now) { this.lastFetchAt = now; }

    public String getCode() { return code; }
    public String getType() { return type; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public String getTermsUrl() { return termsUrl; }
    public String getTermsNotes() { return termsNotes; }
    public String getAttributionText() { return attributionText; }
    public boolean isAttributionRequired() { return attributionRequired; }
    public boolean isCommercialUseAllowed() { return commercialUseAllowed; }
    public int getMinIntervalMinutes() { return minIntervalMinutes; }
    public Integer getDailyRequestCap() { return dailyRequestCap; }
    public Instant getLastFetchAt() { return lastFetchAt; }
}
