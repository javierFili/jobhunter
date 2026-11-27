package com.jobhunter.jobsources.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Company followed on an ATS board (Greenhouse/Lever/Ashby have no global search). */
@Entity
@Table(name = "company_watchlist")
public class CompanyWatch extends BaseEntity {

    public static final String TOKEN_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";

    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, length = 16, updatable = false) private String ats;
    @Column(name = "board_token", nullable = false, length = 120, updatable = false) private String boardToken;
    @Column(name = "company_name", length = 200) private String companyName;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_checked_at") private Instant lastCheckedAt;
    @Column(name = "last_status", length = 16) private String lastStatus;
    @Column(name = "last_job_count") private Integer lastJobCount;

    protected CompanyWatch() {
    }

    public CompanyWatch(Long userId, String ats, String boardToken, String companyName) {
        this.userId = userId;
        this.ats = ats;
        this.boardToken = boardToken;
        this.companyName = companyName;
    }

    public void update(Boolean enabled, String companyName) {
        if (enabled != null) this.enabled = enabled;
        if (companyName != null) this.companyName = companyName.isBlank() ? null : companyName.trim();
    }

    public void checked(Instant now, String status, Integer jobCount) {
        this.lastCheckedAt = now;
        this.lastStatus = status;
        this.lastJobCount = jobCount;
    }

    public String sourceCode() {
        return ats.toLowerCase();
    }

    public Long getUserId() { return userId; }
    public String getAts() { return ats; }
    public String getBoardToken() { return boardToken; }
    public String getCompanyName() { return companyName; }
    public boolean isEnabled() { return enabled; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public String getLastStatus() { return lastStatus; }
    public Integer getLastJobCount() { return lastJobCount; }
}
