package com.jobhunter.jobs.application;

import com.jobhunter.jobs.domain.Geo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Catalogue filters (shared by the Discover page and saved searches). Null/empty = no restriction. */
public record JobQuery(
        String q,
        List<Long> skillIds,
        List<String> countries,
        List<String> regions,
        List<String> modalities,
        List<String> remoteScopes,
        List<String> eligibility,
        List<String> contractTypes,
        List<String> engagementTypes,
        List<String> seniority,
        BigDecimal salaryMinUsdMonth,
        List<String> languages,
        Integer postedWithinDays,
        List<String> companies,
        List<String> excludeCompanies,
        List<String> sources,
        Boolean saved,
        boolean includeHidden,
        String status,
        String sort) {

    public static JobQuery all() {
        return new JobQuery(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, true, "ACTIVE", "recent");
    }

    static boolean has(List<?> l) {
        return l != null && !l.isEmpty();
    }

    /** Regions whose remote-scope implies the requested countries are allowed. */
    Set<String> scopesCoveringCountries() {
        Set<String> scopes = new java.util.HashSet<>();
        scopes.add("WORLDWIDE");
        for (String c : countries) {
            if (Geo.LATAM.contains(c)) {
                scopes.add("LATAM");
                scopes.add("AMERICAS");
            }
            if (Geo.NORTH_AMERICA.contains(c)) scopes.add("AMERICAS");
            if (Geo.EUROPE.contains(c)) scopes.add("EUROPE");
        }
        return scopes;
    }
}
