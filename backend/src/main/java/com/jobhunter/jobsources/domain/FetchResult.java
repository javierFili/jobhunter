package com.jobhunter.jobsources.domain;

import com.jobhunter.jobs.domain.JobOfferDraft;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param completeListing true when {@code items} is the source's FULL current listing (ATS boards) so missing items
 *                        can be marked removed; {@code listingPrefix} scopes that check (e.g. "gitlab:")
 */
public record FetchResult(List<JobOfferDraft> items, int requests, BigDecimal costUsd, boolean costIsEstimate,
                          boolean completeListing, String listingPrefix, String externalRunId,
                          String externalDatasetId, List<String> warnings) {

    public static FetchResult of(List<JobOfferDraft> items, int requests) {
        return new FetchResult(items, requests, BigDecimal.ZERO, false, false, null, null, null, List.of());
    }

    public static FetchResult complete(List<JobOfferDraft> items, int requests, String prefix) {
        return new FetchResult(items, requests, BigDecimal.ZERO, false, true, prefix, null, null, List.of());
    }
}
