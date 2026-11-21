package com.ghostbridge.config;

import com.ghostbridge.security.GhostBridgeHttpSecurityCustomizer;
import com.ghostbridge.security.ProblemAccessDeniedHandler;
import com.ghostbridge.security.ProblemAuthenticationEntryPoint;
import com.ghostbridge.util.ProblemResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The one shared Spring Security setup: deny by default, JSON 401/403, hardened headers, central CORS,
 * restricted actuator. Backs off completely when the application defines its own {@link SecurityFilterChain}.
 * No authentication mechanism is configured here; applications plug theirs in through
 * {@link GhostBridgeHttpSecurityCustomizer} beans.
 */
@AutoConfiguration(
        after = {GhostBridgeCoreAutoConfiguration.class, GhostBridgeWebAutoConfiguration.class},
        before = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnProperty(prefix = "ghostbridge.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableWebSecurity
@EnableMethodSecurity
public class GhostBridgeSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain ghostBridgeSecurityFilterChain(
            HttpSecurity http,
            GhostBridgeProperties properties,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
            ProblemResponseWriter responseWriter,
            ObjectProvider<GhostBridgeHttpSecurityCustomizer> customizers) throws Exception {
        GhostBridgeProperties.Security security = properties.security();

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint(responseWriter))
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(responseWriter)))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        security.stateless() ? SessionCreationPolicy.STATELESS : SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(security.contentSecurityPolicy()))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.get(security.referrerPolicy())))
                        .httpStrictTransportSecurity(hsts -> {
                            hsts.includeSubDomains(true).maxAgeInSeconds(security.hstsMaxAge().toSeconds());
                            if (!security.hstsEnabled()) {
                                hsts.disable();
                            }
                        }));

        configureCsrf(http, security);
        configureAuthorization(http, security);
        for (GhostBridgeHttpSecurityCustomizer customizer : customizers.orderedStream().toList()) {
            customizer.customize(http);
        }
        return http.build();
    }

    /**
     * CSRF protects cookie/session authentication. In the stateless model credentials are sent explicitly
     * (Authorization header) and the browser never attaches them automatically, so CSRF is disabled only then.
     * With sessions enabled, Spring's default CSRF protection stays on.
     */
    private static void configureCsrf(HttpSecurity http, GhostBridgeProperties.Security security) throws Exception {
        if (security.stateless()) {
            http.csrf(AbstractHttpConfigurer::disable);
        }
    }

    private static void configureAuthorization(HttpSecurity http, GhostBridgeProperties.Security security) throws Exception {
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll();
            auth.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(security.actuatorRole());
            if (!security.publicPaths().isEmpty()) {
                auth.requestMatchers(security.publicPaths().toArray(String[]::new)).permitAll();
            }
            auth.anyRequest().authenticated();
        });
    }

    /** Prevents Spring Boot from creating its default user with a random password. */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService ghostBridgeUserDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder ghostBridgePasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
