package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.domain.Geo;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.shared.http.SafeHttpClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Remotive public API (https://github.com/remotive-com/remote-jobs-api). Keyless, small feed, 24 h delay.
 * Guidance: at most ~4 calls/day, >2/minute is blocked → the orchestrator fetches the whole feed at most every 6 h
 * (job_sources.min_interval_minutes) and filters locally. Terms: link back + credit Remotive.
 */
@Component
public class RemotiveConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public RemotiveConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "remotive"; }
    @Override public IntegrationCode integration() { return IntegrationCode.REMOTIVE; }
    @Override public IntegrationCode code() { return IntegrationCode.REMOTIVE; }

    @Override
    public Capabilities capabilities() {
        // the whole feed is fetched (rate rules), filtering happens locally
        return new Capabilities(false, false, false, false, true, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        var response = http.get(policy("remotive", 0.03, Duration.ofSeconds(30)), uri("remotive", "/api/remote-jobs", Map.of()), null);
        JsonNode jobs = parse(response, "Remotive").get("jobs");
        List<JobOfferDraft> items = new ArrayList<>();
        if (jobs != null && jobs.isArray()) {
            for (JsonNode j : jobs) items.add(map(j));
        }
        return FetchResult.of(items, 1);
    }

    static JobOfferDraft map(JsonNode j) {
        String location = text(j, "candidate_required_location");
        return JobOfferDraft.builder("remotive", text(j, "id"))
                .sourceUrl(text(j, "url")).applyUrl(text(j, "url"))
                .title(text(j, "title")).company(text(j, "company_name"))
                .descriptionHtml(text(j, "description"))
                .location(location)
                .allowedLocations(countryList(location))
                .remote(true)
                .employmentType(text(j, "job_type"))
                .salaryText(text(j, "salary"))
                .publishedAt(instant(text(j, "publication_date")), false)
                .attribution("Vía Remotive")
                .rawPayload(j.toString())
                .build();
    }

    /** "USA, Canada" → [USA, Canada]; mixed text like "Americas, LATAM, USA timezones" → [] (handled as text). */
    static List<String> countryList(String location) {
        if (location == null || location.isBlank()) return List.of();
        List<String> parts = Arrays.stream(location.split("[,;/]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        boolean allCountries = parts.stream().allMatch(p -> Geo.iso(p).isPresent());
        return allCountries ? parts : List.of();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("remotive", 0.03, Duration.ofSeconds(15)), uri("remotive", "/api/remote-jobs/categories", Map.of()), null);
        return r.ok() ? ProbeResult.ok("Remotive responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
