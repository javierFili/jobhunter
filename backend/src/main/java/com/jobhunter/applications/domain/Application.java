package com.jobhunter.applications.domain;

import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "applications")
@SQLRestriction("deleted_at is null")
public class Application extends VersionedEntity {

    public enum Method { EMAIL, EXTERNAL_FORM, CAREER_PORTAL, LINKEDIN, FREELANCE_PLATFORM, OTHER }

    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "job_offer_id") private Long jobOfferId;
    @Column(name = "company_name", nullable = false, length = 200) private String companyName;
    @Column(name = "position_title", nullable = false, length = 300) private String positionTitle;
    @Column(name = "job_url", length = 1000) private String jobUrl;
    @Column(name = "source_label", length = 100) private String sourceLabel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ApplicationStatus status = ApplicationStatus.DISCOVERED;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Method method;
    @Column(name = "resume_id") private Long resumeId;
    @Column(name = "resume_version_no") private Integer resumeVersionNo;
    @Column(name = "cover_letter_id") private Long coverLetterId;
    @Column(name = "salary_expectation", precision = 12, scale = 2) private BigDecimal salaryExpectation;
    @Column(name = "salary_currency", length = 3) private String salaryCurrency;
    @Column(name = "salary_period", length = 8) private String salaryPeriod;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "follow_up_at") private LocalDate followUpAt;
    @Column(name = "follow_up_notified_on") private LocalDate followUpNotifiedOn;
    @Column(columnDefinition = "text") private String notes;
    @Column(name = "kanban_order", nullable = false) private int kanbanOrder;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected Application() {
    }

    public Application(Long userId, Long jobOfferId, String companyName, String positionTitle, String jobUrl, String sourceLabel) {
        this.userId = userId;
        this.jobOfferId = jobOfferId;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.jobUrl = jobUrl;
        this.sourceLabel = sourceLabel;
    }

    public ApplicationStatus transition(ApplicationStatus target) {
        if (target == ApplicationStatus.APPLIED || !status.canTransitionTo(target)) {
            throw ApiException.badRequest("INVALID_TRANSITION", "No se puede pasar de " + status + " a " + target
                    + (target == ApplicationStatus.APPLIED ? " (usa «Marcar como enviada»)" : ""));
        }
        ApplicationStatus from = status;
        status = target;
        return from;
    }

    public ApplicationStatus markSubmitted(Method method, Instant when) {
        if (!ApplicationStatus.PRE_APPLIED.contains(status)) {
            throw ApiException.badRequest("INVALID_TRANSITION", "La postulación ya fue enviada o está cerrada");
        }
        ApplicationStatus from = status;
        this.status = ApplicationStatus.APPLIED;
        this.method = method;
        this.appliedAt = when;
        return from;
    }

    public void update(String companyName, String positionTitle, String jobUrl, Method method, Long resumeId,
                       Integer resumeVersionNo, Long coverLetterId, BigDecimal salaryExpectation, String salaryCurrency,
                       String salaryPeriod, LocalDate followUpAt, String notes) {
        if (companyName != null) this.companyName = companyName;
        if (positionTitle != null) this.positionTitle = positionTitle;
        if (jobUrl != null) this.jobUrl = jobUrl.isBlank() ? null : jobUrl;
        if (method != null) this.method = method;
        if (resumeId != null) {
            this.resumeId = resumeId;
            this.resumeVersionNo = resumeVersionNo;
        }
        if (coverLetterId != null) this.coverLetterId = coverLetterId;
        if (salaryExpectation != null) this.salaryExpectation = salaryExpectation;
        if (salaryCurrency != null) this.salaryCurrency = salaryCurrency;
        if (salaryPeriod != null) this.salaryPeriod = salaryPeriod;
        if (followUpAt != null && !followUpAt.equals(this.followUpAt)) {
            this.followUpAt = followUpAt;
            this.followUpNotifiedOn = null;
        }
        if (notes != null) this.notes = notes;
    }

    public void clearFollowUp() { this.followUpAt = null; }
    public void followUpNotified(LocalDate day) { this.followUpNotifiedOn = day; }
    public void setKanbanOrder(int order) { this.kanbanOrder = order; }
    public void markDeleted(Instant now) { this.deletedAt = now; }

    public Long getUserId() { return userId; }
    public Long getJobOfferId() { return jobOfferId; }
    public String getCompanyName() { return companyName; }
    public String getPositionTitle() { return positionTitle; }
    public String getJobUrl() { return jobUrl; }
    public String getSourceLabel() { return sourceLabel; }
    public ApplicationStatus getStatus() { return status; }
    public Method getMethod() { return method; }
    public Long getResumeId() { return resumeId; }
    public Integer getResumeVersionNo() { return resumeVersionNo; }
    public Long getCoverLetterId() { return coverLetterId; }
    public BigDecimal getSalaryExpectation() { return salaryExpectation; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public String getSalaryPeriod() { return salaryPeriod; }
    public Instant getAppliedAt() { return appliedAt; }
    public LocalDate getFollowUpAt() { return followUpAt; }
    public LocalDate getFollowUpNotifiedOn() { return followUpNotifiedOn; }
    public String getNotes() { return notes; }
    public int getKanbanOrder() { return kanbanOrder; }
}
