package com.jobhunter.ai;

import com.jobhunter.ai.domain.AiCallException;
import com.jobhunter.ai.domain.AiRequest;
import com.jobhunter.ai.domain.AiResponse;
import com.jobhunter.ai.infrastructure.ClaudeAiProvider;
import com.jobhunter.ai.infrastructure.DeepSeekAiProvider;
import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobsources.infrastructure.ConnectorProperties;
import com.jobhunter.shared.http.SafeHttpClient;
import com.jobhunter.shared.json.Json;
import com.jobhunter.support.StubServer;
import com.jobhunter.support.StubServer.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Providers against a local stub — no real (paid) API calls. Responses are synthetic TEST DATA. */
class ProvidersTest {

    private StubServer stub;
    private ConnectorProperties props;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        props = new ConnectorProperties(Map.of("deepseek", stub.baseUrl(), "anthropic", stub.baseUrl()), true);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static AiRequest request(String model) {
        return new AiRequest(model, "Return json. TEST", "<untrusted_job_posting>x</untrusted_job_posting>",
                "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}},\"required\":[\"ok\"],\"additionalProperties\":false}",
                1000, false, "LOW", 0.1, Duration.ofSeconds(10));
    }

    @Test
    void deepSeekParsesUsageAndDisablesThinking() {
        stub.on("/chat/completions", Reply.json("""

                {"id":"x","model":"deepseek-flash","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"{\\"ok\\":true}"}}],
                 "usage":{"prompt_tokens":120,"completion_tokens":10,"total_tokens":130,"prompt_cache_hit_tokens":100,"prompt_cache_miss_tokens":20}}"""));
        AiResponse r = new DeepSeekAiProvider(new SafeHttpClient(), props).complete(request("deepseek-flash"), Map.of("apiKey", "TEST"));
        assertThat(r.content()).isEqualTo("{\"ok\":true}");
        assertThat(r.inputTokens()).isEqualTo(120);
        assertThat(r.cachedInputTokens()).isEqualTo(100);
        assertThat(r.outputTokens()).isEqualTo(10);
        assertThat(stub.authHeaders).contains("Bearer TEST");
    }

    @Test
    void deepSeekEmptyContentIsReturnedForTheGatewayToRepair() {
        stub.on("/chat/completions", Reply.json("""
                {"choices":[{"finish_reason":"stop","message":{"content":"   "}}],"usage":{"prompt_tokens":5,"completion_tokens":1}}"""));
        AiResponse r = new DeepSeekAiProvider(new SafeHttpClient(), props).complete(request("deepseek-flash"), Map.of("apiKey", "TEST"));
        assertThat(r.content()).isBlank();
    }

    @Test
    void deepSeek402IsNotRetryable() {
        stub.on("/chat/completions", new Reply(402, "{\"error\":{\"message\":\"Insufficient Balance\"}}", Map.of()));
        assertThatThrownBy(() -> new DeepSeekAiProvider(new SafeHttpClient(), props).complete(request("deepseek-flash"), Map.of("apiKey", "TEST")))
                .isInstanceOfSatisfying(AiCallException.class, e -> {
                    assertThat(e.status()).isEqualTo(402);
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.getMessage()).contains("Insufficient Balance");
                });
    }

    private ClaudeAiProvider claude() {
        IntegrationService integrations = mock(IntegrationService.class);
        when(integrations.config(IntegrationCode.CLAUDE)).thenReturn(Json.tree("{\"serverSideFallbacks\":true}"));
        return new ClaudeAiProvider(props, integrations);
    }

    @Test
    void claudeStructuredOutputAndUsage() {
        stub.on("/v1/messages", Reply.json("""
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"{\\"ok\\":true}"}],"stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":50,"output_tokens":7,"cache_read_input_tokens":200,"cache_creation_input_tokens":0}}"""));
        AiResponse r = claude().complete(request("claude-opus-5-5"), Map.of("apiKey", "sk-ant-test"));
        assertThat(r.content()).isEqualTo("{\"ok\":true}");
        assertThat(r.refused()).isFalse();
        assertThat(r.inputTokens()).isEqualTo(250);
        assertThat(r.cachedInputTokens()).isEqualTo(200);
        String sent = stub.requests.get(0);
        assertThat(sent).contains("POST /v1/messages");
    }

    @Test
    void claudeRefusalIsSurfaced() {
        stub.on("/v1/messages", Reply.json("""
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],
                 "stop_reason":"refusal","stop_sequence":null,"usage":{"input_tokens":5,"output_tokens":0}}"""));
        assertThat(claude().complete(request("claude-opus-5-5"), Map.of("apiKey", "sk-ant-test")).refused()).isTrue();
    }

    @Test
    void claude529IsRetryable() {
        stub.on("/v1/messages", new Reply(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}",
                Map.of("Content-Type", "application/json")));
        assertThatThrownBy(() -> claude().complete(request("claude-sonnet-5-5"), Map.of("apiKey", "sk-ant-test")))
                .isInstanceOfSatisfying(AiCallException.class, e -> assertThat(e.retryable()).isTrue());
    }
}
