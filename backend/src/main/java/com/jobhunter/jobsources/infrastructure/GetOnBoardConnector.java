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
 * Get on Board public API (https://www.getonbrd.com/api-doc), the main LATAM tech job board. Keyless, keyword search,
 * salaries published as gross USD per month. Remote modality: fully_remote / remote_local (remote inside the listed
 * countries) / hybrid / no_remote. Terms: link to the posting on Get on Board and credit it; apply there.
 */
@Component
public class GetOnBoardConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    private static final Map<String, String> SENIORITY = Map.of("1", "junior", "2", "junior", "3", "mid", "4", "senior", "5", "senior");
    private static final Map<String, String> MODALITY = Map.of("1", "full time", "2", "part time", "3", "freelance", "4", "internship");

    public GetOnBoardConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "getonboard"; }
    @Override public IntegrationCode integration() { return IntegrationCode.GETONBOARD; }
    @Override public IntegrationCode code() { return IntegrationCode.GETONBOARD; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, false, false, false, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        int perPage = Math.min(100, Math.max(1, q.maxResults()));
        var response = http.get(policy("getonboard", 1, Duration.ofSeconds(30)), uri("getonboard", "/api/v0/search/jobs",
                params("query", q.keywords() == null ? "" : q.keywords().trim(), "per_page", perPage, "page", 1,
                        "expand", "[\"company\"]")), null);
        JsonNode data = parse(response, "Get on Board").get("data");
        List<JobOfferDraft> items = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode j : data) {
                if (items.size() >= q.maxResults()) break;
                items.add(map(j));
            }
        }
        return FetchResult.of(items, 1);
    }

    static JobOfferDraft map(JsonNode j) {
        JsonNode a = j.path("attributes");
        String url = path(j, "links.public_url");
        String modality = text(a, "remote_modality");
        List<String> countries = strings(a, "countries").stream().filter(c -> !"Remote".equalsIgnoreCase(c)).toList();
        boolean remote = "fully_remote".equals(modality) || "remote_local".equals(modality) || "temporarily_remote".equals(modality);
        String workplace = "hybrid".equals(modality) ? "hybrid" : "no_remote".equals(modality) ? "onsite" : remote ? "remote" : null;
        String location = countries.isEmpty() ? (remote ? "Remote" : null) : String.join(", ", countries);
        StringBuilder html = new StringBuilder();
        for (String field : List.of("description", "functions", "desirable", "benefits")) {
            String v = text(a, field);
            if (v != null) html.append(v).append('\n');
        }
        var min = decimal(a, "min_salary");
        var max = decimal(a, "max_salary");
        return JobOfferDraft.builder("getonboard", text(j, "id"))
                .sourceUrl(url).applyUrl(url)
                .title(text(a, "title"))
                .company(path(a, "company.data.attributes.name"))
                .descriptionHtml(html.toString())
                .location(location)
                // remote_local = remote, but only from the listed countries
                .allowedLocations("remote_local".equals(modality) ? countries : List.of())
                .remote(remote)
                .workplaceType(workplace)
                .employmentType(MODALITY.get(path(a, "modality.data.id")))
                .seniority(SENIORITY.get(path(a, "seniority.data.id")))
                .salary(min, max, min == null && max == null ? null : "USD", min == null && max == null ? null : "MONTH", false)
                .publishedAt(epoch(a, "published_at", false), false)
                .attribution("Vía Get on Board")
                .rawPayload(j.toString())
                .build();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("getonboard", 1, Duration.ofSeconds(15)),
                uri("getonboard", "/api/v0/search/jobs", params("query", "developer", "per_page", 1)), null);
        return r.ok() ? ProbeResult.ok("Get on Board responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
