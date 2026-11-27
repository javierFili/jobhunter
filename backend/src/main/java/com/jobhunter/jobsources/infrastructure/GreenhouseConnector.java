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
 * Greenhouse Job Board API (https://docs.greenhouse.io/job-board.html): per-company board token, no search params,
 * returns the complete board → removed jobs can be detected. GET only; application submission is never used.
 * {@code content} is HTML-entity-encoded (decoded by the sanitizer).
 */
@Component
public class GreenhouseConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public GreenhouseConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "greenhouse"; }
    @Override public IntegrationCode integration() { return IntegrationCode.GREENHOUSE; }
    @Override public IntegrationCode code() { return IntegrationCode.GREENHOUSE; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(false, false, false, false, false, true, true);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        var response = http.get(policy("greenhouse", 1, Duration.ofSeconds(30)),
                uri("greenhouse", "/v1/boards/" + enc(q.boardToken()) + "/jobs", params("content", "true")), null);
        JsonNode jobs = parse(response, "Greenhouse").get("jobs");
        List<JobOfferDraft> items = new ArrayList<>();
        if (jobs != null && jobs.isArray()) {
            for (JsonNode j : jobs) items.add(map(j, q.boardToken(), q.companyName()));
        }
        return FetchResult.complete(items, 1, q.boardToken() + ":");
    }

    static JobOfferDraft map(JsonNode j, String token, String companyName) {
        String company = text(j, "company_name") != null ? text(j, "company_name") : companyName;
        String location = path(j, "location.name");
        String published = text(j, "first_published") != null ? text(j, "first_published") : text(j, "updated_at");
        return JobOfferDraft.builder("greenhouse", token + ":" + text(j, "id"))
                .sourceUrl(text(j, "absolute_url")).applyUrl(text(j, "absolute_url"))
                .title(text(j, "title")).company(company != null ? company : token)
                .descriptionHtml(text(j, "content"))
                .location(location)
                .publishedAt(instant(published), false)
                .expiresAt(instant(text(j, "application_deadline")))
                .attribution("Vía " + (company != null ? company : token) + " (Greenhouse)")
                .rawPayload(j.toString())
                .build();
    }

    /** Validation of a board token: returns the number of open jobs. */
    public int countJobs(String token) {
        var response = http.get(policy("greenhouse", 1, Duration.ofSeconds(20)),
                uri("greenhouse", "/v1/boards/" + enc(token) + "/jobs", Map.of()), null);
        if (response.status() == 404) return -1;
        JsonNode jobs = parse(response, "Greenhouse").get("jobs");
        return jobs == null ? 0 : jobs.size();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("greenhouse", 1, Duration.ofSeconds(15)), uri("greenhouse", "/v1/boards/gitlab/jobs", Map.of()), null);
        return r.ok() ? ProbeResult.ok("La API de Greenhouse responde (board de ejemplo: gitlab)", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
