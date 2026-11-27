package com.jobhunter.profiles.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

/** User-owned, UI-edited item with soft delete. */
@MappedSuperclass
public abstract class SoftDeletableEntity extends VersionedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SoftDeletableEntity() {
    }

    protected SoftDeletableEntity(Long userId) {
        this.userId = userId;
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
