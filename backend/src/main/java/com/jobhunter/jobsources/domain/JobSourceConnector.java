package com.jobhunter.jobsources.domain;

import com.jobhunter.integrations.domain.IntegrationCode;

import java.util.Map;

/**
 * One external job source. Implementations map the source's response to {@link com.jobhunter.jobs.domain.JobOfferDraft}s
 * and declare which filters they apply server-side (the rest are applied locally after ingestion).
 */
public interface JobSourceConnector {

    /** {@code job_sources.code}. */
    String sourceCode();

    IntegrationCode integration();

    Capabilities capabilities();

    FetchResult fetch(SourceQuery query, Map<String, String> credentials);

    record Capabilities(boolean keyword, boolean country, boolean salary, boolean postedSince, boolean remoteOnly,
                        boolean perCompany, boolean completeListing) {
    }
}
