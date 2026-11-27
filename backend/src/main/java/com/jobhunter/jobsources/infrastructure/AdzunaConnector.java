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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adzuna search API (https://developer.adzuna.com/docs/search). No Bolivia, no remote filter, description snippet
 * only. Salary period is not documented in the response → amounts are kept but not converted to USD/month.
 * Terms: "Adzuna" label with link on every ad; predicted salaries flagged; commercial use needs a licence.
 */
@Component
public class AdzunaConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    public static final Set<String> COUNTRIES = Set.of("gb", "us", "at", "au", "be", "br", "ca", "ch", "de", "es", "fr",
            "in", "it", "mx", "nl", "nz", "pl", "sg", "za");
    private static final Map<String, String> CURRENCY = Map.ofEntries(Map.entry("gb", "GBP"), Map.entry("us", "USD"),
            Map.entry("at", "EUR"), Map.entry("au", "AUD"), Map.entry("be", "EUR"), Map.entry("br", "BRL"),
            Map.entry("ca", "CAD"), Map.entry("ch", "CHF"), Map.entry("de", "EUR"), Map.entry("es", "EUR"),
            Map.entry("fr", "EUR"), Map.entry("in", "INR"), Map.entry("it", "EUR"), Map.entry("mx", "MXN"),
            Map.entry("nl", "EUR"), Map.entry("nz", "NZD"), Map.entry("pl", "PLN"), Map.entry("sg", "SGD"),
            Map.entry("za", "ZAR"));

    public AdzunaConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "adzuna"; }
    @Override public IntegrationCode integration() { return IntegrationCode.ADZUNA; }
    @Override public IntegrationCode code() { return IntegrationCode.ADZUNA; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true, true, false, false, false);
    }

    /** {@code q.countries()} must already be Adzuna-supported (lower-case); the orchestrator applies the default. */
    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        List<JobOfferDraft> items = new ArrayList<>();
        int requests = 0;
        String what = (q.keywords() == null ? "" : q.keywords()) + (q.remoteOnly() ? " remote" : "");
        for (String country : q.countries().subList(0, Math.min(2, q.countries().size()))) {
            String c = country.toLowerCase(Locale.ROOT);
            if (!COUNTRIES.contains(c)) continue;
            var response = http.get(policy("adzuna", 1, Duration.ofSeconds(30)), uri("adzuna", "/v1/api/jobs/" + c + "/search/1",
                    params("app_id", credentials.get("appId"), "app_key", credentials.get("appKey"),
                            "what", what.trim(), "results_per_page", Math.min(50, Math.max(1, q.maxResults())),
                            "max_days_old", q.postedWithinDays(), "content-type", "application/json")), null);
            requests++;
            JsonNode results = parse(response, "Adzuna").get("results");
            if (results == null || !results.isArray()) continue;
            for (JsonNode j : results) {
                if (items.size() >= q.maxResults()) break;
                items.add(map(j, c));
            }
        }
        return FetchResult.of(items, requests);
    }

    static JobOfferDraft map(JsonNode j, String country) {
        String predicted = text(j, "salary_is_predicted");
        boolean estimated = "1".equals(predicted) || "true".equalsIgnoreCase(predicted);
        String contractTime = text(j, "contract_time");
        String contractType = text(j, "contract_type");
        return JobOfferDraft.builder("adzuna", country + ":" + text(j, "id"))
                .sourceUrl(text(j, "redirect_url")).applyUrl(text(j, "redirect_url"))
                .title(text(j, "title")).company(path(j, "company.display_name"))
                .descriptionText(text(j, "description"))
                .location(path(j, "location.display_name"))
                .countryHint(country.toUpperCase(Locale.ROOT).equals("GB") ? "GB" : country.toUpperCase(Locale.ROOT))
                .employmentType((contractTime == null ? "" : contractTime) + " " + (contractType == null ? "" : contractType))
                .salary(decimal(j, "salary_min"), decimal(j, "salary_max"), CURRENCY.get(country), null, estimated)
                .publishedAt(instant(text(j, "created")), false)
                .attribution("Jobs by Adzuna")
                .rawPayload(j.toString())
                .build();
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("adzuna", 1, Duration.ofSeconds(20)), uri("adzuna", "/v1/api/jobs/us/search/1",
                params("app_id", credentials.get("appId"), "app_key", credentials.get("appKey"),
                        "results_per_page", 1, "content-type", "application/json")), null);
        return r.ok() ? ProbeResult.ok("Credenciales de Adzuna válidas", Map.of("latencyMs", r.latencyMs()))
                : ProbeResult.fail("HTTP " + r.status() + " (¿app_id/app_key correctos?)");
    }
}
