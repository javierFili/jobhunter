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
 * Remote OK public JSON (https://remoteok.com/api). The first element is a legal notice. Terms: followable link
 * back + name Remote OK as the source; no logo. Whole feed fetched at most every 6 h, filtered locally.
 */
@Component
public class RemoteOkConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public RemoteOkConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "remoteok"; }
    @Override public IntegrationCode integration() { return IntegrationCode.REMOTEOK; }
    @Override public IntegrationCode code() { return IntegrationCode.REMOTEOK; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, false, false, true, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        // Remote OK filters by tag (?tag=java); without keywords it returns the whole recent feed.
        String tag = q.keywords() == null || q.keywords().isBlank() ? null
                : q.keywords().trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        var response = http.get(policy("remoteok", 0.05, Duration.ofSeconds(30)),
                uri("remoteok", "/api", tag == null ? Map.of() : Map.of("tag", tag)), null);
        JsonNode root = parse(response, "Remote OK");
        List<JobOfferDraft> items = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode j : root) {
                if (j.has("legal") || text(j, "id") == null) continue;
                items.add(map(j));
            }
        }
        return FetchResult.of(items, 1);
    }

    static JobOfferDraft map(JsonNode j) {
        var min = decimal(j, "salary_min");
        var max = decimal(j, "salary_max");
        String salaryText = min == null ? null : "USD " + min.toPlainString() + (max != null ? " - " + max.toPlainString() : "");
        return JobOfferDraft.builder("remoteok", text(j, "id"))
                .sourceUrl(text(j, "url")).applyUrl(text(j, "apply_url"))
                .title(text(j, "position")).company(text(j, "company"))
                .descriptionHtml(text(j, "description"))
                .location(text(j, "location"))
                .remote(true)
                .salaryText(salaryText)
                .publishedAt(instant(text(j, "date")), false)
                .attribution("Vía Remote OK")
                .rawPayload(j.toString())
                .build();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("remoteok", 0.05, Duration.ofSeconds(15)), uri("remoteok", "/api", Map.of()), null);
        return r.ok() ? ProbeResult.ok("Remote OK responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
