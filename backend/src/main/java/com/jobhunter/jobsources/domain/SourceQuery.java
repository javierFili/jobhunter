package com.jobhunter.jobsources.domain;

import java.util.List;

/**
 * Query sent to a connector. ATS connectors use {@code boardToken}/{@code companyName} (per-company listings);
 * search connectors use keywords/countries. {@code maxResults} is a hard cap (cost control).
 */
public record SourceQuery(String keywords, List<String> countries, boolean remoteOnly, Integer postedWithinDays,
                          int maxResults, String boardToken, String companyName) {

    public static SourceQuery board(String token, String companyName, int maxResults) {
        return new SourceQuery(null, List.of(), false, null, maxResults, token, companyName);
    }
}
