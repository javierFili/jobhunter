package com.jobhunter.integrations.api;

import com.jobhunter.integrations.application.UsageService;
import com.jobhunter.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Usage and cost per integration (AI tokens, API requests, Apify spend) for a period. */
@RestController
public class UsageController {

    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    public record UsageRow(String code, long requests, BigDecimal costUsd, long inputTokens, long outputTokens, long cachedTokens) {
    }

    @GetMapping("/api/v1/analytics/usage")
    public List<UsageRow> usage(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        CurrentUser.id();
        LocalDate f = from != null ? from : LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate t = to != null ? to.plusDays(1) : LocalDate.now(ZoneOffset.UTC).plusDays(1);
        Instant fi = f.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant ti = t.atStartOfDay().toInstant(ZoneOffset.UTC);
        return usage.aggregate(fi, ti).stream().map(a -> new UsageRow(a.getCode().name(), a.getRequests(), a.getCost(),
                a.getInputTokens(), a.getOutputTokens(), a.getCachedTokens())).toList();
    }
}
