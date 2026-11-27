package com.jobhunter.auth.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Opaque refresh token, stored only as SHA-256 hash. Tokens of one login session share a {@code familyId};
 * presenting an already-rotated token revokes the whole family (theft detection).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "binary(32)")
    private byte[] tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(length = 45)
    private String ip;

    protected RefreshToken() {
    }

    public RefreshToken(Long userId, byte[] tokenHash, String familyId, Instant expiresAt, String userAgent, String ip) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        this.ip = ip;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, Long replacedBy) {
        if (revokedAt == null) {
            revokedAt = now;
            replacedById = replacedBy;
        }
    }

    public Long getUserId() { return userId; }
    public String getFamilyId() { return familyId; }
    public Instant getRevokedAt() { return revokedAt; }
    public Long getReplacedById() { return replacedById; }
}
