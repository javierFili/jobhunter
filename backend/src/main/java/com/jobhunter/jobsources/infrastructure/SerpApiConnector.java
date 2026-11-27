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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SerpApi Google Jobs (https://serpapi.com/google-jobs-api). Quota-based (free plan 250 searches/month): every page
 * is one search. Never sends {@code no_cache}, so identical queries within 1 h are free. The API key travels as a
 * query parameter → it is never logged. Pagination uses {@code next_page_token} ({@code start} is discontinued).
 */
@Component
public class SerpApiConnector extends HttpConnectorSupport implements JobSourceConnector, IntegrationProbe {

    private static final Pattern RELATIVE = Pattern.compile("(\\d+)\\+?\\s*(minute|hour|day|week|month)s?\\s+ago", Pattern.CASE_INSENSITIVE);

    public SerpApiConnector(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public String sourceCode() { return "serpapi_google_jobs"; }
    @Override public IntegrationCode integration() { return IntegrationCode.SERPAPI; }
    @Override public IntegrationCode code() { return IntegrationCode.SERPAPI; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, false, false, true, false, false);
    }

    /** Pages per country (from integration config "maxPagesPerSearch", default 2) are decided by the caller via maxResults. */
    @Override
    public FetchResult fetch(SourceQuery q, Map<String, String> credentials) {
        String key = credentials.get("apiKey");
        List<String> countries = q.countries().isEmpty() ? java.util.Collections.singletonList((String) null)
                : q.countries().subList(0, Math.min(2, q.countries().size()));
        String query = (q.keywords() == null ? "software developer" : q.keywords()) + (q.remoteOnly() ? " remote" : "");
        List<JobOfferDraft> items = new ArrayList<>();
        int requests = 0;
        int maxPages = Math.max(1, Math.min(5, (int) Math.ceil(q.maxResults() / 10.0)));
        for (String country : countries) {
            String token = null;
            for (int page = 0; page < maxPages && items.size() < q.maxResults(); page++) {
                var response = http.get(policy("serpapi", 1, Duration.ofSeconds(40)), uri("serpapi", "/search.json",
                        params("engine", "google_jobs", "q", query, "hl", "en",
                                "gl", country == null ? null : country.toLowerCase(Locale.ROOT),
                                "next_page_token", token, "api_key", key)), null);
                requests++;
                JsonNode root = parse(response, "SerpApi");
                if (root.has("error")) {
                    String error = text(root, "error");
                    if (error != null && error.toLowerCase().contains("hasn't returned any results")) break;
                    throw new SafeHttpClient.HttpCallException("SerpApi: " + error, response.status(), null);
                }
                JsonNode jobs = root.get("jobs_results");
                if (jobs == null || !jobs.isArray()) break;
                for (JsonNode j : jobs) {
                    if (items.size() >= q.maxResults()) break;
                    items.add(map(j, country, Instant.now()));
                }
                token = path(root, "serpapi_pagination.next_page_token");
                if (token == null) break;
            }
        }
        // cost is quota-based (plan), not per call: recorded as 0 USD with request counts
        return new FetchResult(items, requests, BigDecimal.ZERO, true, false, null, null, null, List.of());
    }

    static JobOfferDraft map(JsonNode j, String country, Instant now) {
        String applyLink = path(j, "apply_options.0.link");
        String share = text(j, "share_link");
        String via = text(j, "via");
        String posted = path(j, "detected_extensions.posted_at");
        JsonNode ext = j.get("detected_extensions");
        Boolean remote = ext != null && ext.has("work_from_home") ? ext.get("work_from_home").asBoolean() : null;
        String description = text(j, "description");
        return JobOfferDraft.builder("serpapi_google_jobs", text(j, "job_id"))
                .sourceUrl(applyLink != null ? applyLink : share).applyUrl(applyLink)
                .title(text(j, "title")).company(text(j, "company_name"))
                .descriptionText(description)
                .location(text(j, "location"))
                .remote(remote)
                .employmentType(path(j, "detected_extensions.schedule_type"))
                .salaryText(path(j, "detected_extensions.salary"))
                .publishedAt(relative(posted, now), true)
                .countryHint(null)
                .attribution(via != null ? "Google Jobs · " + via : "Google Jobs")
                .rawPayload(j.toString())
                .build();
    }

    /** "3 days ago" → approximate instant (marked approximate). */
    static Instant relative(String text, Instant now) {
        if (text == null) return null;
        Matcher m = RELATIVE.matcher(text);
        if (!m.find()) return null;
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "minute" -> now.minus(n, ChronoUnit.MINUTES);
            case "hour" -> now.minus(n, ChronoUnit.HOURS);
            case "day" -> now.minus(n, ChronoUnit.DAYS);
            case "week" -> now.minus(n * 7, ChronoUnit.DAYS);
            case "month" -> now.minus(n * 30, ChronoUnit.DAYS);
            default -> null;
        };
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var r = http.get(policy("serpapi", 1, Duration.ofSeconds(20)),
                uri("serpapi", "/account.json", params("api_key", credentials.get("apiKey"))), null);
        if (!r.ok()) return ProbeResult.fail("HTTP " + r.status() + " (¿API key correcta?)");
        JsonNode acc = parse(r, "SerpApi");
        return ProbeResult.ok("Cuenta SerpApi válida", Map.of(
                "plan", String.valueOf(text(acc, "plan_name")),
                "searchesLeft", String.valueOf(text(acc, "total_searches_left")),
                "thisMonthUsage", String.valueOf(text(acc, "this_month_usage"))));
    }
}
