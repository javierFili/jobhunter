package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.EducationStatus;
import com.jobhunter.profiles.domain.Enums.ItemSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "educations")
@SQLRestriction("deleted_at is null")
public class Education extends SoftDeletableEntity {

    @Column(nullable = false, length = 200)
    private String institution;
    @Column(length = 200)
    private String degree;
    @Column(name = "field_of_study", length = 200)
    private String fieldOfStudy;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EducationStatus status = EducationStatus.COMPLETED;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false)
    private boolean verified;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemSource source = ItemSource.MANUAL;

    protected Education() {
    }

    public Education(Long userId, ItemSource source) {
        super(userId);
        this.source = source;
        this.verified = source == ItemSource.MANUAL;
    }

    public void update(String institution, String degree, String fieldOfStudy, LocalDate startDate, LocalDate endDate,
                       EducationStatus status, String description) {
        this.institution = institution;
        this.degree = degree;
        this.fieldOfStudy = fieldOfStudy;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status == null ? EducationStatus.COMPLETED : status;
        this.description = description;
    }

    public void markVerified() { this.verified = true; }

    public String getInstitution() { return institution; }
    public String getDegree() { return degree; }
    public String getFieldOfStudy() { return fieldOfStudy; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public EducationStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public boolean isVerified() { return verified; }
    public ItemSource getSource() { return source; }
}
