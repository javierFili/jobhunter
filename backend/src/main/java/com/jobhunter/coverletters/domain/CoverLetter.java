package com.jobhunter.coverletters.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** One immutable version of an application text. Versions of the same letter share {@code letterGroupId}. */
@Entity
@Table(name = "cover_letters")
public class CoverLetter extends BaseEntity {

    public static final java.util.Set<String> FORMATS = java.util.Set.of("FORMAL_LETTER", "SHORT_MESSAGE", "EMAIL_INTRO",
            "FREELANCE_PROPOSAL", "LINKEDIN_MESSAGE");
    public static final java.util.Set<String> TONES = java.util.Set.of("FORMAL", "NEUTRAL", "FRIENDLY");

    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "letter_group_id") private Long letterGroupId;
    @Column(name = "job_offer_id", updatable = false) private Long jobOfferId;
    @Column(nullable = false, length = 24, updatable = false) private String format;
    @Column(nullable = false, length = 2, updatable = false) private String language;
    @Column(nullable = false, length = 10, updatable = false) private String tone;
    @Column(name = "version_no", nullable = false, updatable = false) private int versionNo;
    @Column(name = "parent_id", updatable = false) private Long parentId;
    @Column(nullable = false, length = 10) private String status = "DRAFT";
    @Column(length = 300, updatable = false) private String subject;
    @Column(nullable = false, columnDefinition = "mediumtext", updatable = false) private String body;
    @Column(columnDefinition = "json", updatable = false) private String suggestions;
    @Column(name = "validation_report", columnDefinition = "json", updatable = false) private String validationReport;
    @Column(name = "ai_analysis_id", updatable = false) private Long aiAnalysisId;
    @Column(length = 16, updatable = false) private String provider;
    @Column(length = 64, updatable = false) private String model;
    @Column(name = "approved_at") private Instant approvedAt;

    protected CoverLetter() {
    }

    public CoverLetter(Long userId, Long letterGroupId, Long jobOfferId, String format, String language, String tone,
                       int versionNo, Long parentId, String subject, String body, String suggestions,
                       String validationReport, Long aiAnalysisId, String provider, String model) {
        this.userId = userId;
        this.letterGroupId = letterGroupId;
        this.jobOfferId = jobOfferId;
        this.format = format;
        this.language = language;
        this.tone = tone;
        this.versionNo = versionNo;
        this.parentId = parentId;
        this.subject = subject;
        this.body = body;
        this.suggestions = suggestions;
        this.validationReport = validationReport;
        this.aiAnalysisId = aiAnalysisId;
        this.provider = provider;
        this.model = model;
    }

    /** First version of a group points to itself. */
    public void setGroup(Long groupId) { this.letterGroupId = groupId; }

    public void approve(Instant now) {
        this.status = "APPROVED";
        this.approvedAt = now;
    }

    public Long getUserId() { return userId; }
    public Long getLetterGroupId() { return letterGroupId; }
    public Long getJobOfferId() { return jobOfferId; }
    public String getFormat() { return format; }
    public String getLanguage() { return language; }
    public String getTone() { return tone; }
    public int getVersionNo() { return versionNo; }
    public Long getParentId() { return parentId; }
    public String getStatus() { return status; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getSuggestions() { return suggestions; }
    public String getValidationReport() { return validationReport; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public Instant getApprovedAt() { return approvedAt; }
}
