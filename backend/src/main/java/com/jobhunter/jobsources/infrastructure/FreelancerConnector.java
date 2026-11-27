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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Freelancer.com public Projects API (https://developers.freelancer.com). Read-only search of ACTIVE projects works
 * without a token. These are freelance PROJECTS, not employment: fixed budgets are kept as text (they are not a
 * monthly salary); hourly rates are stored per hour. Open worldwide — bidding happens on Freelancer.com.
 */
@Component
public class FreelancerConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public FreelancerConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "freelancer"; }
    @Override public IntegrationCode integration() { return IntegrationCode.FREELANCER; }
    @Override public IntegrationCode code() { return IntegrationCode.FREELANCER; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, false, false, true, false, false);
    }

    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        var response = http.get(policy("freelancer", 1, Duration.ofSeconds(30)), uri("freelancer", "/api/projects/0.1/projects/active/",
                params("query", q.keywords() == null ? "" : q.keywords().trim(), "limit", Math.min(100, Math.max(1, q.maxResults())),
                        "full_description", "true", "job_details", "true", "compact", "true")), null);
        JsonNode projects = parse(response, "Freelancer.com").path("result").path("projects");
        List<JobOfferDraft> items = new ArrayList<>();
        if (projects.isArray()) {
            for (JsonNode p : projects) {
                if (items.size() >= q.maxResults()) break;
                items.add(map(p, props.baseUrl("freelancer")));
            }
        }
        return FetchResult.of(items, 1);
    }

    static JobOfferDraft map(JsonNode p, String base) {
        String seo = text(p, "seo_url");
        String url = (base.contains("freelancer.com") ? "https://www.freelancer.com" : base) + "/projects/" + (seo != null ? seo : text(p, "id"));
        String currency = path(p, "currency.code");
        BigDecimal min = decimal(p.path("budget"), "minimum");
        BigDecimal max = decimal(p.path("budget"), "maximum");
        boolean hourly = "hourly".equalsIgnoreCase(text(p, "type"));
        String budget = min == null ? null : (hourly ? "Tarifa por hora " : "Presupuesto fijo ") + (currency == null ? "" : currency + " ")
                + min.stripTrailingZeros().toPlainString() + (max != null ? " – " + max.stripTrailingZeros().toPlainString() : "")
                + (hourly ? " / hora" : " (proyecto completo)");
        List<String> skills = new ArrayList<>();
        p.path("jobs").forEach(j -> { String n = text(j, "name"); if (n != null) skills.add(n); });
        String desc = text(p, "description");
        String html = "<p>" + esc(desc == null ? (text(p, "preview_description") == null ? "" : text(p, "preview_description")) : desc)
                .replace("\n", "<br>") + "</p>"
                + (skills.isEmpty() ? "" : "<p>Habilidades: " + esc(String.join(", ", skills)) + "</p>")
                + (budget == null ? "" : "<p>" + esc(budget) + "</p>")
                + "<p>Proyecto freelance en Freelancer.com: se postula enviando una propuesta (bid) en la plataforma.</p>";
        JobOfferDraft.Builder b = JobOfferDraft.builder("freelancer", text(p, "id"))
                .sourceUrl(url).applyUrl(url)
                .title(text(p, "title"))
                .company("Cliente de Freelancer.com")
                .descriptionHtml(html)
                .location("Worldwide")
                .allowedLocations(List.of("Worldwide"))
                .remote(true)
                .employmentType("freelance")
                // a fixed budget is the price of the whole project, never a monthly salary: keep it out of salary fields
                .salaryText(hourly ? budget : null)
                .publishedAt(epoch(p, "time_submitted", false), false)
                .attribution("Vía Freelancer.com")
                .rawPayload(p.toString());
        if (hourly && min != null) b.salary(min, max, currency, "HOUR", false);
        return b.build();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("freelancer", 1, Duration.ofSeconds(15)),
                uri("freelancer", "/api/projects/0.1/projects/active/", params("query", "java", "limit", 1, "compact", "true")), null);
        return r.ok() ? ProbeResult.ok("Freelancer.com responde correctamente", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status());
    }
}
