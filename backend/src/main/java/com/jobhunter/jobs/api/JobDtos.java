package com.jobhunter.jobs.api;

import com.jobhunter.jobs.domain.JobClassification;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobs.domain.JobSourceReference;
import com.jobhunter.jobs.domain.UserJobState;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.shared.json.Json;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JobDtos {

    private JobDtos() {
    }

    public record SkillRef(Long id, String name) {
    }

    public record SourceRef(String code, String displayName, String attributionText, String sourceUrl) {
    }

    public record MatchRef(int scoreTotal, String classification) {
    }

    public record JobSummaryDto(Long id, String title, String companyName, String locationText, String country,
                                String region, String workModality, String remoteScope, String boliviaEligibility,
                                String engagementType, String contractType, String seniority, BigDecimal salaryMin,
                                BigDecimal salaryMax, String salaryCurrency, String salaryPeriod,
                                boolean salaryIsEstimated, String salaryText, BigDecimal salaryMinUsdMonth,
                                Instant publishedAt, boolean publishedIsApprox, Instant firstSeenAt, String status,
                                List<SkillRef> skills, List<SourceRef> sources, boolean saved, boolean hidden,
                                MatchRef match) {
    }

    public record ReferenceDto(String sourceCode, String sourceName, String sourceUrl, String applyUrl,
                               String attributionText, Instant firstSeenAt, Instant lastSeenAt, Instant removedAt) {
    }

    public record JobDetailDto(JobSummaryDto summary, String descriptionHtml, String descriptionText,
                               String eligibilityEvidence, List<String> remoteCountries,
                               List<JobClassification.RequiredLanguage> requiredLanguages, String applyUrl,
                               String canonicalUrl, Instant expiresAt, Instant lastSeenAt, Instant lastVerifiedAt,
                               List<ReferenceDto> references) {
    }

    private static final TypeReference<List<JobClassification.RequiredLanguage>> LANGS = new TypeReference<>() {
    };

    public static JobSummaryDto summary(JobOffer o, Map<Long, Skill> skills, List<JobSourceReference> refs,
                                        Map<Long, JobSource> sources, UserJobState state, MatchRef match) {
        List<SourceRef> srcs = refs == null ? List.of() : refs.stream().map(r -> {
            JobSource s = sources.get(r.getJobSourceId());
            return s == null ? null : new SourceRef(s.getCode(), s.getDisplayName(),
                    r.getAttributionText() != null ? r.getAttributionText() : s.getAttributionText(), r.getSourceUrl());
        }).filter(Objects::nonNull).distinct().toList();
        return new JobSummaryDto(o.getId(), o.getTitle(), o.getCompanyName(), o.getLocationText(), o.getCountry(),
                o.getRegion(), o.getWorkModality(), o.getRemoteScope(), o.getBoliviaEligibility(), o.getEngagementType(),
                o.getContractType(), o.getSeniority(), o.getSalaryMin(), o.getSalaryMax(), o.getSalaryCurrency(),
                o.getSalaryPeriod(), o.isSalaryIsEstimated(), o.getSalaryText(), o.getSalaryMinUsdMonth(),
                o.getPublishedAt(), o.isPublishedIsApprox(), o.getFirstSeenAt(), o.getStatus(),
                o.getSkillIds().stream().map(skills::get).filter(Objects::nonNull).map(s -> new SkillRef(s.getId(), s.getName())).toList(),
                srcs, state != null && state.isSaved(), state != null && state.isHidden(), match);
    }

    public static JobDetailDto detail(JobOffer o, Map<Long, Skill> skills, List<JobSourceReference> refs,
                                      Map<Long, JobSource> sources, UserJobState state, MatchRef match) {
        List<JobClassification.RequiredLanguage> langs = Json.read(o.getRequiredLanguages(), LANGS);
        String applyUrl = o.getApplyUrl() != null ? o.getApplyUrl()
                : refs.stream().map(JobSourceReference::getApplyUrl).filter(Objects::nonNull).findFirst()
                .orElse(refs.isEmpty() ? null : refs.get(0).getSourceUrl());
        return new JobDetailDto(summary(o, skills, refs, sources, state, match), o.getDescriptionHtml(),
                o.getDescriptionText(), o.getEligibilityEvidence(), o.getRemoteCountries(),
                langs == null ? List.of() : langs, applyUrl, o.getCanonicalUrl(), o.getExpiresAt(), o.getLastSeenAt(),
                o.getLastVerifiedAt(),
                refs.stream().map(r -> {
                    JobSource s = sources.get(r.getJobSourceId());
                    return new ReferenceDto(s == null ? null : s.getCode(), s == null ? null : s.getDisplayName(),
                            r.getSourceUrl(), r.getApplyUrl(), r.getAttributionText(), r.getFirstSeenAt(),
                            r.getLastSeenAt(), r.getRemovedAt());
                }).toList());
    }
}
