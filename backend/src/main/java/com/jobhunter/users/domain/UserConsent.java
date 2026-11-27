package com.jobhunter.users.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_consents")
public class UserConsent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserConsent() {
    }

    public UserConsent(Long userId, ConsentType consentType, String provider) {
        this.userId = userId;
        this.consentType = consentType;
        this.provider = provider;
    }

    public void set(boolean value, Instant now) {
        if (value && !granted) {
            grantedAt = now;
            revokedAt = null;
        } else if (!value && granted) {
            revokedAt = now;
        }
        granted = value;
    }

    public Long getUserId() { return userId; }
    public ConsentType getConsentType() { return consentType; }
    public String getProvider() { return provider; }
    public boolean isGranted() { return granted; }
    public Instant getGrantedAt() { return grantedAt; }
}
