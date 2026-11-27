package com.jobhunter.jobs.domain;

import com.jobhunter.shared.domain.BaseEntity;
import com.jobhunter.shared.json.StringListConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Normalized, deduplicated vacancy. Source-specific data lives in {@link JobSourceReference}. */
@Entity
@Table(name = "job_offers")
public class JobOffer extends BaseEntity {

    @Column(nullable = false, length = 300) private String title;
    @Column(name = "normalized_title", nullable = false, length = 300) private String normalizedTitle;
    @Column(name = "company_name", length = 200) private String companyName;
    @Column(name = "normalized_company", length = 200) private String normalizedCompany;
    @Column(name = "description_html", columnDefinition = "mediumtext") private String descriptionHtml;
    @Column(name = "description_text", columnDefinition = "mediumtext") private String descriptionText;
    @Column(name = "location_text", length = 500) private String locationText;
    @Column(length = 2) private String country;
    @Column(length = 16) private String region;
    @Column(name = "work_modality", nullable = false, length = 16) private String workModality = "UNKNOWN";
    @Column(name = "remote_scope", nullable = false, length = 24) private String remoteScope = "UNKNOWN";
    @Convert(converter = StringListConverter.class)
    @Column(name = "remote_countries", columnDefinition = "json") private List<String> remoteCountries = new ArrayList<>();
    @Column(name = "bolivia_eligibility", nullable = false, length = 16) private String boliviaEligibility = "UNKNOWN";
    @Column(name = "eligibility_evidence", length = 1000) private String eligibilityEvidence;
    @Column(name = "engagement_type", nullable = false, length = 24) private String engagementType = "UNKNOWN";
    @Column(name = "contract_type", nullable = false, length = 16) private String contractType = "UNKNOWN";
    @Column(length = 16) private String seniority;
    @Column(name = "salary_min", precision = 14, scale = 2) private BigDecimal salaryMin;
    @Column(name = "salary_max", precision = 14, scale = 2) private BigDecimal salaryMax;
    @Column(name = "salary_currency", length = 3) private String salaryCurrency;
    @Column(name = "salary_period", length = 8) private String salaryPeriod;
    @Column(name = "salary_min_usd_month", precision = 12, scale = 2) private BigDecimal salaryMinUsdMonth;
    @Column(name = "salary_is_estimated", nullable = false) private boolean salaryIsEstimated;
    @Column(name = "salary_text", length = 200) private String salaryText;
    @Column(name = "required_languages", columnDefinition = "json") private String requiredLanguages;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_is_approx", nullable = false) private boolean publishedIsApprox;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "canonical_url", length = 1000) private String canonicalUrl;
    @Column(name = "canonical_url_hash", columnDefinition = "binary(32)") private byte[] canonicalUrlHash;
    @Column(name = "apply_url", length = 1000) private String applyUrl;
    @Column(name = "content_hash", nullable = false, columnDefinition = "binary(32)") private byte[] contentHash;
    @Column(nullable = false, columnDefinition = "binary(32)") private byte[] fingerprint;
    @Column private Long simhash;
    @Column(nullable = false, length = 16) private String status = "ACTIVE";
    @Column(name = "duplicate_of_id") private Long duplicateOfId;
    @Column(name = "first_seen_at", nullable = false) private Instant firstSeenAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "last_verified_at") private Instant lastVerifiedAt;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "job_offer_skills", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Column(name = "skill_id")
    private Set<Long> skillIds = new LinkedHashSet<>();

    protected JobOffer() {
    }

    public JobOffer(Instant now) {
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    /** Applies normalized content. Returns true when the content hash changed (material change). */
    public boolean apply(NormalizedJob n) {
        boolean changed = contentHash == null || !java.util.Arrays.equals(contentHash, n.contentHash());
        title = n.title();
        normalizedTitle = n.normalizedTitle();
        companyName = n.companyName();
        normalizedCompany = n.normalizedCompany();
        if (n.descriptionHtml() != null || descriptionHtml == null) descriptionHtml = n.descriptionHtml();
        if (n.descriptionText() != null && (descriptionText == null || n.descriptionText().length() >= descriptionText.length() * 0.8)) {
            descriptionText = n.descriptionText();
        }
        locationText = n.locationText();
        JobClassification c = n.classification();
        country = c.country();
        region = c.region();
        workModality = c.workModality();
        remoteScope = c.remoteScope();
        remoteCountries = new ArrayList<>(c.remoteCountries());
        boliviaEligibility = c.boliviaEligibility();
        eligibilityEvidence = c.evidence();
        engagementType = c.engagementType();
        contractType = c.contractType();
        seniority = c.seniority();
        requiredLanguages = com.jobhunter.shared.json.Json.write(c.requiredLanguages());
        if (n.salaryMin() != null || salaryMin == null) {
            salaryMin = n.salaryMin();
            salaryMax = n.salaryMax();
            salaryCurrency = n.salaryCurrency();
            salaryPeriod = n.salaryPeriod();
            salaryIsEstimated = n.salaryEstimated();
            salaryMinUsdMonth = n.salaryMinUsdMonth();
        }
        if (n.salaryText() != null) salaryText = n.salaryText();
        if (n.publishedAt() != null && (publishedAt == null || publishedIsApprox)) {
            publishedAt = n.publishedAt();
            publishedIsApprox = n.publishedApprox();
        }
        if (n.expiresAt() != null) expiresAt = n.expiresAt();
        if (n.canonicalUrl() != null && canonicalUrl == null) {
            canonicalUrl = n.canonicalUrl();
            canonicalUrlHash = JobText.sha256(n.canonicalUrl());
        }
        if (n.applyUrl() != null) applyUrl = n.applyUrl();
        contentHash = n.contentHash();
        fingerprint = n.fingerprint();
        simhash = n.simhash();
        skillIds.clear();
        skillIds.addAll(n.skillIds());
        if (!"ACTIVE".equals(status) && !"DUPLICATE".equals(status)) status = "ACTIVE";
        return changed;
    }

    /**
     * Applies AI analysis results conservatively: only fills fields the deterministic classifier left UNKNOWN/empty.
     * Eligibility is upgraded only with a verbatim evidence quote (checked by the caller).
     */
    public void applyAiAnalysis(String eligibility, String evidence, String seniority, String contractType,
                                String engagementType, String requiredLanguagesJson) {
        if ("UNKNOWN".equals(boliviaEligibility) && eligibility != null && !"UNKNOWN".equals(eligibility) && evidence != null) {
            boliviaEligibility = eligibility;
            eligibilityEvidence = evidence.length() > 1000 ? evidence.substring(0, 1000) : evidence;
        }
        if (this.seniority == null && seniority != null && !"UNKNOWN".equals(seniority)) this.seniority = seniority;
        if ("UNKNOWN".equals(this.contractType) && contractType != null) this.contractType = contractType;
        if ("UNKNOWN".equals(this.engagementType) && engagementType != null) this.engagementType = engagementType;
        if ((requiredLanguages == null || "[]".equals(requiredLanguages)) && requiredLanguagesJson != null) {
            requiredLanguages = requiredLanguagesJson;
        }
    }

    public void seen(Instant now) { lastSeenAt = now; }
    public void verified(Instant now) { lastVerifiedAt = now; }
    public void expire() { status = "EXPIRED"; }
    public void remove() { status = "REMOVED"; }
    public void setSalaryMinUsdMonth(BigDecimal v) { salaryMinUsdMonth = v; }

    public String getTitle() { return title; }
    public String getNormalizedTitle() { return normalizedTitle; }
    public String getCompanyName() { return companyName; }
    public String getNormalizedCompany() { return normalizedCompany; }
    public String getDescriptionHtml() { return descriptionHtml; }
    public String getDescriptionText() { return descriptionText; }
    public String getLocationText() { return locationText; }
    public String getCountry() { return country; }
    public String getRegion() { return region; }
    public String getWorkModality() { return workModality; }
    public String getRemoteScope() { return remoteScope; }
    public List<String> getRemoteCountries() { return remoteCountries; }
    public String getBoliviaEligibility() { return boliviaEligibility; }
    public String getEligibilityEvidence() { return eligibilityEvidence; }
    public String getEngagementType() { return engagementType; }
    public String getContractType() { return contractType; }
    public String getSeniority() { return seniority; }
    public BigDecimal getSalaryMin() { return salaryMin; }
    public BigDecimal getSalaryMax() { return salaryMax; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public String getSalaryPeriod() { return salaryPeriod; }
    public BigDecimal getSalaryMinUsdMonth() { return salaryMinUsdMonth; }
    public boolean isSalaryIsEstimated() { return salaryIsEstimated; }
    public String getSalaryText() { return salaryText; }
    public String getRequiredLanguages() { return requiredLanguages; }
    public Instant getPublishedAt() { return publishedAt; }
    public boolean isPublishedIsApprox() { return publishedIsApprox; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getApplyUrl() { return applyUrl; }
    public byte[] getContentHash() { return contentHash; }
    public byte[] getFingerprint() { return fingerprint; }
    public Long getSimhash() { return simhash; }
    public String getStatus() { return status; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
    public Set<Long> getSkillIds() { return skillIds; }
}
