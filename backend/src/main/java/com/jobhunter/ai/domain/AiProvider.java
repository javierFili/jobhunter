package com.jobhunter.ai.domain;

import java.util.Map;

/** An LLM provider. Implementations never retry on their own: retries/fallback are decided by the gateway. */
public interface AiProvider {

    ProviderCode provider();

    AiResponse complete(AiRequest request, Map<String, String> credentials);
}
