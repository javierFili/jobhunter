package com.jobhunter.jobs.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Where (and as what) a vacancy was published. Never discarded when offers are merged. */
@Entity
@Table(name = "job_source_references")
public class JobSourceReference extends BaseEntity {

    @Column(name = "job_offer_id", nullable = false) private Long jobOfferId;
    @Column(name = "job_source_id", nullable = false, updatable = false) private Long jobSourceId;
    @Column(name = "external_id", nullable = false, length = 200, updatable = false) private String externalId;
    @Column(name = "source_url", nullable = false, length = 1000) private String sourceUrl;
    @Column(name = "apply_url", length = 1000) private String applyUrl;
    @Column(name = "attribution_text", length = 300) private String attributionText;
    @Column(name = "raw_payload", columnDefinition = "json") private String rawPayload;
    @Column(name = "raw_content_hash", columnDefinition = "binary(32)") private byte[] rawContentHash;
    @Column(name = "first_seen_at", nullable = false) private Instant firstSeenAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "removed_at") private Instant removedAt;

    protected JobSourceReference() {
    }

    public JobSourceReference(Long jobOfferId, Long jobSourceId, String externalId, Instant now) {
        this.jobOfferId = jobOfferId;
        this.jobSourceId = jobSourceId;
        this.externalId = externalId;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    public void update(String sourceUrl, String applyUrl, String attribution, String rawPayload, byte[] rawHash, Instant now) {
        this.sourceUrl = sourceUrl;
        this.applyUrl = applyUrl;
        this.attributionText = attribution;
        this.rawPayload = rawPayload;
        this.rawContentHash = rawHash;
        this.lastSeenAt = now;
        this.removedAt = null;
    }

    public void markRemoved(Instant now) { if (removedAt == null) removedAt = now; }

    public Long getJobOfferId() { return jobOfferId; }
    public Long getJobSourceId() { return jobSourceId; }
    public String getExternalId() { return externalId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getApplyUrl() { return applyUrl; }
    public String getAttributionText() { return attributionText; }
    public byte[] getRawContentHash() { return rawContentHash; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRemovedAt() { return removedAt; }
}
