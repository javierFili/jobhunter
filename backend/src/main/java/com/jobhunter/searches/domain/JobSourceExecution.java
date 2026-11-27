package com.jobhunter.searches.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One connector call inside a search run (or a watch-list sweep). */
@Entity
@Table(name = "job_source_executions")
public class JobSourceExecution {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_source_id", nullable = false) private Long jobSourceId;
    @Column(name = "search_execution_id") private Long searchExecutionId;
    @Column(name = "user_id") private Long userId;
    @Column(nullable = false, length = 16) private String status = "RUNNING";
    @Column(name = "external_run_id", length = 64) private String externalRunId;
    @Column(name = "external_dataset_id", length = 64) private String externalDatasetId;
    @Column(name = "request_count", nullable = false) private int requestCount;
    @Column(name = "items_fetched", nullable = false) private int itemsFetched;
    @Column(name = "items_new", nullable = false) private int itemsNew;
    @Column(name = "items_duplicate", nullable = false) private int itemsDuplicate;
    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6) private BigDecimal costUsd = BigDecimal.ZERO;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message", length = 1000) private String errorMessage;

    protected JobSourceExecution() {
    }

    public JobSourceExecution(Long jobSourceId, Long searchExecutionId, Long userId, Instant now) {
        this.jobSourceId = jobSourceId;
        this.searchExecutionId = searchExecutionId;
        this.userId = userId;
        this.startedAt = now;
    }

    public void finish(String status, int requests, int fetched, int fresh, int duplicates, BigDecimal cost,
                       String runId, String datasetId, String errorCode, String errorMessage, Instant now) {
        this.status = status;
        this.requestCount = requests;
        this.itemsFetched = fetched;
        this.itemsNew = fresh;
        this.itemsDuplicate = duplicates;
        this.costUsd = cost == null ? BigDecimal.ZERO : cost;
        this.externalRunId = runId;
        this.externalDatasetId = datasetId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(1000, errorMessage.length()));
        this.finishedAt = now;
    }

    public Long getId() { return id; }
    public Long getJobSourceId() { return jobSourceId; }
    public Long getSearchExecutionId() { return searchExecutionId; }
    public String getStatus() { return status; }
    public int getRequestCount() { return requestCount; }
    public int getItemsFetched() { return itemsFetched; }
    public int getItemsNew() { return itemsNew; }
    public int getItemsDuplicate() { return itemsDuplicate; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getErrorMessage() { return errorMessage; }
}
