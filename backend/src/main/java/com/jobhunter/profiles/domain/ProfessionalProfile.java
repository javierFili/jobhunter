package com.jobhunter.profiles.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/** A target "persona" (e.g. Java Backend, Laravel Freelance) that selects and orders existing profile items. */
@Entity
@Table(name = "professional_profiles")
@SQLRestriction("deleted_at is null")
public class ProfessionalProfile extends SoftDeletableEntity {

    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 200)
    private String headline;
    @Column(columnDefinition = "text")
    private String summary;
    @Column(name = "target_role", length = 150)
    private String targetRole;
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
    @Column(name = "contact_email", length = 254)
    private String contactEmail;
    @Column(length = 40)
    private String phone;
    @Column(length = 100)
    private String city;
    @Column(length = 2)
    private String country;
    @Column(name = "github_url", length = 500)
    private String githubUrl;
    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;
    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;
    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "profile_experiences", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "experience_id")
    private List<Long> experienceIds = new ArrayList<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "profile_projects", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "project_id")
    private List<Long> projectIds = new ArrayList<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "profile_skills", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "skill_id")
    private List<Long> skillIds = new ArrayList<>();

    protected ProfessionalProfile() {
    }

    public ProfessionalProfile(Long userId) {
        super(userId);
    }

    public void update(String name, String headline, String summary, String targetRole, String contactEmail,
                       String phone, String city, String country, String githubUrl, String linkedinUrl,
                       String portfolioUrl, String websiteUrl) {
        this.name = name;
        this.headline = headline;
        this.summary = summary;
        this.targetRole = targetRole;
        this.contactEmail = contactEmail;
        this.phone = phone;
        this.city = city;
        this.country = country;
        this.githubUrl = githubUrl;
        this.linkedinUrl = linkedinUrl;
        this.portfolioUrl = portfolioUrl;
        this.websiteUrl = websiteUrl;
    }

    public void setDefault(boolean value) { this.isDefault = value; }

    public void setItems(List<Long> experienceIds, List<Long> projectIds, List<Long> skillIds) {
        this.experienceIds.clear();
        this.experienceIds.addAll(experienceIds.stream().distinct().toList());
        this.projectIds.clear();
        this.projectIds.addAll(projectIds.stream().distinct().toList());
        this.skillIds.clear();
        this.skillIds.addAll(skillIds.stream().distinct().toList());
    }

    public String getName() { return name; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getTargetRole() { return targetRole; }
    public boolean isDefault() { return isDefault; }
    public String getContactEmail() { return contactEmail; }
    public String getPhone() { return phone; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public List<Long> getExperienceIds() { return experienceIds; }
    public List<Long> getProjectIds() { return projectIds; }
    public List<Long> getSkillIds() { return skillIds; }
}
