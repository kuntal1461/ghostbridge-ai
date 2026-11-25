package com.ghostbridge.config;

import com.ghostbridge.advice.AiExceptionAdvice;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.filter.AiRequestBodyLimitFilter;
import com.ghostbridge.mapper.AiApiMapper;
import com.ghostbridge.restcontroller.AiRestController;
import com.ghostbridge.security.AiAccessInterceptor;
import com.ghostbridge.security.AiRequesterLimiter;
import com.ghostbridge.security.AiRequesterResolver;
import com.ghostbridge.service.AiApiService;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiRequestRegistry;
import com.ghostbridge.service.impl.DefaultAiApiService;
import com.ghostbridge.util.ProblemResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Registers the AI REST API. It is registered only when the shared security chain is enabled
 * ({@code ghostbridge.security.enabled}, default true), so the endpoints can never exist unauthenticated.
 * Beans are overridable (the MVC interceptor registration excepted); nothing here replaces shared CORS, security or the global exception handler.
 */
@AutoConfiguration(afterName = {
        "com.ghostbridge.config.AiAutoConfiguration",
        "com.ghostbridge.config.GhostBridgeWebAutoConfiguration",
        "com.ghostbridge.config.GhostBridgeSecurityAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnAiWebSecurity
@EnableConfigurationProperties(AiWebProperties.class)
public class AiWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AiRequesterResolver aiRequesterResolver(AiWebProperties properties) {
        return new AiRequesterResolver(properties.requiredRole());
    }

    @Bean
    @ConditionalOnMissingBean
    AiRequesterLimiter aiRequesterLimiter(AiWebProperties properties, Clock clock) {
        return new AiRequesterLimiter(properties.requesterLimit(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    AiApiMapper aiApiMapper() {
        return new AiApiMapper();
    }

    @Bean
    @ConditionalOnAiRestApi
    @ConditionalOnMissingBean(AiApiService.class)
    AiApiService aiApiService(AiClient client, AiRequesterLimiter limiter, AiApiMapper mapper, AiProperties aiProperties,
                              AiWebProperties webProperties, AiRequestRegistry registry, ObjectProvider<AiProvider> providers) {
        return new DefaultAiApiService(client, limiter, mapper, aiProperties, webProperties, registry,
                providers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnAiRestApi
    @ConditionalOnMissingBean
    AiRestController aiRestController(AiApiService service, AiRequesterResolver resolver) {
        return new AiRestController(service, resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    AiExceptionAdvice aiExceptionAdvice() {
        return new AiExceptionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    AiAccessInterceptor aiAccessInterceptor(AiRequesterResolver resolver) {
        return new AiAccessInterceptor(resolver);
    }

    @Bean
    WebMvcConfigurer aiAccessWebMvcConfigurer(AiAccessInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns(GhostBridgeAiLiterals.API_PATTERN);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "aiRequestBodyLimitFilter")
    FilterRegistrationBean<AiRequestBodyLimitFilter> aiRequestBodyLimitFilter(AiWebProperties properties,
                                                                              ProblemResponseWriter responseWriter) {
        FilterRegistrationBean<AiRequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new AiRequestBodyLimitFilter(properties.maxRequestBytes(), responseWriter));
        registration.addUrlPatterns(GhostBridgeAiLiterals.API_FILTER_PATTERN);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
