package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.shared.http.SafeHttpClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * We Work Remotely public category RSS feeds (back-end + full-stack programming). Whole-feed source: keywords are
 * applied by the catalogue query, the feed is re-downloaded at most every {@code min_interval_minutes}.
 * Terms: link back to the original posting and credit We Work Remotely; apply on their site.
 */
@Component
public class WeWorkRemotelyConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    static final List<String> FEEDS = List.of(
            "/categories/remote-back-end-programming-jobs.rss",
            "/categories/remote-full-stack-programming-jobs.rss");

    public WeWorkRemotelyConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "weworkremotely"; }
    @Override public IntegrationCode integration() { return IntegrationCode.WEWORKREMOTELY; }
    @Override public IntegrationCode code() { return IntegrationCode.WEWORKREMOTELY; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(false, false, false, false, true, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        List<JobOfferDraft> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String feed : FEEDS) {
            var response = http.get(policy("weworkremotely", 0.5, Duration.ofSeconds(30)), uri("weworkremotely", feed, Map.of()), null);
            for (RssSupport.Item it : RssSupport.parse(response, "We Work Remotely")) {
                JobOfferDraft d = map(it);
                if (d != null && seen.add(d.externalId())) items.add(d);
            }
        }
        return FetchResult.of(items, FEEDS.size());
    }

    static JobOfferDraft map(RssSupport.Item it) {
        String link = it.get("link");
        String id = it.get("guid") != null ? it.get("guid") : link;
        if (id == null) return null;
        // titles are "Company: Job title"
        String raw = it.get("title") == null ? "" : it.get("title");
        int colon = raw.indexOf(':');
        String company = colon > 0 ? raw.substring(0, colon).trim() : null;
        String title = colon > 0 ? raw.substring(colon + 1).trim() : raw.trim();
        title = title.replaceAll("[\\s\\-–—]+$", "");
        String region = it.get("region");
        List<String> allowed = region == null ? List.of()
                : Arrays.stream(region.split("[,;/]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String location = region != null ? region : it.get("country");
        return JobOfferDraft.builder("weworkremotely", id)
                .sourceUrl(link).applyUrl(link)
                .title(title).company(company)
                .descriptionHtml(it.get("description"))
                .location(location)
                .allowedLocations(allowed)
                .remote(true)
                .employmentType(it.get("type"))
                .publishedAt(RssSupport.rfc1123(it.get("pubDate")), false)
                .expiresAt(HttpConnectorSupport.instant(it.get("expires_at")))
                .attribution("Vía We Work Remotely")
                .rawPayload(com.jobhunter.shared.json.Json.write(it.fields()))
                .build();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("weworkremotely", 0.5, Duration.ofSeconds(15)), uri("weworkremotely", FEEDS.get(0), Map.of()), null);
        return r.ok() ? ProbeResult.ok("We Work Remotely responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
