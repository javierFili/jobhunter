package com.jobhunter.users.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends VersionedEntity {

    public static final int MAX_FAILED_LOGINS = 10;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, length = 10)
    private String locale = "es";

    @Column(nullable = false, length = 64)
    private String timezone = "America/La_Paz";

    @Column(nullable = false, length = 8)
    private String theme = "system";

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.role = role;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public boolean isLocked(Instant now) {
        return status != UserStatus.ACTIVE || (lockedUntil != null && lockedUntil.isAfter(now));
    }

    public void registerFailedLogin(Instant now) {
        failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLogins = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLogins = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void updateSettings(String fullName, String locale, String timezone, String theme) {
        if (fullName != null) this.fullName = fullName.trim();
        if (locale != null) this.locale = locale;
        if (timezone != null) this.timezone = timezone;
        if (theme != null) this.theme = theme;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public String getLocale() { return locale; }
    public String getTimezone() { return timezone; }
    public String getTheme() { return theme; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
