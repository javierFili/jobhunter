package com.jobhunter.searches.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "search_executions")
public class SearchExecution {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "saved_search_id", nullable = false) private Long savedSearchId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "run_trigger", nullable = false, length = 16) private String trigger;
    @Column(nullable = false, length = 16) private String status = "RUNNING";
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "jobs_found", nullable = false) private int jobsFound;
    @Column(name = "jobs_new", nullable = false) private int jobsNew;
    @Column(name = "jobs_duplicate", nullable = false) private int jobsDuplicate;
    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6) private BigDecimal costUsd = BigDecimal.ZERO;
    @Column(name = "error_summary", length = 1000) private String errorSummary;

    protected SearchExecution() {
    }

    public SearchExecution(Long savedSearchId, Long userId, String trigger, Instant now) {
        this.savedSearchId = savedSearchId;
        this.userId = userId;
        this.trigger = trigger;
        this.startedAt = now;
    }

    public void finish(String status, int found, int fresh, int duplicates, BigDecimal cost, String errors, Instant now) {
        this.status = status;
        this.jobsFound = found;
        this.jobsNew = fresh;
        this.jobsDuplicate = duplicates;
        this.costUsd = cost;
        this.errorSummary = errors == null ? null : errors.substring(0, Math.min(1000, errors.length()));
        this.finishedAt = now;
    }

    public Long getId() { return id; }
    public Long getSavedSearchId() { return savedSearchId; }
    public Long getUserId() { return userId; }
    public String getTrigger() { return trigger; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getJobsFound() { return jobsFound; }
    public int getJobsNew() { return jobsNew; }
    public int getJobsDuplicate() { return jobsDuplicate; }
    public BigDecimal getCostUsd() { return costUsd; }
    public String getErrorSummary() { return errorSummary; }
}
