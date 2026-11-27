package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.LanguageLevel;
import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_languages")
public class UserLanguage extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "language_code", nullable = false, length = 2)
    private String languageCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LanguageLevel level;
    @Column(length = 200)
    private String certificate;
    @Column(nullable = false)
    private boolean verified;

    protected UserLanguage() {
    }

    public UserLanguage(Long userId, String languageCode, LanguageLevel level, String certificate, boolean verified) {
        this.userId = userId;
        this.languageCode = languageCode.toLowerCase();
        this.level = level;
        this.certificate = certificate;
        this.verified = verified;
    }

    public void update(LanguageLevel level, String certificate) {
        this.level = level;
        this.certificate = certificate;
    }

    public void markVerified() { this.verified = true; }

    public Long getUserId() { return userId; }
    public String getLanguageCode() { return languageCode; }
    public LanguageLevel getLevel() { return level; }
    public String getCertificate() { return certificate; }
    public boolean isVerified() { return verified; }
}
