package com.jobhunter.jobs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Requirement extracted from a posting (by the deterministic dictionary or by AI analysis). */
@Entity
@Table(name = "job_requirements")
public class JobRequirement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_offer_id", nullable = false) private Long jobOfferId;
    @Column(nullable = false, length = 16) private String kind;
    @Column(nullable = false, length = 12) private String importance;
    @Column(name = "skill_id") private Long skillId;
    @Column(nullable = false, length = 500) private String text;
    @Column(name = "min_years", precision = 4, scale = 1) private BigDecimal minYears;
    @Column(name = "language_code", length = 2) private String languageCode;
    @Column(length = 8) private String level;
    @Column(name = "extracted_by", nullable = false, length = 16) private String extractedBy;
    @Column(name = "ai_analysis_id") private Long aiAnalysisId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected JobRequirement() {
    }

    public JobRequirement(Long jobOfferId, String kind, String importance, Long skillId, String text, BigDecimal minYears,
                          String languageCode, String level, String extractedBy, Long aiAnalysisId) {
        this.jobOfferId = jobOfferId;
        this.kind = kind;
        this.importance = importance;
        this.skillId = skillId;
        this.text = text.length() > 500 ? text.substring(0, 500) : text;
        this.minYears = minYears;
        this.languageCode = languageCode;
        this.level = level;
        this.extractedBy = extractedBy;
        this.aiAnalysisId = aiAnalysisId;
    }

    public Long getId() { return id; }
    public Long getJobOfferId() { return jobOfferId; }
    public String getKind() { return kind; }
    public String getImportance() { return importance; }
    public Long getSkillId() { return skillId; }
    public String getText() { return text; }
    public BigDecimal getMinYears() { return minYears; }
    public String getLanguageCode() { return languageCode; }
    public String getLevel() { return level; }
    public String getExtractedBy() { return extractedBy; }
}
