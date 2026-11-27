package com.jobhunter.matching.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Latest compatibility evaluation of one job for one user (and professional profile). */
@Entity
@Table(name = "job_matches")
public class JobMatch extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "profile_key", nullable = false, updatable = false) private Long profileKey;
    @Column(name = "job_offer_id", nullable = false, updatable = false) private Long jobOfferId;
    @Column(name = "profile_snapshot_hash", nullable = false, length = 64) private String profileSnapshotHash;
    @Column(name = "job_content_hash", nullable = false, columnDefinition = "binary(32)") private byte[] jobContentHash;
    @Column(name = "score_total", nullable = false) private int scoreTotal;
    @Column(name = "score_skills") private Integer scoreSkills;
    @Column(name = "score_experience") private Integer scoreExperience;
    @Column(name = "score_language") private Integer scoreLanguage;
    @Column(name = "score_seniority") private Integer scoreSeniority;
    @Column(name = "score_location") private Integer scoreLocation;
    @Column(name = "score_compensation") private Integer scoreCompensation;
    @Column(name = "score_contract") private Integer scoreContract;
    @Column(nullable = false, length = 16) private String classification;
    @Column(nullable = false, length = 10) private String recommendation;
    @Column(columnDefinition = "json") private String strengths;
    @Column(columnDefinition = "json") private String gaps;
    @Column(columnDefinition = "json") private String reasons;
    @Column(nullable = false, length = 16) private String method;
    @Column(name = "ai_analysis_id") private Long aiAnalysisId;

    protected JobMatch() {
    }

    public JobMatch(Long userId, Long profileKey, Long jobOfferId) {
        this.userId = userId;
        this.profileKey = profileKey;
        this.jobOfferId = jobOfferId;
    }

    public void update(String snapshotHash, byte[] contentHash, int total, Integer skills, Integer experience,
                       Integer language, Integer seniority, Integer location, Integer compensation, Integer contract,
                       String classification, String recommendation, String strengths, String gaps, String reasons,
                       String method, Long aiAnalysisId) {
        this.profileSnapshotHash = snapshotHash;
        this.jobContentHash = contentHash;
        this.scoreTotal = total;
        this.scoreSkills = skills;
        this.scoreExperience = experience;
        this.scoreLanguage = language;
        this.scoreSeniority = seniority;
        this.scoreLocation = location;
        this.scoreCompensation = compensation;
        this.scoreContract = contract;
        this.classification = classification;
        this.recommendation = recommendation;
        this.strengths = strengths;
        this.gaps = gaps;
        this.reasons = reasons;
        this.method = method;
        this.aiAnalysisId = aiAnalysisId;
    }

    public Long getUserId() { return userId; }
    public Long getProfileKey() { return profileKey; }
    public Long getJobOfferId() { return jobOfferId; }
    public String getProfileSnapshotHash() { return profileSnapshotHash; }
    public byte[] getJobContentHash() { return jobContentHash; }
    public int getScoreTotal() { return scoreTotal; }
    public Integer getScoreSkills() { return scoreSkills; }
    public Integer getScoreExperience() { return scoreExperience; }
    public Integer getScoreLanguage() { return scoreLanguage; }
    public Integer getScoreSeniority() { return scoreSeniority; }
    public Integer getScoreLocation() { return scoreLocation; }
    public Integer getScoreCompensation() { return scoreCompensation; }
    public Integer getScoreContract() { return scoreContract; }
    public String getClassification() { return classification; }
    public String getRecommendation() { return recommendation; }
    public String getStrengths() { return strengths; }
    public String getGaps() { return gaps; }
    public String getReasons() { return reasons; }
    public String getMethod() { return method; }
    public Long getAiAnalysisId() { return aiAnalysisId; }
}
