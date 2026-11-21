package com.ghostbridge.config;

import com.ghostbridge.filter.CorrelationIdFilter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConventionsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GhostBridgeCoreAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    GhostBridgeRestClientAutoConfiguration.class));

    private HttpServer server;
    private final AtomicReference<String> receivedCorrelationId = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            receivedCorrelationId.set(exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/echo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        MDC.clear();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void propagatesCorrelationIdToOutboundCalls() {
        runner.run(context -> {
            MDC.put(CorrelationIdFilter.MDC_KEY, "corr-12345678");
            RestClient client = context.getBean(RestClient.Builder.class).build();
            client.get().uri(url("/echo")).retrieve().toBodilessEntity();
            assertThat(receivedCorrelationId.get()).isEqualTo("corr-12345678");
        });
    }

    @Test
    void doesNotFollowRedirects() {
        runner.run(context -> {
            RestClient client = context.getBean(RestClient.Builder.class).build();
            ResponseEntity<Void> response = client.get().uri(url("/redirect")).retrieve().toBodilessEntity();
            assertThat(response.getStatusCode().value()).isEqualTo(302);
            assertThat(receivedCorrelationId.get()).isNull();
        });
    }
}
