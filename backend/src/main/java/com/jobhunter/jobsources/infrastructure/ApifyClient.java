package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobsources.domain.ApifyActor;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.shared.http.SafeHttpClient;
import com.jobhunter.shared.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Apify REST API v2 (https://docs.apify.com/api/v2). Asynchronous run → poll status (waitForFinish ≤ 60 s) →
 * read dataset items. Every run carries {@code maxItems} and {@code maxTotalChargeUsd} (per-run cost cap); runs
 * exceeding the deadline are aborted. Token sent as Bearer header (never as query param).
 */
@Component
public class ApifyClient extends HttpConnectorSupport implements IntegrationProbe {

    private static final Logger log = LoggerFactory.getLogger(ApifyClient.class);
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "TIMED-OUT", "ABORTED");

    public ApifyClient(SafeHttpClient http, ConnectorProperties props) {
        super(http, props);
    }

    @Override public IntegrationCode code() { return IntegrationCode.APIFY; }

    private SafeHttpClient.Policy policy() {
        return policy("apify", 5, Duration.ofSeconds(75));
    }

    public record Limits(BigDecimal monthlyUsageUsd, BigDecimal maxMonthlyUsageUsd) {
    }

    public Limits limits(String token) {
        var r = http.get(policy(), uri("apify", "/v2/users/me/limits", Map.of()), h -> h.setBearerAuth(token));
        JsonNode data = parse(r, "Apify").path("data");
        return new Limits(decimalOrZero(data.path("current"), "monthlyUsageUsd"), decimal(data.path("limits"), "maxMonthlyUsageUsd"));
    }

    public FetchResult run(ApifyActor actor, SourceQuery q, String token) {
        int maxItems = Math.min(actor.getMaxItems(), q.maxResults());
        String input = renderInput(actor.getInputTemplate(), q, maxItems);
        String actorPath = enc(actor.getActorId()).replace("%7E", "~");
        var start = http.postJson(policy(), uri("apify", "/v2/actors/" + actorPath + "/runs", params(
                "timeout", actor.getTimeoutSecs(), "maxItems", maxItems, "maxTotalChargeUsd", actor.getMaxChargeUsd().toPlainString(),
                "memory", actor.getMemoryMb())), h -> h.setBearerAuth(token), input);
        JsonNode run = parse(start, "Apify").path("data");
        String runId = text(run, "id");
        int requests = 1;
        Instant deadline = Instant.now().plusSeconds(actor.getTimeoutSecs() + 60L);
        String status = text(run, "status");
        while (!TERMINAL.contains(status)) {
            if (Instant.now().isAfter(deadline)) {
                http.postJson(policy(), uri("apify", "/v2/actor-runs/" + runId + "/abort", Map.of()), h -> h.setBearerAuth(token), "{}");
                requests++;
                return new FetchResult(List.of(), requests, decimalOrZero(run, "usageTotalUsd"), true, false, null, runId,
                        text(run, "defaultDatasetId"), List.of("Ejecución abortada por exceder el tiempo máximo"));
            }
            var poll = http.get(policy(), uri("apify", "/v2/actor-runs/" + runId, params("waitForFinish", 60)), h -> h.setBearerAuth(token));
            requests++;
            run = parse(poll, "Apify").path("data");
            status = text(run, "status");
        }
        String datasetId = text(run, "defaultDatasetId");
        List<JobOfferDraft> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!"SUCCEEDED".equals(status)) warnings.add("Estado final del run: " + status);
        if (datasetId != null) {
            var data = http.get(policy(), uri("apify", "/v2/datasets/" + datasetId + "/items",
                    params("clean", "true", "format", "json", "limit", maxItems)), h -> h.setBearerAuth(token));
            requests++;
            JsonNode arr = parse(data, "Apify");
            JsonNode mapping = Json.tree(actor.getFieldMapping());
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    JobOfferDraft d = map(item, mapping, actor);
                    if (d.title() != null && d.sourceUrl() != null) items.add(d);
                }
            }
        }
        // cost reported right after finishing can be preliminary (docs) → flagged as estimate
        return new FetchResult(items, requests, decimalOrZero(run, "usageTotalUsd"), true, false, null, runId, datasetId, warnings);
    }

    /** Replaces "{{keywords}}", "{{location}}", "{{country}}" in string values; "{{maxItems}}" becomes a number. */
    static String renderInput(String template, SourceQuery q, int maxItems) {
        JsonNode root = Json.tree(template == null || template.isBlank() ? "{}" : template);
        if (!(root instanceof ObjectNode obj)) throw new IllegalArgumentException("inputTemplate must be a JSON object");
        String location = q.countries().isEmpty() ? "" : q.countries().get(0);
        for (String name : new ArrayList<>(obj.propertyNames())) {
            JsonNode v = obj.get(name);
            if (v.isString()) {
                String s = v.asString();
                if (s.equals("{{maxItems}}")) obj.put(name, maxItems);
                else obj.put(name, s.replace("{{keywords}}", q.keywords() == null ? "" : q.keywords())
                        .replace("{{location}}", location).replace("{{country}}", location));
            }
        }
        return obj.toString();
    }

    static JobOfferDraft map(JsonNode item, JsonNode mapping, ApifyActor actor) {
        java.util.function.Function<String, String> f = key -> {
            String p = text(mapping, key);
            return p == null ? null : path(item, p);
        };
        String url = f.apply("sourceUrl");
        return JobOfferDraft.builder(actor.sourceCode(), f.apply("externalId") != null ? f.apply("externalId") : url)
                .sourceUrl(url).applyUrl(f.apply("applyUrl") != null ? f.apply("applyUrl") : url)
                .title(f.apply("title")).company(f.apply("companyName"))
                .descriptionHtml(f.apply("descriptionHtml")).descriptionText(f.apply("descriptionText"))
                .location(f.apply("location"))
                .employmentType(f.apply("employmentType"))
                .salaryText(f.apply("salaryText"))
                .publishedAt(instant(f.apply("publishedAt")), false)
                .attribution("Vía " + actor.getDisplayName() + " (Apify)")
                .rawPayload(item.toString().length() > 60_000 ? null : item.toString())
                .build();
    }

    private static BigDecimal decimalOrZero(JsonNode n, String field) {
        BigDecimal d = decimal(n, field);
        return d == null ? BigDecimal.ZERO : d;
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        Limits l = limits(credentials.get("token"));
        return ProbeResult.ok("Token de Apify válido", Map.of(
                "monthlyUsageUsd", l.monthlyUsageUsd() == null ? "?" : l.monthlyUsageUsd().toPlainString(),
                "maxMonthlyUsageUsd", l.maxMonthlyUsageUsd() == null ? "?" : l.maxMonthlyUsageUsd().toPlainString()));
    }
}
