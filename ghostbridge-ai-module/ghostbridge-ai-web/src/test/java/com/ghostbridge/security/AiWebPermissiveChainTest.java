package com.ghostbridge.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.STATUS;
import static com.ghostbridge.support.AiWebTestRequests.STRUCTURED;
import static com.ghostbridge.support.AiWebTestRequests.USAGE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_STRUCTURED;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static com.ghostbridge.support.AiWebTestRequests.withoutAiRole;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for the defense-in-depth claim: even when an application replaces the shared chain with one that permits
 * every request and disables CSRF, the AI API still answers 401 for anonymous callers and 403 for callers without the
 * AI role, because {@link AiRequesterResolver} enforces both independently of the filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiWebPermissiveChainTest {
    @TestConfiguration
    static class PermitAllChain {
        @Bean
        SecurityFilterChain permitAllChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable)
                    .build();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousCallersStillGet401OnEveryEndpoint() throws Exception {
        mvc.perform(post(GENERATE).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post(STRUCTURED).contentType(MediaType.APPLICATION_JSON).content(VALID_STRUCTURED))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USAGE)).andExpect(status().isUnauthorized());
    }

    @Test
    void callersWithoutTheAiRoleStillGet403OnEveryEndpoint() throws Exception {
        String user = uniqueUser();
        mvc.perform(post(GENERATE).with(withoutAiRole(user)).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(STRUCTURED).with(withoutAiRole(user)).contentType(MediaType.APPLICATION_JSON).content(VALID_STRUCTURED))
                .andExpect(status().isForbidden());
        mvc.perform(get(STATUS).with(withoutAiRole(user))).andExpect(status().isForbidden());
        mvc.perform(get(USAGE).with(withoutAiRole(user))).andExpect(status().isForbidden());
    }

    @Test
    void anAuthorizedCallerIsStillServed() throws Exception {
        mvc.perform(get(USAGE).with(aiUser(uniqueUser()))).andExpect(status().isOk());
    }
}
