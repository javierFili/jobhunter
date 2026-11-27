package com.jobhunter.ai.infrastructure;

import com.jobhunter.ai.domain.AiCallException;
import com.jobhunter.ai.domain.AiProvider;
import com.jobhunter.ai.domain.AiRequest;
import com.jobhunter.ai.domain.AiResponse;
import com.jobhunter.ai.domain.ProviderCode;
import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobsources.infrastructure.ConnectorProperties;
import com.jobhunter.shared.http.SafeHttpClient;
import com.jobhunter.shared.json.Json;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * DeepSeek chat completions (OpenAI-compatible), https://api-docs.deepseek.com/.
 * <ul>
 *   <li>JSON Output via {@code response_format: json_object}; the prompt contains the word "json" and an example.</li>
 *   <li>Invalid JSON is returned as whitespace → treated as retryable INVALID_OUTPUT by the gateway.</li>
 *   <li>Thinking mode is the default; extraction tasks send {@code "thinking": {"type": "disabled"}} (verified docs).</li>
 *   <li>The server may send keep-alive blank lines while queued; the body is trimmed before parsing.</li>
 * </ul>
 */
@Component
public class DeepSeekAiProvider implements AiProvider, IntegrationProbe {

    private final SafeHttpClient http;
    private final ConnectorProperties props;

    public DeepSeekAiProvider(SafeHttpClient http, ConnectorProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public ProviderCode provider() { return ProviderCode.DEEPSEEK; }
    @Override public IntegrationCode code() { return IntegrationCode.DEEPSEEK; }


    private SafeHttpClient.Policy policy(Duration timeout) {
        URI base = URI.create(props.baseUrl("deepseek"));
        return new SafeHttpClient.Policy("deepseek", Set.of(base.getHost()), timeout, 0, 5, props.allowPrivateNetwork());
    }

    @Override
    public AiResponse complete(AiRequest r, Map<String, String> credentials) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("model", r.model());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", r.staticSystem()
                + "\n\nReturn ONLY a json object that satisfies this JSON Schema:\n" + r.outputSchema());
        messages.addObject().put("role", "user").put("content", r.userContent());
        body.putObject("response_format").put("type", "json_object");
        body.put("max_tokens", r.maxOutputTokens());
        boolean thinking = r.thinking() != null && r.thinking();
        body.putObject("thinking").put("type", thinking ? "enabled" : "disabled");
        if (!thinking && r.temperature() != null) body.put("temperature", r.temperature());
        body.put("stream", false);

        SafeHttpClient.Response res;
        try {
            res = http.postJson(policy(r.timeout()), URI.create(props.baseUrl("deepseek") + "/chat/completions"),
                    h -> h.setBearerAuth(credentials.get("apiKey")), body.toString());
        } catch (SafeHttpClient.HttpCallException e) {
            throw new AiCallException("DeepSeek: " + e.getMessage(), e.status(), AiCallException.isRetryableStatus(e.status()), e);
        }
        if (!res.ok()) {
            String detail = errorMessage(res.body());
            throw new AiCallException("DeepSeek HTTP " + res.status() + (detail == null ? "" : ": " + detail), res.status(),
                    AiCallException.isRetryableStatus(res.status()), null);
        }
        JsonNode root;
        try {
            root = Json.tree(res.body().trim());
        } catch (RuntimeException e) {
            throw new AiCallException("DeepSeek devolvió una respuesta que no es JSON", res.status(), true, e);
        }
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asString("");
        JsonNode usage = root.path("usage");
        int hit = usage.path("prompt_cache_hit_tokens").asInt(0);
        int miss = usage.path("prompt_cache_miss_tokens").asInt(usage.path("prompt_tokens").asInt(0) - hit);
        return new AiResponse(content, choice.path("finish_reason").asString(null), false, hit + miss, hit,
                usage.path("completion_tokens").asInt(0), root.path("model").asString(r.model()), res.latencyMs());
    }

    private static String errorMessage(String body) {
        try {
            return Json.tree(body).path("error").path("message").asString(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        var res = http.get(policy(Duration.ofSeconds(20)), URI.create(props.baseUrl("deepseek") + "/user/balance"),
                h -> h.setBearerAuth(credentials.get("apiKey")));
        if (!res.ok()) return ProbeResult.fail("HTTP " + res.status() + " (¿API key correcta?)");
        JsonNode root = Json.tree(res.body());
        JsonNode info = root.path("balance_infos").path(0);
        return ProbeResult.ok(root.path("is_available").asBoolean(false) ? "Clave válida y con saldo" : "Clave válida pero SIN saldo",
                Map.of("currency", info.path("currency").asString("?"), "totalBalance", info.path("total_balance").asString("?")));
    }
}
