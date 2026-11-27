package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.EvidenceLevel;
import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_skills")
public class UserSkill extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "skill_id", nullable = false, updatable = false)
    private Long skillId;
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_level", nullable = false, length = 16)
    private EvidenceLevel evidenceLevel;
    @Column(name = "self_rating")
    private Integer selfRating;
    @Column(name = "last_used_year")
    private Integer lastUsedYear;
    @Column(nullable = false)
    private boolean verified;

    protected UserSkill() {
    }

    public UserSkill(Long userId, Long skillId, boolean verified) {
        this.userId = userId;
        this.skillId = skillId;
        this.verified = verified;
    }

    public void update(EvidenceLevel evidenceLevel, Integer selfRating, Integer lastUsedYear) {
        this.evidenceLevel = evidenceLevel;
        this.selfRating = selfRating;
        this.lastUsedYear = lastUsedYear;
    }

    public void markVerified() { this.verified = true; }

    public Long getUserId() { return userId; }
    public Long getSkillId() { return skillId; }
    public EvidenceLevel getEvidenceLevel() { return evidenceLevel; }
    public Integer getSelfRating() { return selfRating; }
    public Integer getLastUsedYear() { return lastUsedYear; }
    public boolean isVerified() { return verified; }
}
