package com.jobhunter.jobs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "user_job_states")
public class UserJobState {

    @Embeddable
    public record Id(@Column(name = "user_id") Long userId, @Column(name = "job_offer_id") Long jobOfferId)
            implements Serializable {
    }

    @EmbeddedId
    private Id id;
    @Column(nullable = false) private boolean saved;
    @Column(nullable = false) private boolean hidden;
    @Column(name = "seen_at") private Instant seenAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserJobState() {
    }

    public UserJobState(Long userId, Long jobOfferId) {
        this.id = new Id(userId, jobOfferId);
        this.updatedAt = Instant.now();
    }

    public void update(Boolean saved, Boolean hidden) {
        if (saved != null) this.saved = saved;
        if (hidden != null) this.hidden = hidden;
        this.updatedAt = Instant.now();
    }

    public Id getId() { return id; }
    public boolean isSaved() { return saved; }
    public boolean isHidden() { return hidden; }
}
