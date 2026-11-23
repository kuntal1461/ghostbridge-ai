package com.ghostbridge.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local, deterministic stand-in for Ollama Cloud (loopback only, ephemeral port, no network access). */
final class StubOllamaServer implements AutoCloseable {

    /** A request as the server saw it. */
    record Received(String method, String path, String query, Map<String, List<String>> headers, byte[] body) {
        String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    static final String OK_BODY = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"}}],"
            + "\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7}}";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Received> requests = new CopyOnWriteArrayList<>();

    StubOllamaServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(), Map.copyOf(exchange.getRequestHeaders()),
                    exchange.getRequestBody().readAllBytes()));
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    static StubOllamaServer replying(int status, String body) throws IOException {
        return new StubOllamaServer(exchange -> respond(exchange, status, body));
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    int port() {
        return server.getAddress().getPort();
    }

    URI chatUri() {
        return URI.create("http://127.0.0.1:" + port() + "/v1/chat/completions");
    }

    List<Received> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
