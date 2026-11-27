package com.jobhunter.ai.domain;

import java.time.Duration;

/**
 * @param staticSystem   instructions + rules + schema: identical across calls → placed first for prompt caching
 * @param userContent    variable content (profile facts, untrusted job text)
 * @param outputSchema   JSON Schema (string) the answer must satisfy
 * @param thinking       DeepSeek thinking on/off (Claude: ignored, effort is used instead)
 * @param effort         Claude effort (LOW/MEDIUM/HIGH/XHIGH/MAX); null = provider default
 */
public record AiRequest(String model, String staticSystem, String userContent, String outputSchema,
                        int maxOutputTokens, Boolean thinking, String effort, Double temperature, Duration timeout) {
}
