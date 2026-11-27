package com.jobhunter.analytics.api;

import com.jobhunter.analytics.application.AnalyticsService;
import com.jobhunter.analytics.application.SkillRecommendationService;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.security.CurrentUser;
import com.jobhunter.shared.tasks.AsyncTasks;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final SkillRecommendationService recommendations;
    private final AsyncTasks tasks;

    public AnalyticsController(AnalyticsService analytics, SkillRecommendationService recommendations, AsyncTasks tasks) {
        this.analytics = analytics;
        this.recommendations = recommendations;
        this.tasks = tasks;
    }

    @GetMapping("/overview")
    public AnalyticsService.Overview overview(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return analytics.overview(CurrentUser.id(), AnalyticsService.Period.of(from, to));
    }

    @GetMapping("/jobs/breakdown")
    public List<AnalyticsService.Bucket> breakdown(@RequestParam String dimension, @RequestParam(required = false) LocalDate from,
                                                   @RequestParam(required = false) LocalDate to,
                                                   @RequestParam(defaultValue = "15") int limit) {
        CurrentUser.id();
        try {
            return analytics.breakdown(dimension, AnalyticsService.Period.of(from, to), limit);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Dimensión no válida");
        }
    }

    @GetMapping("/jobs/timeline")
    public List<AnalyticsService.DayPoint> timeline(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        CurrentUser.id();
        return analytics.timeline(AnalyticsService.Period.of(from, to));
    }

    @GetMapping("/applications/funnel")
    public List<AnalyticsService.Stage> funnel(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return analytics.funnel(CurrentUser.id(), AnalyticsService.Period.of(from, to));
    }

    @GetMapping("/skills/demand")
    public List<AnalyticsService.SkillDemand> demand(@RequestParam(required = false) String region,
                                                     @RequestParam(defaultValue = "90") int days,
                                                     @RequestParam(defaultValue = "20") int limit) {
        return analytics.skillDemand(CurrentUser.id(), region, days, limit);
    }

    @PostMapping("/skills/recommendations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> recommend(@RequestParam(required = false) String region, @RequestParam(defaultValue = "90") int days) {
        long userId = CurrentUser.id();
        String key = "skill-rec:" + userId;
        tasks.submit(key, () -> recommendations.generate(userId, region, days));
        return state(key);
    }

    @GetMapping("/skills/recommendations")
    public Map<String, Object> latestRecommendations(@RequestParam(required = false) String region,
                                                     @RequestParam(defaultValue = "90") int days) {
        long userId = CurrentUser.id();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("task", state("skill-rec:" + userId));
        out.put("result", recommendations.latest(userId, region, days).orElse(null));
        return out;
    }

    @GetMapping("/usage/daily")
    public List<AnalyticsService.UsageDay> usageDaily(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        CurrentUser.id();
        return analytics.usageDaily(AnalyticsService.Period.of(from, to));
    }

    private Map<String, Object> state(String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        var s = tasks.status(key);
        m.put("state", s.map(x -> x.state().name()).orElse("NONE"));
        m.put("code", s.map(AsyncTasks.Status::code).orElse(null));
        m.put("message", s.map(AsyncTasks.Status::message).orElse(null));
        return m;
    }
}
