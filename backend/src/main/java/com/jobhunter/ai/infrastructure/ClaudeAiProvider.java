package com.jobhunter.ai.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.jobhunter.ai.domain.AiCallException;
import com.jobhunter.ai.domain.AiProvider;
import com.jobhunter.ai.domain.AiRequest;
import com.jobhunter.ai.domain.AiResponse;
import com.jobhunter.ai.domain.ProviderCode;
import com.jobhunter.integrations.application.IntegrationProbe;
import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobsources.infrastructure.ConnectorProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Claude Messages API through the official Java SDK (com.anthropic:anthropic-java).
 * <ul>
 *   <li>Structured outputs: {@code output_config.format} with the task's JSON Schema.</li>
 *   <li>Thinking is not configured (current models run adaptive thinking); depth via {@code output_config.effort}.</li>
 *   <li>Static instructions cached with {@code cache_control} (prefix caching).</li>
 *   <li>{@code stop_reason: refusal} is surfaced; Opus 5.5 / Sonnet 5.5 requests opt into server-side fallbacks
 *       (beta {@code server-side-fallback-2026-07-01}, {@code fallbacks: "default"}) unless disabled in config.</li>
 *   <li>SDK retries disabled: the gateway owns retry/fallback policy (no double retries).</li>
 * </ul>
 */
@Component
public class ClaudeAiProvider implements AiProvider, IntegrationProbe {

    private static final Set<String> FALLBACK_MODELS = Set.of("claude-opus-5-5", "claude-sonnet-5-5");
    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 = new com.fasterxml.jackson.databind.ObjectMapper();

    private final ConnectorProperties props;
    private final IntegrationService integrations;
    private final Map<String, AnthropicClient> clients = new ConcurrentHashMap<>();

    public ClaudeAiProvider(ConnectorProperties props, IntegrationService integrations) {
        this.props = props;
        this.integrations = integrations;
    }

    @Override public ProviderCode provider() { return ProviderCode.CLAUDE; }
    @Override public IntegrationCode code() { return IntegrationCode.CLAUDE; }

    private AnthropicClient client(String apiKey) {
        String id = HexFormat.of().formatHex(com.jobhunter.jobs.domain.JobText.sha256(apiKey));
        if (clients.size() > 5) clients.clear();
        return clients.computeIfAbsent(id, k -> AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(props.baseUrl("anthropic"))
                .maxRetries(0)
                .timeout(Duration.ofMinutes(5))
                .build());
    }

    @Override
    public AiResponse complete(AiRequest r, Map<String, String> credentials) {
        long start = System.nanoTime();
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(r.model())
                .maxTokens((long) r.maxOutputTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(r.staticSystem())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(r.userContent());
        OutputConfig.Builder output = OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema(r.outputSchema())).build());
        if (r.effort() != null) output.effort(OutputConfig.Effort.of(r.effort().toLowerCase()));
        params.outputConfig(output.build());
        boolean fallbacks = integrations.config(IntegrationCode.CLAUDE).path("serverSideFallbacks").asBoolean(true);
        if (fallbacks && FALLBACK_MODELS.contains(r.model())) {
            params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        Message message;
        try {
            message = client(credentials.get("apiKey")).messages().create(params.build());
        } catch (AnthropicServiceException e) {
            throw new AiCallException("Claude HTTP " + e.statusCode() + ": " + e.getMessage(), e.statusCode(),
                    AiCallException.isRetryableStatus(e.statusCode()), e);
        } catch (AnthropicIoException e) {
            throw new AiCallException("Claude: error de red/timeout: " + e.getMessage(), 0, true, e);
        }
        boolean refused = message.stopReason().map(StopReason.REFUSAL::equals).orElse(false);
        String text = message.content().stream()
                .flatMap((ContentBlock b) -> b.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        long cached = message.usage().cacheReadInputTokens().orElse(0L);
        long input = message.usage().inputTokens() + cached + message.usage().cacheCreationInputTokens().orElse(0L);
        return new AiResponse(text, message.stopReason().map(Object::toString).orElse(null), refused, (int) input,
                (int) cached, (int) message.usage().outputTokens(), message.model().toString(),
                (System.nanoTime() - start) / 1_000_000);
    }

    /** JSON Schema string → SDK schema object (the SDK uses Jackson 2 internally). */
    static JsonOutputFormat.Schema schema(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JACKSON2.readTree(json);
            JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
            node.properties().forEach(e -> b.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
            return b.build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid output schema", e);
        }
    }

    @Override
    public ProbeResult probe(Map<String, String> credentials) {
        try {
            var page = client(credentials.get("apiKey")).models().list();
            List<String> ids = page.data().stream().map(m -> m.id()).limit(10).toList();
            return ProbeResult.ok("Clave de Anthropic válida", Map.of("models", ids));
        } catch (AnthropicServiceException e) {
            return ProbeResult.fail("HTTP " + e.statusCode() + " (¿API key correcta?)");
        }
    }

    /** Model ids reported by the API (for the routing picker). */
    public List<String> models(String apiKey) {
        return client(apiKey).models().list().data().stream().map(m -> m.id()).toList();
    }
}
