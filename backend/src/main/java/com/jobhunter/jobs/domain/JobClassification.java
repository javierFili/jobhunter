package com.jobhunter.jobs.domain;

import java.util.List;

/** Deterministic classification result. {@code evidence} quotes the text/field that produced the decision. */
public record JobClassification(
        String workModality,
        String remoteScope,
        List<String> remoteCountries,
        String boliviaEligibility,
        String evidence,
        String engagementType,
        String contractType,
        String seniority,
        String country,
        String region,
        List<RequiredLanguage> requiredLanguages) {

    public record RequiredLanguage(String code, String level, String evidence) {
    }
}
