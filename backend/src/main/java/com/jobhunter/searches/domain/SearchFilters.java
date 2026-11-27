package com.jobhunter.searches.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Typed search filters (validated before being stored as JSON — never a raw passthrough). */
public record SearchFilters(
        @Size(max = 30) List<Long> skillIds,
        @Size(max = 60) List<@Pattern(regexp = "^[A-Z]{2}$") String> countries,
        @Size(max = 7) List<@Pattern(regexp = "^(LATAM|EUROPE|NORTH_AMERICA|ASIA|AFRICA|OCEANIA|WORLDWIDE)$") String> regions,
        @Size(max = 4) List<@Pattern(regexp = "^(REMOTE|HYBRID|ONSITE|UNKNOWN)$") String> modalities,
        @Size(max = 7) List<@Pattern(regexp = "^(WORLDWIDE|LATAM|AMERICAS|EUROPE|COUNTRY_RESTRICTED|TIMEZONE_RESTRICTED|UNKNOWN)$") String> remoteScopes,
        @Size(max = 4) List<@Pattern(regexp = "^(ELIGIBLE|LIKELY|NOT_ELIGIBLE|UNKNOWN)$") String> eligibility,
        @Size(max = 7) List<@Pattern(regexp = "^(FULL_TIME|PART_TIME|CONTRACT|TEMPORARY|FREELANCE|INTERNSHIP|UNKNOWN)$") String> contractTypes,
        @Size(max = 6) List<@Pattern(regexp = "^(EMPLOYEE_LOCAL|EMPLOYEE_EOR|CONTRACTOR|FREELANCE_PROJECT|SPONSORED_RELOCATION|UNKNOWN)$") String> engagementTypes,
        @Size(max = 3) List<@Pattern(regexp = "^(JUNIOR|MID|SENIOR)$") String> seniority,
        BigDecimal salaryMinUsdMonth,
        @Size(max = 10) List<@Pattern(regexp = "^[a-z]{2}$") String> languages,
        @Min(1) @Max(365) Integer postedWithinDays,
        @Size(max = 20) List<@Size(max = 200) String> companies,
        @Size(max = 50) List<@Size(max = 200) String> excludeCompanies) {

    public static SearchFilters empty() {
        return new SearchFilters(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, List.of(), null, List.of(), List.of());
    }

    /** Replaces null lists with empty lists. */
    public SearchFilters normalized() {
        return new SearchFilters(nn(skillIds), nn(countries), nn(regions), nn(modalities), nn(remoteScopes), nn(eligibility),
                nn(contractTypes), nn(engagementTypes), nn(seniority), salaryMinUsdMonth, nn(languages),
                postedWithinDays, nn(companies), nn(excludeCompanies));
    }

    private static <T> List<T> nn(List<T> l) {
        return l == null ? List.of() : l;
    }
}
