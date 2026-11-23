package com.ghostbridge.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.STATUS;
import static com.ghostbridge.support.AiWebTestRequests.STRUCTURED;
import static com.ghostbridge.support.AiWebTestRequests.USAGE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_STRUCTURED;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static com.ghostbridge.support.AiWebTestRequests.withoutAiRole;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, authorization, per-requester limiting (G-M4-1), spoofing, CORS and CSRF (stateless model). */
@SpringBootTest(properties = {
        "ghostbridge.ai.web.requester-limit.requests-per-minute=3",
        "ghostbridge.cors.allowed-origins=https://allowed.example"})
@AutoConfigureMockMvc
class AiWebSecurityTest {
    @Autowired
    private MockMvc mvc;

    private ResultActions generate(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE));
    }

    @Test
    void everyEndpointRejectsUnauthenticatedCallers() throws Exception {
        mvc.perform(post(GENERATE).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post(STRUCTURED).contentType(MediaType.APPLICATION_JSON).content(VALID_STRUCTURED)).andExpect(status().isUnauthorized());
        mvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USAGE)).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCallersWithoutTheAiRoleAreForbidden() throws Exception {
        String user = uniqueUser();
        mvc.perform(post(GENERATE).with(withoutAiRole(user)).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(STRUCTURED).with(withoutAiRole(user)).contentType(MediaType.APPLICATION_JSON).content(VALID_STRUCTURED))
                .andExpect(status().isForbidden());
        mvc.perform(get(STATUS).with(withoutAiRole(user))).andExpect(status().isForbidden());
        mvc.perform(get(USAGE).with(withoutAiRole(user))).andExpect(status().isForbidden());
    }

    @Test
    void authorizationIsCheckedBeforeTheBodyIsValidated() throws Exception {
        mvc.perform(post(GENERATE).with(withoutAiRole(uniqueUser())).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isForbidden());
    }

    @Test
    void perRequesterLimitReturns429WithRetryAfterAndDoesNotAffectOtherCallers() throws Exception {
        String heavy = uniqueUser();
        for (int i = 0; i < 3; i++) {
            generate(post(GENERATE).with(aiUser(heavy))).andExpect(status().isOk());
        }
        generate(post(GENERATE).with(aiUser(heavy)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", not(org.hamcrest.Matchers.emptyString())))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        Long.parseLong(result.getResponse().getHeader("Retry-After"))).isBetween(1L, 60L));
        mvc.perform(post(STRUCTURED).with(aiUser(heavy)).contentType(MediaType.APPLICATION_JSON).content(VALID_STRUCTURED))
                .andExpect(status().isTooManyRequests());
        generate(post(GENERATE).with(aiUser(uniqueUser()))).andExpect(status().isOk());
    }

    @Test
    void spoofedRequesterHeadersDoNotChangeTheLimitedIdentity() throws Exception {
        String user = uniqueUser();
        String[] spoofs = {"X-Requester-Id", "X-User", "X-Forwarded-User", "X-User-Id"};
        for (int i = 0; i < 3; i++) {
            generate(post(GENERATE).with(aiUser(user)).header(spoofs[i], "someone-else-" + i)).andExpect(status().isOk());
        }
        generate(post(GENERATE).with(aiUser(user)).header(spoofs[3], "yet-another")).andExpect(status().isTooManyRequests());
        mvc.perform(get(USAGE).with(aiUser(user)).header("X-Requester-Id", "someone-else-0"))
                .andExpect(jsonPath("$.requestCount").value(3));
    }

    @Test
    void aRequesterIdInTheBodyIsRejectedNotTrusted() throws Exception {
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"x\"},\"requesterId\":\"admin\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptName\":\"generic-structured\",\"options\":{\"userId\":\"admin\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void identitiesThatDifferOnlyByCaseOrWhitespaceAreLimitedIndependently() throws Exception {
        String base = uniqueUser();
        for (int i = 0; i < 3; i++) {
            generate(post(GENERATE).with(aiUser(base))).andExpect(status().isOk());
        }
        generate(post(GENERATE).with(aiUser(base))).andExpect(status().isTooManyRequests());
        generate(post(GENERATE).with(aiUser(base.toUpperCase()))).andExpect(status().isOk());
        generate(post(GENERATE).with(aiUser(base + " "))).andExpect(status().isOk());
    }

    @Test
    void errorBodiesStaySafeAndSecurityHeadersArePresent() throws Exception {
        mvc.perform(post(GENERATE).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void corsPreflightFollowsTheSharedConfiguration() throws Exception {
        mvc.perform(options(GENERATE).header("Origin", "https://allowed.example")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options(GENERATE).header("Origin", "https://evil.example").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        generate(post(GENERATE).with(aiUser(uniqueUser())).header("Origin", "https://evil.example")).andExpect(status().isForbidden());
    }

    @Test
    void statelessModeNeedsNoCsrfTokenForTokenAuthenticatedPosts() throws Exception {
        generate(post(GENERATE).with(aiUser(uniqueUser()))).andExpect(status().isOk());
    }
}
