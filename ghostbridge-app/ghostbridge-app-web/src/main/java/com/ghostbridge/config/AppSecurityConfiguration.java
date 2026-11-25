package com.ghostbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import com.ghostbridge.security.AppLoginFailureHandler;
import com.ghostbridge.security.AppLoginSuccessHandler;
import com.ghostbridge.security.GhostBridgeHttpSecurityCustomizer;
import com.ghostbridge.security.LoginAttemptLimiter;
import com.ghostbridge.security.LoginThrottleFilter;
import com.ghostbridge.util.ProblemResponseWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

import java.time.Clock;
import java.util.List;

/**
 * Authentication of the App, plugged into the shared deny-by-default chain through its single integration point. Login is
 * processed by Spring Security's own filter (session-fixation protection, CSRF check, context persistence), answered with
 * JSON, throttled against brute force, and logout invalidates the session. Users are held in memory from configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppSecurityProperties.class)
public class AppSecurityConfiguration {
    private static final String LOGIN_URL = GhostBridgeAppLiterals.AUTH_BASE_PATH + GhostBridgeAppLiterals.AUTH_PATH_LOGIN;
    private static final String LOGOUT_URL = GhostBridgeAppLiterals.AUTH_BASE_PATH + GhostBridgeAppLiterals.AUTH_PATH_LOGOUT;

    @Bean
    UserDetailsService appUserDetailsService(AppSecurityProperties properties) {
        List<UserDetails> users = properties.users().stream()
                .map(user -> User.withUsername(user.username()).password(user.passwordHash()).roles(user.roles().toArray(String[]::new)).build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter(AppSecurityProperties properties, Clock clock) {
        return new LoginAttemptLimiter(properties.loginAttempts(), clock);
    }

    @Bean
    AppSecurityValidator appSecurityValidator(AppSecurityProperties properties, Environment environment) {
        return new AppSecurityValidator(properties, environment);
    }

    @Bean
    GhostBridgeHttpSecurityCustomizer appAuthenticationCustomizer(ObjectMapper mapper, ProblemResponseWriter writer,
                                                                  LoginAttemptLimiter limiter, AppSecurityProperties properties,
                                                                  ServerProperties serverProperties) {
        return http -> http
                .formLogin(form -> form
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler(new AppLoginSuccessHandler(mapper, limiter, serverProperties.getServlet().getSession().getTimeout()))
                        .failureHandler(new AppLoginFailureHandler(writer, limiter)))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .invalidateHttpSession(true)
                        .deleteCookies(GhostBridgeAppLiterals.SESSION_COOKIE_NAME)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterBefore(new LoginThrottleFilter(limiter, properties.loginAttempts(), writer), UsernamePasswordAuthenticationFilter.class);
    }
}
