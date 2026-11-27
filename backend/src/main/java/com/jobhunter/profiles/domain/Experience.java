package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.EmploymentType;
import com.jobhunter.profiles.domain.Enums.ItemSource;
import com.jobhunter.profiles.domain.Enums.WorkModality;
import com.jobhunter.shared.json.StringListConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Professional EMPLOYMENT experience. Personal/academic work belongs in {@link Project}. */
@Entity
@Table(name = "experiences")
@SQLRestriction("deleted_at is null")
public class Experience extends SoftDeletableEntity {

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;
    @Column(name = "company_url", length = 500)
    private String companyUrl;
    @Column(name = "position_title", nullable = false, length = 200)
    private String positionTitle;
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 16)
    private EmploymentType employmentType;
    @Enumerated(EnumType.STRING)
    @Column(name = "work_modality", length = 8)
    private WorkModality workModality;
    @Column(length = 200)
    private String location;
    @Column(length = 2)
    private String country;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "is_current", nullable = false)
    private boolean current;
    @Column(columnDefinition = "text")
    private String description;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "json")
    private List<String> achievements = new ArrayList<>();
    @Column(nullable = false)
    private boolean verified;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemSource source = ItemSource.MANUAL;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "experience_skills", joinColumns = @JoinColumn(name = "experience_id"))
    @Column(name = "skill_id")
    private Set<Long> skillIds = new LinkedHashSet<>();

    protected Experience() {
    }

    public Experience(Long userId, ItemSource source) {
        super(userId);
        this.source = source;
        this.verified = source == ItemSource.MANUAL;
    }

    public void update(String companyName, String companyUrl, String positionTitle, EmploymentType employmentType,
                       WorkModality workModality, String location, String country, LocalDate startDate,
                       LocalDate endDate, boolean current, String description, List<String> achievements,
                       Set<Long> skillIds) {
        this.companyName = companyName;
        this.companyUrl = companyUrl;
        this.positionTitle = positionTitle;
        this.employmentType = employmentType;
        this.workModality = workModality;
        this.location = location;
        this.country = country;
        this.startDate = startDate;
        this.current = current;
        this.endDate = current ? null : endDate;
        this.description = description;
        this.achievements = achievements == null ? new ArrayList<>() : new ArrayList<>(achievements);
        this.skillIds.clear();
        if (skillIds != null) this.skillIds.addAll(skillIds);
    }

    public void markVerified() { this.verified = true; }

    public String getCompanyName() { return companyName; }
    public String getCompanyUrl() { return companyUrl; }
    public String getPositionTitle() { return positionTitle; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public WorkModality getWorkModality() { return workModality; }
    public String getLocation() { return location; }
    public String getCountry() { return country; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isCurrent() { return current; }
    public String getDescription() { return description; }
    public List<String> getAchievements() { return achievements; }
    public boolean isVerified() { return verified; }
    public ItemSource getSource() { return source; }
    public Set<Long> getSkillIds() { return skillIds; }
}
