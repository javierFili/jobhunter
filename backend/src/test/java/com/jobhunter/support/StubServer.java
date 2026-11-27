package com.jobhunter.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal local HTTP stub (no Docker, no network). Responses are keyed by path; requests are recorded. */
public class StubServer implements AutoCloseable {

    public record Reply(int status, String body, Map<String, String> headers) {
        public static Reply json(String body) {
            return new Reply(200, body, Map.of("Content-Type", "application/json"));
        }
    }

    private final HttpServer server;
    private final Map<String, List<Reply>> replies = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    public final List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<String> authHeaders = new java.util.concurrent.CopyOnWriteArrayList<>();

    public StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) authHeaders.add(auth);
            List<Reply> list = replies.getOrDefault(path, List.of(new Reply(404, "{}", Map.of())));
            int i = counters.computeIfAbsent(path, k -> new AtomicInteger()).getAndIncrement();
            Reply r = list.get(Math.min(i, list.size() - 1));
            r.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(r.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    /** Successive calls to the same path get successive replies (last one repeats). */
    public StubServer on(String path, Reply... sequence) {
        replies.put(path, new ArrayList<>(List.of(sequence)));
        counters.remove(path);
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
