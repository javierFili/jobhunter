package com.jobhunter.jobs.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** A draft after sanitizing, normalization, classification and tagging — ready to merge into a {@link JobOffer}. */
public record NormalizedJob(
        String title, String normalizedTitle, String companyName, String normalizedCompany,
        String descriptionHtml, String descriptionText, String locationText,
        JobClassification classification,
        BigDecimal salaryMin, BigDecimal salaryMax, String salaryCurrency, String salaryPeriod,
        boolean salaryEstimated, String salaryText, BigDecimal salaryMinUsdMonth,
        Instant publishedAt, boolean publishedApprox, Instant expiresAt,
        String canonicalUrl, String applyUrl,
        byte[] contentHash, byte[] fingerprint, long simhash, Set<Long> skillIds) {
}
