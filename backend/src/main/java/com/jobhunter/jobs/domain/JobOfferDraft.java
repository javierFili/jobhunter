package com.jobhunter.jobs.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a connector returns: the source's data mapped to common fields, BEFORE classification/deduplication.
 * {@code allowedLocations} = structured location restrictions from the source (names as given by the source).
 */
public record JobOfferDraft(
        String sourceCode,
        String externalId,
        String sourceUrl,
        String applyUrl,
        String title,
        String companyName,
        String descriptionHtml,
        String descriptionText,
        String locationText,
        List<String> allowedLocations,
        List<Integer> timezoneOffsets,
        Boolean remote,
        String workplaceType,
        String employmentType,
        String seniority,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        String salaryPeriod,
        boolean salaryEstimated,
        String salaryText,
        Instant publishedAt,
        boolean publishedApprox,
        Instant expiresAt,
        String countryHint,
        String attribution,
        String rawPayload) {

    public static Builder builder(String sourceCode, String externalId) {
        return new Builder(sourceCode, externalId);
    }

    public static final class Builder {
        private final String sourceCode;
        private final String externalId;
        private String sourceUrl, applyUrl, title, companyName, descriptionHtml, descriptionText, locationText;
        private List<String> allowedLocations = List.of();
        private List<Integer> timezoneOffsets = List.of();
        private Boolean remote;
        private String workplaceType, employmentType, seniority, salaryCurrency, salaryPeriod, salaryText;
        private BigDecimal salaryMin, salaryMax;
        private boolean salaryEstimated, publishedApprox;
        private Instant publishedAt, expiresAt;
        private String countryHint, attribution, rawPayload;

        private Builder(String sourceCode, String externalId) {
            this.sourceCode = sourceCode;
            this.externalId = externalId;
        }

        public Builder sourceUrl(String v) { sourceUrl = v; return this; }
        public Builder applyUrl(String v) { applyUrl = v; return this; }
        public Builder title(String v) { title = v == null ? null : v.replaceAll("\\s+", " ").strip(); return this; }
        public Builder company(String v) { companyName = v == null ? null : v.replaceAll("\\s+", " ").strip(); return this; }
        public Builder descriptionHtml(String v) { descriptionHtml = v; return this; }
        public Builder descriptionText(String v) { descriptionText = v; return this; }
        public Builder location(String v) { locationText = v; return this; }
        public Builder allowedLocations(List<String> v) { allowedLocations = v == null ? List.of() : v; return this; }
        public Builder timezoneOffsets(List<Integer> v) { timezoneOffsets = v == null ? List.of() : v; return this; }
        public Builder remote(Boolean v) { remote = v; return this; }
        public Builder workplaceType(String v) { workplaceType = v; return this; }
        public Builder employmentType(String v) { employmentType = v; return this; }
        public Builder seniority(String v) { seniority = v; return this; }
        public Builder salary(BigDecimal min, BigDecimal max, String currency, String period, boolean estimated) {
            salaryMin = min; salaryMax = max; salaryCurrency = currency; salaryPeriod = period; salaryEstimated = estimated;
            return this;
        }
        public Builder salaryText(String v) { salaryText = v; return this; }
        public Builder publishedAt(Instant v, boolean approx) { publishedAt = v; publishedApprox = approx; return this; }
        public Builder expiresAt(Instant v) { expiresAt = v; return this; }
        public Builder countryHint(String v) { countryHint = v; return this; }
        public Builder attribution(String v) { attribution = v; return this; }
        public Builder rawPayload(String v) { rawPayload = v; return this; }

        public JobOfferDraft build() {
            return new JobOfferDraft(sourceCode, externalId, sourceUrl, applyUrl, title, companyName, descriptionHtml,
                    descriptionText, locationText, allowedLocations, timezoneOffsets, remote, workplaceType,
                    employmentType, seniority, salaryMin, salaryMax, salaryCurrency, salaryPeriod, salaryEstimated,
                    salaryText, publishedAt, publishedApprox, expiresAt, countryHint, attribution, rawPayload);
        }
    }
}
