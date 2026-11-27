package com.jobhunter.resumes.domain;

import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

/** Immutable snapshot of a resume. Edits always create a new version; approved versions never change. */
@Entity
@Table(name = "resume_versions")
public class ResumeVersion extends BaseEntity {

    public enum Origin { UPLOAD, PROFILE, MANUAL, OPTIMIZED, TRANSLATED }

    public enum Status { DRAFT, APPROVED, ARCHIVED }

    @Column(name = "resume_id", nullable = false, updatable = false)
    private Long resumeId;
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;
    @Column(name = "parent_version_id", updatable = false)
    private Long parentVersionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Origin origin;
    @Column(name = "job_offer_id", updatable = false)
    private Long jobOfferId;
    @Column(nullable = false, length = 2, updatable = false)
    private String language;
    @Column(nullable = false, columnDefinition = "json", updatable = false)
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "pdf_file_id")
    private Long pdfFileId;
    @Column(name = "ai_analysis_id", updatable = false)
    private Long aiAnalysisId;
    @Column(name = "validation_report", columnDefinition = "json")
    private String validationReport;
    @Column(length = 300, updatable = false)
    private String note;

    protected ResumeVersion() {
    }

    public ResumeVersion(Long resumeId, int versionNo, Long parentVersionId, Origin origin, Long jobOfferId,
                         String language, String content, Long aiAnalysisId, String validationReport, String note) {
        this.resumeId = resumeId;
        this.versionNo = versionNo;
        this.parentVersionId = parentVersionId;
        this.origin = origin;
        this.jobOfferId = jobOfferId;
        this.language = language;
        this.content = content;
        this.aiAnalysisId = aiAnalysisId;
        this.validationReport = validationReport;
        this.note = note;
    }

    public void approve(Instant now, Long pdfFileId) {
        if (status == Status.ARCHIVED) {
            throw ApiException.conflict("No se puede aprobar una versión archivada");
        }
        this.status = Status.APPROVED;
        this.approvedAt = now;
        this.pdfFileId = pdfFileId;
    }

    public void archive() {
        this.status = Status.ARCHIVED;
    }

    public Long getResumeId() { return resumeId; }
    public int getVersionNo() { return versionNo; }
    public Long getParentVersionId() { return parentVersionId; }
    public Origin getOrigin() { return origin; }
    public Long getJobOfferId() { return jobOfferId; }
    public String getLanguage() { return language; }
    public String getContent() { return content; }
    public Status getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
    public Long getPdfFileId() { return pdfFileId; }
    public Long getAiAnalysisId() { return aiAnalysisId; }
    public String getValidationReport() { return validationReport; }
    public String getNote() { return note; }
}
