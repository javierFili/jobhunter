package com.jobhunter.shared.http;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The only way the backend talks to external HTTP services (WebClient on the JDK HTTP client, used blocking from
 * virtual threads). Each call names a {@link Policy}: allowed hosts, timeout, retries, rate limit, circuit breaker.
 * <ul>
 *   <li>SSRF: HTTPS only, host must be on the policy's allow-list and must not resolve to a private/loopback/
 *   link-local/multicast address. Redirects are followed manually and re-validated.</li>
 *   <li>Retries only on 429/5xx/connection errors, with exponential backoff + jitter, honouring Retry-After.</li>
 *   <li>Secrets: query strings are never logged.</li>
 * </ul>
 */
@Component
public class SafeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpClient.class);
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BODY_BYTES = 12 * 1024 * 1024;

    public record Policy(String name, Set<String> allowedHosts, Duration timeout, int maxRetries,
                         double requestsPerSecond, boolean allowPrivateNetwork) {

        public static Policy of(String name, Set<String> hosts, Duration timeout, int maxRetries, double rps) {
            return new Policy(name, hosts, timeout, maxRetries, rps, false);
        }
    }

    public record Response(int status, String body, HttpHeaders headers, long latencyMs) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Raised for non-retryable outcomes or after retries are exhausted. */
    public static class HttpCallException extends RuntimeException {
        private final int status;

        public HttpCallException(String message, int status, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final WebClient webClient;
    private final CircuitBreakerRegistry breakers;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public SafeHttpClient() {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.webClient = WebClient.builder()
                .clientConnector(new JdkClientHttpConnector(jdk))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_BODY_BYTES)).build())
                .defaultHeader(HttpHeaders.USER_AGENT, "JobHunterAI/0.4 (personal job search assistant)")
                .build();
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(20)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build());
    }

    public Response get(Policy policy, URI uri, Consumer<HttpHeaders> headers) {
        return execute(policy, HttpMethod.GET, uri, headers, null);
    }

    public Response postJson(Policy policy, URI uri, Consumer<HttpHeaders> headers, String jsonBody) {
        return execute(policy, HttpMethod.POST, uri, headers, jsonBody);
    }

    public Response execute(Policy policy, HttpMethod method, URI uri, Consumer<HttpHeaders> headers, String body) {
        CircuitBreaker breaker = breakers.circuitBreaker(policy.name());
        try {
            return breaker.executeSupplier(() -> withRetries(policy, method, uri, headers, body));
        } catch (CallNotPermittedException e) {
            throw new HttpCallException("Circuit open for " + policy.name() + "; skipping call", 503, e);
        }
    }

    private Response withRetries(Policy policy, HttpMethod method, URI uri, Consumer<HttpHeaders> headers, String body) {
        int attempt = 0;
        while (true) {
            acquire(policy);
            try {
                Response r = followRedirects(policy, method, uri, headers, body);
                boolean retryable = r.status() == 429 || r.status() >= 500;
                if (!retryable || attempt >= policy.maxRetries()) {
                    if (retryable) {
                        throw new HttpCallException(policy.name() + " responded " + r.status() + " after retries", r.status(), null);
                    }
                    return r;
                }
                sleep(backoff(attempt, r.headers().getFirst(HttpHeaders.RETRY_AFTER)));
            } catch (WebClientRequestException e) {
                if (attempt >= policy.maxRetries()) {
                    throw new HttpCallException(policy.name() + " connection error: " + e.getMostSpecificCause().getMessage(), 0, e);
                }
                sleep(backoff(attempt, null));
            }
            attempt++;
            log.debug("Retrying {} {} (attempt {})", policy.name(), uri.getHost(), attempt + 1);
        }
    }

    private Response followRedirects(Policy policy, HttpMethod method, URI uri, Consumer<HttpHeaders> headers, String body) {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            validate(policy, current);
            Response r = once(method, current, headers, body, policy.timeout());
            if (r.status() >= 300 && r.status() < 400 && r.headers().getLocation() != null) {
                current = current.resolve(r.headers().getLocation());
                continue;
            }
            return r;
        }
        throw new HttpCallException("Too many redirects calling " + policy.name(), 0, null);
    }

    private Response once(HttpMethod method, URI uri, Consumer<HttpHeaders> headers, String body, Duration timeout) {
        long start = System.nanoTime();
        WebClient.RequestBodySpec spec = webClient.method(method).uri(uri).headers(h -> {
            h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
            if (headers != null) headers.accept(h);
        });
        WebClient.RequestHeadersSpec<?> request = body == null ? spec
                : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
        Response response = request.exchangeToMono(res -> res.bodyToMono(String.class).defaultIfEmpty("")
                        .map(b -> new Response(res.statusCode().value(), b, res.headers().asHttpHeaders(),
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))))
                .timeout(timeout)
                .onErrorMap(java.util.concurrent.TimeoutException.class,
                        e -> new WebClientRequestException(e, method, uri, new HttpHeaders()))
                .block();
        if (response == null) throw new HttpCallException("Empty response", 0, null);
        return response;
    }

    /** SSRF defence: scheme, allow-list and resolved address checks. */
    void validate(Policy policy, URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !policy.allowPrivateNetwork()) {
            throw new HttpCallException("Only HTTPS is allowed", 0, null);
        }
        String host = uri.getHost();
        if (host == null || policy.allowedHosts().stream().noneMatch(h -> host.equalsIgnoreCase(h))) {
            throw new HttpCallException("Host not allowed for " + policy.name() + ": " + host, 0, null);
        }
        if (policy.allowPrivateNetwork()) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocalV6(address)
                        || isCgnat(address)) {
                    throw new HttpCallException("Host resolves to a non-public address: " + host, 0, null);
                }
            }
        } catch (UnknownHostException e) {
            throw new HttpCallException("Unknown host: " + host, 0, e);
        }
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    private static boolean isCgnat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
    }

    private void acquire(Policy policy) {
        if (policy.requestsPerSecond() <= 0) return;
        Bucket bucket = buckets.computeIfAbsent(policy.name(), k -> {
            long capacity = Math.max(1, (long) Math.ceil(policy.requestsPerSecond()));
            Duration period = Duration.ofMillis((long) (1000 * capacity / policy.requestsPerSecond()));
            return Bucket.builder().addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build()).build();
        });
        try {
            bucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpCallException("Interrupted while rate limiting", 0, e);
        }
    }

    static Duration backoff(int attempt, String retryAfter) {
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                return Duration.ofSeconds(Math.min(seconds, 30));
            } catch (NumberFormatException ignored) {
                // HTTP-date form: fall back to exponential backoff
            }
        }
        long base = 500L * (1L << Math.min(attempt, 5));
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(250));
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpCallException("Interrupted", 0, e);
        }
    }
}
