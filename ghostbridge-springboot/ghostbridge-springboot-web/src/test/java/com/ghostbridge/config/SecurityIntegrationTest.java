package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ghostbridge.cors.allowed-origins=https://allowed.example","ghostbridge.security.public-paths=/public/**"})
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void unauthenticatedRequestGets401ProblemJson() throws Exception {
        mvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticatedUserWithoutRoleGets403() throws Exception {
        mvc.perform(get("/api/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityAllowsRequiredRole() throws Exception {
        mvc.perform(get("/api/admin")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticatedUserReachesProtectedEndpoint() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
    }

    @Test
    void publicEndpointNeedsNoAuthentication() throws Exception {
        mvc.perform(get("/public/ping")).andExpect(status().isOk());
    }

    @Test
    void unknownPathIsNotPubliclyProbeable() throws Exception {
        mvc.perform(get("/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void statelessModeNeedsNoCsrfToken() throws Exception {
        mvc.perform(post("/api/echo").contentType("application/json").content("{\"name\":\"a\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void noHttpSessionIsCreatedInStatelessMode() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(result ->
                org.junit.jupiter.api.Assertions.assertNull(result.getRequest().getSession(false)));
    }
}
