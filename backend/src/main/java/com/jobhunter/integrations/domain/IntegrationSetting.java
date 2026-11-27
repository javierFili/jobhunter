package com.jobhunter.integrations.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "integration_settings")
public class IntegrationSetting extends VersionedEntity {

    public enum Status { UNKNOWN, OK, ERROR, DISABLED }

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_code", nullable = false, length = 24, updatable = false)
    private IntegrationCode code;
    @Column(nullable = false)
    private boolean enabled;
    @Column(columnDefinition = "json")
    private String config;
    @Column(name = "secret_ciphertext")
    private byte[] secretCiphertext;
    @Column(name = "secret_key_version", nullable = false)
    private int secretKeyVersion = 1;
    @Column(name = "secret_last4", length = 4)
    private String secretLast4;
    @Column(name = "daily_limit")
    private Integer dailyLimit;
    @Column(name = "monthly_limit")
    private Integer monthlyLimit;
    @Column(name = "monthly_budget_usd", precision = 10, scale = 2)
    private BigDecimal monthlyBudgetUsd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.UNKNOWN;
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;
    @Column(name = "last_error_at")
    private Instant lastErrorAt;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected IntegrationSetting() {
    }

    public IntegrationSetting(IntegrationCode code) {
        this.code = code;
        this.config = "{}";
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) this.status = Status.DISABLED;
        else if (status == Status.DISABLED) this.status = Status.UNKNOWN;
    }

    public void setConfig(String config) { this.config = config; }

    public void setLimits(Integer daily, Integer monthly, BigDecimal monthlyBudgetUsd) {
        this.dailyLimit = daily;
        this.monthlyLimit = monthly;
        this.monthlyBudgetUsd = monthlyBudgetUsd;
    }

    public void setSecret(byte[] ciphertext, String last4) {
        this.secretCiphertext = ciphertext;
        this.secretLast4 = last4;
    }

    public void clearSecret() {
        this.secretCiphertext = null;
        this.secretLast4 = null;
    }

    public void recordSuccess(Instant now) {
        this.lastSuccessAt = now;
        if (enabled) this.status = Status.OK;
    }

    public void recordError(Instant now, String message) {
        this.lastErrorAt = now;
        this.lastError = message == null ? null : message.substring(0, Math.min(1000, message.length()));
        if (enabled) this.status = Status.ERROR;
    }

    public IntegrationCode getCode() { return code; }
    public boolean isEnabled() { return enabled; }
    public String getConfig() { return config; }
    public byte[] getSecretCiphertext() { return secretCiphertext; }
    public String getSecretLast4() { return secretLast4; }
    public Integer getDailyLimit() { return dailyLimit; }
    public Integer getMonthlyLimit() { return monthlyLimit; }
    public BigDecimal getMonthlyBudgetUsd() { return monthlyBudgetUsd; }
    public Status getStatus() { return status; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public String getLastError() { return lastError; }
}
