package com.jobhunter.jobsources.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Base URLs of every connector (overridable only by server config — used by tests to point at a local stub).
 * {@code allowPrivateNetwork} must stay false outside tests (SSRF protection).
 */
@ConfigurationProperties(prefix = "jobhunter.connectors")
public record ConnectorProperties(Map<String, String> baseUrls, boolean allowPrivateNetwork) {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("remotive", "https://remotive.com"),
            Map.entry("himalayas", "https://himalayas.app"),
            Map.entry("jobicy", "https://jobicy.com"),
            Map.entry("remoteok", "https://remoteok.com"),
            Map.entry("greenhouse", "https://boards-api.greenhouse.io"),
            Map.entry("lever", "https://api.lever.co"),
            Map.entry("ashby", "https://api.ashbyhq.com"),
            Map.entry("serpapi", "https://serpapi.com"),
            Map.entry("adzuna", "https://api.adzuna.com"),
            Map.entry("apify", "https://api.apify.com"),
            Map.entry("deepseek", "https://api.deepseek.com"),
            Map.entry("anthropic", "https://api.anthropic.com"));

    public ConnectorProperties {
        Map<String, String> merged = new HashMap<>(DEFAULTS);
        if (baseUrls != null) merged.putAll(baseUrls);
        baseUrls = Map.copyOf(merged);
    }

    public String baseUrl(String name) {
        return baseUrls.get(name).replaceAll("/+$", "");
    }
}
