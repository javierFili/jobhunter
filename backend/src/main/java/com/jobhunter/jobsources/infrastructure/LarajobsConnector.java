package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.shared.http.SafeHttpClient;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LaraJobs public RSS feed (Laravel / PHP jobs). The feed carries no description: the posting is summarised from its
 * structured fields (tags, location, type, salary) so keyword search and skill tagging still work; the full text
 * lives on LaraJobs. Whole-feed source. Terms: link back to LaraJobs and credit it.
 */
@Component
public class LarajobsConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public LarajobsConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "larajobs"; }
    @Override public IntegrationCode integration() { return IntegrationCode.LARAJOBS; }
    @Override public IntegrationCode code() { return IntegrationCode.LARAJOBS; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(false, false, false, false, false, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        var response = http.get(policy("larajobs", 0.5, Duration.ofSeconds(30)), uri("larajobs", "/feed", Map.of()), null);
        List<JobOfferDraft> items = new ArrayList<>();
        for (RssSupport.Item it : RssSupport.parse(response, "LaraJobs")) {
            JobOfferDraft d = map(it);
            if (d != null) items.add(d);
        }
        return FetchResult.of(items, 1);
    }

    static JobOfferDraft map(RssSupport.Item it) {
        String link = it.get("link");
        String id = it.get("guid") != null ? it.get("guid") : link;
        if (id == null) return null;
        String company = unescape(it.get("company") != null ? it.get("company") : it.get("creator"));
        String location = unescape(it.get("location"));
        String type = it.get("job_type");
        String salary = unescape(it.get("salary"));
        List<String> tags = it.get("tags") == null ? List.of()
                : Arrays.stream(it.get("tags").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        boolean remote = location != null && location.toLowerCase(Locale.ROOT).contains("remote");
        StringBuilder html = new StringBuilder("<p>Oferta publicada en LaraJobs (bolsa de empleo de Laravel/PHP).</p>");
        if (!tags.isEmpty()) html.append("<p>Tecnologías: ").append(esc(String.join(", ", tags))).append("</p>");
        if (location != null) html.append("<p>Ubicación: ").append(esc(location)).append("</p>");
        if (type != null) html.append("<p>Tipo: ").append(esc(type)).append("</p>");
        if (salary != null) html.append("<p>Salario: ").append(esc(salary)).append("</p>");
        html.append("<p>La descripción completa está en LaraJobs.</p>");
        return JobOfferDraft.builder("larajobs", id)
                .sourceUrl(link).applyUrl(link)
                .title(unescape(it.get("title"))).company(company)
                .descriptionHtml(html.toString())
                .location(location)
                .remote(remote ? Boolean.TRUE : null)
                .workplaceType(location != null && location.toLowerCase(Locale.ROOT).contains("hybrid") ? "hybrid" : null)
                .employmentType(type == null ? null : type.replace('_', ' '))
                .salaryText(salary)
                .publishedAt(RssSupport.rfc1123(it.get("pubDate")), false)
                .attribution("Vía LaraJobs")
                .rawPayload(com.jobhunter.shared.json.Json.write(it.fields()))
                .build();
    }

    private static String unescape(String s) {
        return s == null || s.isBlank() ? null : Parser.unescapeEntities(s, false).trim();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("larajobs", 0.5, Duration.ofSeconds(15)), uri("larajobs", "/feed", Map.of()), null);
        return r.ok() ? ProbeResult.ok("LaraJobs responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
