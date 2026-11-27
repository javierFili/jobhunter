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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Jobicy remote jobs API (https://jobicy.com/jobs-rss-feed). Keyless; last 7 days only, 3 h delay.
 * Terms: credit Jobicy with a direct link; apply buttons must go to the original job URL; mark closed jobs.
 */
@Component
public class JobicyConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public JobicyConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "jobicy"; }
    @Override public IntegrationCode integration() { return IntegrationCode.JOBICY; }
    @Override public IntegrationCode code() { return IntegrationCode.JOBICY; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, false, false, true, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        String tag = q.keywords() == null ? null : q.keywords().trim().split("\\s+")[0];
        var response = http.get(policy("jobicy", 1, Duration.ofSeconds(20)),
                uri("jobicy", "/api/v2/remote-jobs", params("count", Math.min(100, Math.max(1, q.maxResults())), "tag", tag)), null);
        JsonNode jobs = parse(response, "Jobicy").get("jobs");
        List<JobOfferDraft> items = new ArrayList<>();
        if (jobs != null && jobs.isArray()) {
            for (JsonNode j : jobs) {
                if (items.size() >= q.maxResults()) break;
                items.add(map(j));
            }
        }
        return FetchResult.of(items, 1);
    }

    static JobOfferDraft map(JsonNode j) {
        String geo = text(j, "jobGeo");
        List<String> allowed = geo == null ? List.of()
                : Arrays.stream(geo.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<String> types = strings(j, "jobType");
        return JobOfferDraft.builder("jobicy", text(j, "id"))
                .sourceUrl(text(j, "url")).applyUrl(text(j, "url"))
                .title(text(j, "jobTitle")).company(text(j, "companyName"))
                .descriptionHtml(text(j, "jobDescription"))
                .location(geo)
                .allowedLocations(allowed)
                .remote(true)
                .employmentType(types.isEmpty() ? null : String.join(" ", types))
                .seniority("Any".equalsIgnoreCase(text(j, "jobLevel")) ? null : text(j, "jobLevel"))
                .salary(decimal(j, "annualSalaryMin"), decimal(j, "annualSalaryMax"), text(j, "salaryCurrency"),
                        decimal(j, "annualSalaryMin") != null ? "YEAR" : null, false)
                .publishedAt(instant(text(j, "pubDate")), false)
                .attribution("Vía Jobicy")
                .rawPayload(j.toString())
                .build();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("jobicy", 1, Duration.ofSeconds(15)), uri("jobicy", "/api/v2/remote-jobs", params("count", 1)), null);
        return r.ok() ? ProbeResult.ok("Jobicy responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
