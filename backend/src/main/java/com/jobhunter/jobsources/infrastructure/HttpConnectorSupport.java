package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.shared.http.SafeHttpClient;
import com.jobhunter.shared.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** Helpers shared by HTTP connectors: policies, URL building, tolerant JSON reading. */
public abstract class HttpConnectorSupport {

    protected final SafeHttpClient http;
    protected final ConnectorProperties props;

    protected HttpConnectorSupport(SafeHttpClient http, ConnectorProperties props) {
        this.http = http;
        this.props = props;
    }

    protected SafeHttpClient.Policy policy(String name, double rps, Duration timeout) {
        URI base = URI.create(props.baseUrl(name));
        return new SafeHttpClient.Policy(name, Set.of(base.getHost()), timeout, 2, rps, props.allowPrivateNetwork());
    }

    protected URI uri(String name, String path, Map<String, ?> params) {
        StringJoiner q = new StringJoiner("&");
        params.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()) {
                q.add(enc(k) + "=" + enc(v.toString()));
            }
        });
        String query = q.length() == 0 ? "" : "?" + q;
        return URI.create(props.baseUrl(name) + path + query);
    }

    protected static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    protected static JsonNode parse(SafeHttpClient.Response r, String source) {
        if (!r.ok()) {
            throw new SafeHttpClient.HttpCallException(source + " responded HTTP " + r.status(), r.status(), null);
        }
        try {
            return Json.tree(r.body());
        } catch (RuntimeException e) {
            throw new SafeHttpClient.HttpCallException(source + " returned invalid JSON", r.status(), e);
        }
    }

    protected static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ---- tolerant JSON accessors ----

    public static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        String s = v.isValueNode() ? v.asString() : v.toString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String path(JsonNode n, String dotted) {
        JsonNode cur = n;
        for (String part : dotted.split("\\.")) {
            if (cur == null) return null;
            cur = part.matches("\\d+") && cur.isArray() ? cur.get(Integer.parseInt(part)) : cur.get(part);
        }
        if (cur == null || cur.isNull() || cur.isMissingNode()) return null;
        String s = cur.isValueNode() ? cur.asString() : cur.toString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static BigDecimal decimal(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        try {
            BigDecimal d = v.isNumber() ? v.decimalValue() : new BigDecimal(v.asString().trim());
            return d.signum() > 0 ? d : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static List<String> strings(JsonNode n, String field) {
        List<String> out = new ArrayList<>();
        if (n == null) return out;
        JsonNode v = n.get(field);
        if (v == null || !v.isArray()) return out;
        v.forEach(e -> {
            String s = e.isValueNode() ? e.asString() : null;
            if (s != null && !s.isBlank()) out.add(s.trim());
        });
        return out;
    }

    public static Instant instant(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException e2) {
                try {
                    return Instant.parse(value);
                } catch (RuntimeException e3) {
                    return null;
                }
            }
        }
    }

    public static Instant epoch(JsonNode n, String field, boolean millis) {
        if (n == null || n.get(field) == null || !n.get(field).isNumber()) return null;
        long v = n.get(field).asLong();
        return millis ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
    }

    protected static boolean matchesKeywords(String text, String keywords) {
        if (keywords == null || keywords.isBlank()) return true;
        String t = text == null ? "" : text.toLowerCase();
        for (String word : keywords.toLowerCase().split("\\s+")) {
            if (!word.isBlank() && !t.contains(word)) return false;
        }
        return true;
    }
}
