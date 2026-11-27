package com.jobhunter.resumes.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "resumes")
@SQLRestriction("deleted_at is null")
public class Resume extends VersionedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "profile_id")
    private Long profileId;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 2)
    private String language;
    @Column(name = "template_id", nullable = false, length = 32)
    private String templateId;
    @Column(name = "current_version_id")
    private Long currentVersionId;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Resume() {
    }

    public Resume(Long userId, Long profileId, String title, String language, String templateId) {
        this.userId = userId;
        this.profileId = profileId;
        this.title = title;
        this.language = language;
        this.templateId = templateId;
    }

    public void rename(String title, String templateId) {
        if (title != null) this.title = title;
        if (templateId != null) this.templateId = templateId;
    }

    public void setCurrentVersion(Long versionId) { this.currentVersionId = versionId; }
    public void markDeleted(Instant now) { this.deletedAt = now; }

    public Long getUserId() { return userId; }
    public Long getProfileId() { return profileId; }
    public String getTitle() { return title; }
    public String getLanguage() { return language; }
    public String getTemplateId() { return templateId; }
    public Long getCurrentVersionId() { return currentVersionId; }
}
