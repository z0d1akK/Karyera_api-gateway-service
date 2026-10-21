package com.zodiakk.apigateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class StubHttpServer implements AutoCloseable {

    public record RecordedRequest(
            String method,
            String path,
            String body,
            Map<String, List<String>> headers
    ) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().isEmpty() ? null : entry.getValue().get(0))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record StubResponse(int status, String body) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
    private final Map<String, StubResponse> responses = new ConcurrentHashMap<>();

    private volatile StubResponse defaultResponse = new StubResponse(200, "{}");

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start stub HTTP server", exception);
        }

        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void respond(String pathPrefix, int status, String body) {
        responses.put(pathPrefix, new StubResponse(status, body));
    }

    public void reset() {
        requests.clear();
        responses.clear();
        defaultResponse = new StubResponse(200, "{}");
    }

    public RecordedRequest takeRequest() {
        try {
            return requests.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public RecordedRequest pollRequest() {
        try {
            return requests.poll(300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RecordedRequest request = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                body,
                Map.copyOf(exchange.getRequestHeaders())
        );
        requests.add(request);

        StubResponse response = responses.entrySet().stream()
                .filter(entry -> request.path().startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultResponse);

        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
