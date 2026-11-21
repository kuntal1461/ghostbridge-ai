package com.ghostbridge.config;

import com.ghostbridge.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Shared outbound HTTP conventions for every {@link RestClient}: connect/read timeouts, redirects never followed,
 * correlation ID propagation. TLS verification stays at the JDK default (never disabled) and no automatic
 * retries are applied, because retrying non-idempotent calls is unsafe.
 */
@AutoConfiguration(after = GhostBridgeCoreAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
public class GhostBridgeRestClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ghostBridgeRestClientCustomizer")
    RestClientCustomizer ghostBridgeRestClientCustomizer(GhostBridgeProperties properties) {
        GhostBridgeProperties.HttpClient settings = properties.httpClient();
        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(settings.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(settings.readTimeout());
            builder.requestFactory(factory);
            builder.requestInterceptor((request, body, execution) -> {
                String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                if (correlationId != null && !request.getHeaders().containsKey(CorrelationIdFilter.HEADER)) {
                    request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
                }
                return execution.execute(request, body);
            });
        };
    }
}
