package com.jobhunter.dashboard.api;

import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.profiles.application.ProfileService;
import com.jobhunter.searches.application.SearchService;
import com.jobhunter.searches.domain.SavedSearch;
import com.jobhunter.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ProfileService profileService;
    private final JobQueryService jobs;
    private final SearchService searches;

    public DashboardController(ProfileService profileService, JobQueryService jobs, SearchService searches) {
        this.profileService = profileService;
        this.jobs = jobs;
        this.searches = searches;
    }

    public record SearchStats(int count, Instant lastRunAt) {
    }

    public record Summary(int profileCompleteness, List<String> missingSections, Map<String, Long> counts,
                          JobQueryService.CatalogStats jobs, SearchStats searches) {
    }

    @GetMapping("/summary")
    public Summary summary() {
        long userId = CurrentUser.id();
        var completeness = profileService.completeness(userId);
        List<SavedSearch> saved = searches.list(userId);
        Instant lastRun = saved.stream().map(SavedSearch::getLastRunAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new Summary(completeness.profileCompleteness(), completeness.missingSections(), completeness.counts(),
                jobs.stats(userId), new SearchStats(saved.size(), lastRun));
    }
}
