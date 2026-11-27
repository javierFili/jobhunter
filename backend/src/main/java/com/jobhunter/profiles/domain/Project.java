package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.ItemSource;
import com.jobhunter.profiles.domain.Enums.ProjectKind;
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

@Entity
@Table(name = "projects")
@SQLRestriction("deleted_at is null")
public class Project extends SoftDeletableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectKind kind;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 150)
    private String role;
    @Column(columnDefinition = "text")
    private String description;
    @Column(length = 500)
    private String url;
    @Column(name = "repo_url", length = 500)
    private String repoUrl;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "json")
    private List<String> highlights = new ArrayList<>();
    @Column(nullable = false)
    private boolean verified;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemSource source = ItemSource.MANUAL;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "project_skills", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "skill_id")
    private Set<Long> skillIds = new LinkedHashSet<>();

    protected Project() {
    }

    public Project(Long userId, ItemSource source) {
        super(userId);
        this.source = source;
        this.verified = source == ItemSource.MANUAL;
    }

    public void update(ProjectKind kind, String name, String role, String description, String url, String repoUrl,
                       LocalDate startDate, LocalDate endDate, List<String> highlights, Set<Long> skillIds) {
        this.kind = kind;
        this.name = name;
        this.role = role;
        this.description = description;
        this.url = url;
        this.repoUrl = repoUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.highlights = highlights == null ? new ArrayList<>() : new ArrayList<>(highlights);
        this.skillIds.clear();
        if (skillIds != null) this.skillIds.addAll(skillIds);
    }

    public void markVerified() { this.verified = true; }

    public ProjectKind getKind() { return kind; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
    public String getRepoUrl() { return repoUrl; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<String> getHighlights() { return highlights; }
    public boolean isVerified() { return verified; }
    public ItemSource getSource() { return source; }
    public Set<Long> getSkillIds() { return skillIds; }
}
