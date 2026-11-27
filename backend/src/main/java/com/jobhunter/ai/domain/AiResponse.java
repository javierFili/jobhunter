package com.jobhunter.ai.domain;

/** Raw provider answer + token usage. {@code content} is the model's JSON text (validated by the gateway). */
public record AiResponse(String content, String finishReason, boolean refused, int inputTokens, int cachedInputTokens,
                         int outputTokens, String modelUsed, long latencyMs) {
}
