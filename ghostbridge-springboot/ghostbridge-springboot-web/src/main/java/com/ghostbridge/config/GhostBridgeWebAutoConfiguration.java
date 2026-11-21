package com.ghostbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.advice.GlobalExceptionHandler;
import com.ghostbridge.filter.CorrelationIdFilter;
import com.ghostbridge.filter.RequestSizeLimitFilter;
import com.ghostbridge.util.ProblemResponseWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Shared servlet web infrastructure: correlation IDs, the global exception handler and the single CORS policy.
 * Every bean backs off when the application defines its own.
 */
@AutoConfiguration(after = GhostBridgeCoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GhostBridgeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Registered only when the advice was not already picked up by component scanning, so there is never a
     * duplicate. It is ordered last; applications override it by declaring their own advice with higher precedence.
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
            GhostBridgeProperties properties, ProblemResponseWriter responseWriter) {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(properties.upload().maxRequestSize().toBytes(), responseWriter);
        FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemResponseWriter problemResponseWriter(ObjectMapper objectMapper) {
        return new ProblemResponseWriter(objectMapper);
    }

    /** Named bean that Spring Security's {@code cors()} support also picks up. */
    @Bean(name = "corsConfigurationSource")
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    CorsConfigurationSource corsConfigurationSource(GhostBridgeProperties properties) {
        GhostBridgeProperties.Cors cors = properties.cors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.allowedOrigins());
        configuration.setAllowedMethods(cors.allowedMethods());
        configuration.setAllowedHeaders(cors.allowedHeaders());
        configuration.setExposedHeaders(cors.exposedHeaders());
        configuration.setMaxAge(cors.maxAge());
        configuration.setAllowCredentials(cors.allowCredentials());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Only when the shared security chain is off; with security on, the chain applies CORS itself. */
    @Bean
    @ConditionalOnMissingBean(name = "corsFilterRegistration")
    @ConditionalOnProperty(prefix = "ghostbridge.security", name = "enabled", havingValue = "false")
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(@Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
