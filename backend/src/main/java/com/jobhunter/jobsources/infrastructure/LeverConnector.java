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

/** Lever Postings API (https://github.com/lever/postings-api): per-company site, paginated with skip/limit. */
@Component
public class LeverConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    private static final int PAGE = 100;

    public LeverConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "lever"; }
    @Override public IntegrationCode integration() { return IntegrationCode.LEVER; }
    @Override public IntegrationCode code() { return IntegrationCode.LEVER; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(false, false, false, false, false, true, true);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        List<JobOfferDraft> items = new ArrayList<>();
        int requests = 0;
        for (int skip = 0; skip < 2000; skip += PAGE) {
            var response = http.get(policy("lever", 1, Duration.ofSeconds(30)),
                    uri("lever", "/v0/postings/" + enc(q.boardToken()), params("mode", "json", "skip", skip, "limit", PAGE)), null);
            requests++;
            JsonNode root = parse(response, "Lever");
            if (!root.isArray() || root.isEmpty()) break;
            for (JsonNode j : root) items.add(map(j, q.boardToken(), q.companyName()));
            if (root.size() < PAGE) break;
        }
        return FetchResult.complete(items, requests, q.boardToken() + ":");
    }

    static JobOfferDraft map(JsonNode j, String site, String companyName) {
        StringBuilder html = new StringBuilder();
        if (text(j, "description") != null) html.append(text(j, "description"));
        JsonNode lists = j.get("lists");
        if (lists != null && lists.isArray()) {
            for (JsonNode l : lists) {
                html.append("<h3>").append(org.jsoup.nodes.Entities.escape(String.valueOf(text(l, "text")))).append("</h3><ul>")
                        .append(String.valueOf(text(l, "content"))).append("</ul>");
            }
        }
        if (text(j, "additional") != null) html.append(text(j, "additional"));
        JsonNode salary = j.get("salaryRange");
        String interval = salary == null ? null : text(salary, "interval");
        String period = interval == null ? null : interval.contains("year") ? "YEAR" : interval.contains("month") ? "MONTH"
                : interval.contains("hour") ? "HOUR" : interval.contains("day") ? "DAY" : null;
        String company = companyName != null ? companyName : site;
        return JobOfferDraft.builder("lever", site + ":" + text(j, "id"))
                .sourceUrl(text(j, "hostedUrl")).applyUrl(text(j, "applyUrl"))
                .title(text(j, "text")).company(company)
                .descriptionHtml(html.isEmpty() ? null : html.toString())
                .location(path(j, "categories.location"))
                .allowedLocations(List.of())
                .workplaceType(text(j, "workplaceType"))
                .employmentType(path(j, "categories.commitment"))
                .countryHint(text(j, "country") != null && text(j, "country").length() == 2 ? text(j, "country").toUpperCase() : null)
                .salary(salary == null ? null : decimal(salary, "min"), salary == null ? null : decimal(salary, "max"),
                        salary == null ? null : text(salary, "currency"), period, false)
                .publishedAt(epoch(j, "createdAt", true), false)
                .attribution("Vía " + company + " (Lever)")
                .rawPayload(j.toString())
                .build();
    }

    public int countJobs(String site) {
        var response = http.get(policy("lever", 1, Duration.ofSeconds(20)),
                uri("lever", "/v0/postings/" + enc(site), params("mode", "json", "limit", PAGE)), null);
        if (response.status() == 404) return -1;
        JsonNode root = parse(response, "Lever");
        return root.isArray() ? root.size() : 0;
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("lever", 1, Duration.ofSeconds(15)), uri("lever", "/v0/postings/leverdemo", params("mode", "json", "limit", 1)), null);
        return r.ok() ? ProbeResult.ok("La API de Lever responde (site de ejemplo: leverdemo)", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
