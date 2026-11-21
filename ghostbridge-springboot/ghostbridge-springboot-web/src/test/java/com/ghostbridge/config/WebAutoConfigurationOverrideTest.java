package com.ghostbridge.config;

import com.ghostbridge.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebAutoConfigurationOverrideTest {

    @SpringBootTest
    @Nested
    class SharedInfrastructureLoads {
        @Autowired
        ApplicationContext context;

        @Test
        void loadsFromAutoConfigurationImportsWithExactlyOneSecurityChain() {
            assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1).containsKey("ghostBridgeSecurityFilterChain");
            assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
            assertThat(context.containsBean("corsConfigurationSource")).isTrue();
            assertThat(context.containsBean("correlationIdFilter")).isTrue();
            assertThat(context.containsBean("corsFilterRegistration")).isFalse();
        }
    }

    @SpringBootTest(classes = {com.ghostbridge.GhostBridgeWebTestApplication.class, CustomChainConfig.class, CustomChainConfig.ApplicationAdvice.class})
    @Nested
    class ApplicationChainReplacesSharedChain {
        @Autowired
        ApplicationContext context;

        @Test
        void sharedChainAndHandlerBackOff() {
            assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1).containsKey("applicationChain");
            List<ControllerAdviceBean> advices = ControllerAdviceBean.findAnnotatedBeans(context);
            assertThat(advices).hasSize(2);
            assertThat(advices.get(0).getBeanType()).isEqualTo(CustomChainConfig.ApplicationAdvice.class);
            assertThat(advices.get(1).getBeanType()).isEqualTo(GlobalExceptionHandler.class);
        }
    }

    @SpringBootTest(properties = "ghostbridge.security.enabled=false")
    @Nested
    class SecurityDisabled {
        @Autowired
        ApplicationContext context;

        @Test
        void noSharedChainButCorsFilterStillApplies() {
            assertThat(context.containsBean("ghostBridgeSecurityFilterChain")).isFalse();
            assertThat(context.getBeansOfType(FilterRegistrationBean.class)).containsKey("corsFilterRegistration");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CustomChainConfig {
        @Bean
        SecurityFilterChain applicationChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().denyAll()).build();
        }

        /** Application advice with explicit higher precedence than the shared one. */
        @TestConfiguration
        @ControllerAdvice
        @Order(Ordered.HIGHEST_PRECEDENCE)
        static class ApplicationAdvice extends ResponseEntityExceptionHandler {
        }
    }
}
