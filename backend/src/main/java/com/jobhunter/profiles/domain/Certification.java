package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.ItemSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "certifications")
@SQLRestriction("deleted_at is null")
public class Certification extends SoftDeletableEntity {

    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 200)
    private String issuer;
    @Column(name = "issued_at")
    private LocalDate issuedAt;
    @Column(name = "expires_at")
    private LocalDate expiresAt;
    @Column(name = "credential_id", length = 200)
    private String credentialId;
    @Column(name = "credential_url", length = 500)
    private String credentialUrl;
    @Column(nullable = false)
    private boolean verified;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemSource source = ItemSource.MANUAL;

    protected Certification() {
    }

    public Certification(Long userId, ItemSource source) {
        super(userId);
        this.source = source;
        this.verified = source == ItemSource.MANUAL;
    }

    public void update(String name, String issuer, LocalDate issuedAt, LocalDate expiresAt, String credentialId,
                       String credentialUrl) {
        this.name = name;
        this.issuer = issuer;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.credentialId = credentialId;
        this.credentialUrl = credentialUrl;
    }

    public void markVerified() { this.verified = true; }

    public String getName() { return name; }
    public String getIssuer() { return issuer; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public String getCredentialId() { return credentialId; }
    public String getCredentialUrl() { return credentialUrl; }
    public boolean isVerified() { return verified; }
    public ItemSource getSource() { return source; }
}
