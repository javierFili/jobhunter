package com.jobhunter.ai.application;

import com.jobhunter.ai.domain.AiResponse;
import com.jobhunter.ai.domain.ProviderCode;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Cost of a call from token usage and the prices stored in the integration config (USD per 1M tokens). */
public final class AiPricing {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private AiPricing() {
    }

    public static BigDecimal cost(ProviderCode provider, JsonNode config, String model, AiResponse r, Instant at) {
        JsonNode price = config.path("prices").path(model);
        if (price.isMissingNode()) return BigDecimal.ZERO; // unknown model price: usage still logged, cost unknown
        BigDecimal input = price.path("input").decimalValue();
        BigDecimal cached = price.has("cachedInput") ? price.path("cachedInput").decimalValue() : input;
        BigDecimal output = price.path("output").decimalValue();
        int uncached = Math.max(0, r.inputTokens() - r.cachedInputTokens());
        BigDecimal cost = input.multiply(BigDecimal.valueOf(uncached))
                .add(cached.multiply(BigDecimal.valueOf(r.cachedInputTokens())))
                .add(output.multiply(BigDecimal.valueOf(r.outputTokens())))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        if (provider == ProviderCode.DEEPSEEK && !isDeepSeekPeak(at)) {
            cost = cost.multiply(BigDecimal.ONE.subtract(config.path("offPeakDiscount").decimalValue()))
                    .setScale(6, RoundingMode.HALF_UP);
        }
        return cost;
    }

    /** Worst-case estimate before a call (all input uncached, full output budget) for the budget guard. */
    public static BigDecimal estimate(JsonNode config, String model, int inputChars, int maxOutputTokens) {
        JsonNode price = config.path("prices").path(model);
        if (price.isMissingNode()) return BigDecimal.ZERO;
        long inputTokens = inputChars / 3L + 500;
        return price.path("input").decimalValue().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.path("output").decimalValue().multiply(BigDecimal.valueOf(maxOutputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    /** DeepSeek peak hours (official pricing page): 01:00–04:00 and 06:00–10:00 UTC, Monday–Friday. */
    static boolean isDeepSeekPeak(Instant at) {
        ZonedDateTime t = at.atZone(ZoneOffset.UTC);
        if (t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
        LocalTime time = t.toLocalTime();
        return (!time.isBefore(LocalTime.of(1, 0)) && time.isBefore(LocalTime.of(4, 0)))
                || (!time.isBefore(LocalTime.of(6, 0)) && time.isBefore(LocalTime.of(10, 0)));
    }
}
