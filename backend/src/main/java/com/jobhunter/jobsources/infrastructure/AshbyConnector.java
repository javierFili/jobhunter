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

/** Ashby public job posting API (https://developers.ashbyhq.com/docs/public-job-posting-api). Unlisted jobs are skipped. */
@Component
public class AshbyConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public AshbyConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "ashby"; }
    @Override public IntegrationCode integration() { return IntegrationCode.ASHBY; }
    @Override public IntegrationCode code() { return IntegrationCode.ASHBY; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(false, false, true, false, false, true, true);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        var response = http.get(policy("ashby", 1, Duration.ofSeconds(40)),
                uri("ashby", "/posting-api/job-board/" + enc(q.boardToken()), params("includeCompensation", "true")), null);
        JsonNode jobs = parse(response, "Ashby").get("jobs");
        List<JobOfferDraft> items = new ArrayList<>();
        if (jobs != null && jobs.isArray()) {
            for (JsonNode j : jobs) {
                if (j.has("isListed") && !j.get("isListed").asBoolean(true)) continue;
                items.add(map(j, q.boardToken(), q.companyName()));
            }
        }
        return FetchResult.complete(items, 1, q.boardToken() + ":");
    }

    static JobOfferDraft map(JsonNode j, String board, String companyName) {
        List<String> locations = new ArrayList<>();
        JsonNode secondary = j.get("secondaryLocations");
        if (secondary != null && secondary.isArray()) {
            secondary.forEach(s -> { String l = text(s, "location"); if (l != null) locations.add(l); });
        }
        String location = text(j, "location");
        // salary component (if published)
        java.math.BigDecimal min = null, max = null;
        String currency = null, period = null;
        JsonNode comps = j.path("compensation").path("summaryComponents");
        if (comps.isArray()) {
            for (JsonNode c : comps) {
                if ("Salary".equalsIgnoreCase(text(c, "compensationType"))) {
                    min = decimal(c, "minValue");
                    max = decimal(c, "maxValue");
                    currency = text(c, "currencyCode");
                    String interval = String.valueOf(text(c, "interval")).toUpperCase();
                    period = interval.contains("YEAR") ? "YEAR" : interval.contains("MONTH") ? "MONTH"
                            : interval.contains("HOUR") ? "HOUR" : interval.contains("DAY") ? "DAY" : null;
                    break;
                }
            }
        }
        String company = companyName != null ? companyName : board;
        return JobOfferDraft.builder("ashby", board + ":" + text(j, "id"))
                .sourceUrl(text(j, "jobUrl")).applyUrl(text(j, "applyUrl"))
                .title(text(j, "title")).company(company)
                .descriptionHtml(text(j, "descriptionHtml")).descriptionText(text(j, "descriptionPlain"))
                .location(location + (locations.isEmpty() ? "" : " · " + String.join(", ", locations)))
                .remote(j.has("isRemote") ? j.get("isRemote").asBoolean() : null)
                .workplaceType(text(j, "workplaceType"))
                .employmentType(text(j, "employmentType"))
                .salary(min, max, currency, period, false)
                .salaryText(path(j, "compensation.scrapeableCompensationSalarySummary"))
                .publishedAt(instant(text(j, "publishedAt")), false)
                .attribution("Vía " + company + " (Ashby)")
                .rawPayload(j.toString().length() > 60_000 ? null : j.toString())
                .build();
    }

    public int countJobs(String board) {
        var response = http.get(policy("ashby", 1, Duration.ofSeconds(40)),
                uri("ashby", "/posting-api/job-board/" + enc(board), Map.of()), null);
        if (response.status() == 404) return -1;
        JsonNode jobs = parse(response, "Ashby").get("jobs");
        return jobs == null ? 0 : jobs.size();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("ashby", 1, Duration.ofSeconds(40)), uri("ashby", "/posting-api/job-board/ashby", Map.of()), null);
        return r.ok() ? ProbeResult.ok("La API de Ashby responde (board de ejemplo: ashby)", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
