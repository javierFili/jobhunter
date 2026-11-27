package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.shared.http.SafeHttpClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Himalayas public remote-jobs API (https://himalayas.app/docs/remote-jobs-api). No key needed (optional free key
 * raises limits). Structured {@code locationRestrictions} and {@code timezoneRestrictions} drive eligibility.
 * Terms: visible credit + link; apply via {@code applicationLink}.
 */
@Component
public class HimalayasConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    private static final int PAGE_SIZE = 20;

    public HimalayasConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "himalayas"; }
    @Override public IntegrationCode integration() { return IntegrationCode.HIMALAYAS; }
    @Override public IntegrationCode code() { return IntegrationCode.HIMALAYAS; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, false, false, true, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        List<JobOfferDraft> items = new ArrayList<>();
        int requests = 0;
        int maxPages = Math.max(1, Math.min(10, (int) Math.ceil(q.maxResults() / (double) PAGE_SIZE)));
        for (int page = 1; page <= maxPages && items.size() < q.maxResults(); page++) {
            var response = http.get(policy("himalayas", 1, Duration.ofSeconds(20)),
                    uri("himalayas", "/jobs/api/search", params("q", q.keywords(), "page", page)),
                    h -> {
                        String key = credentials.get("apiKey");
                        if (key != null) h.setBearerAuth(key);
                    });
            requests++;
            JsonNode jobs = parse(response, "Himalayas").get("jobs");
            if (jobs == null || !jobs.isArray() || jobs.isEmpty()) break;
            for (JsonNode j : jobs) {
                if (items.size() >= q.maxResults()) break;
                items.add(map(j));
            }
            if (jobs.size() < PAGE_SIZE) break;
        }
        return FetchResult.of(items, requests);
    }

    static JobOfferDraft map(JsonNode j) {
        String guid = text(j, "guid");
        String apply = text(j, "applicationLink");
        List<Integer> offsets = new ArrayList<>();
        JsonNode tz = j.get("timezoneRestrictions");
        if (tz != null && tz.isArray()) tz.forEach(t -> { if (t.isNumber()) offsets.add((int) Math.round(t.asDouble())); });
        String period = switch (String.valueOf(text(j, "salaryPeriod")).toLowerCase()) {
            case "annual", "yearly", "year" -> "YEAR";
            case "monthly", "month" -> "MONTH";
            case "hourly", "hour" -> "HOUR";
            case "daily", "day" -> "DAY";
            default -> null;
        };
        return JobOfferDraft.builder("himalayas", guid != null ? guid : apply)
                .sourceUrl(guid != null ? guid : apply).applyUrl(apply)
                .title(text(j, "title")).company(text(j, "companyName"))
                .descriptionHtml(text(j, "description"))
                .allowedLocations(strings(j, "locationRestrictions"))
                .timezoneOffsets(offsets)
                .remote(true)
                .employmentType(text(j, "employmentType"))
                .seniority(String.join(" ", strings(j, "seniority")))
                .salary(decimal(j, "minSalary"), decimal(j, "maxSalary"), text(j, "currency"), period, false)
                .publishedAt(epoch(j, "pubDate", false), false)
                .expiresAt(epoch(j, "expiryDate", false))
                .attribution("Vía Himalayas")
                .rawPayload(j.toString())
                .build();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("himalayas", 1, Duration.ofSeconds(15)), uri("himalayas", "/jobs/api", params("limit", 1)), null);
        return r.ok() ? ProbeResult.ok("Himalayas responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
