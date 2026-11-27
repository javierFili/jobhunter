package com.jobhunter.auth.application;

import com.jobhunter.shared.api.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory brute-force throttle for auth endpoints (default 10 attempts/minute per client IP). */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED = 10_000;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int perMinute;

    public LoginRateLimiter(@org.springframework.beans.factory.annotation.Value("${jobhunter.security.login-attempts-per-minute:10}") int perMinute) {
        this.perMinute = Math.max(1, perMinute);
    }

    public void check(String clientKey) {
        if (buckets.size() > MAX_TRACKED) {
            buckets.clear(); // bounded memory; worst case resets throttling for everyone
        }
        Bucket bucket = buckets.computeIfAbsent(clientKey == null ? "unknown" : clientKey, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Demasiados intentos; espera un minuto e inténtalo de nuevo");
        }
    }
}
