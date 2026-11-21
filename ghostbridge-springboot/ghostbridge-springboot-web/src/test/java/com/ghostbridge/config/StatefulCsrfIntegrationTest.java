package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ghostbridge.security.stateless=false")
@AutoConfigureMockMvc
class StatefulCsrfIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser(roles = "USER")
    void sessionModeRejectsPostWithoutCsrfToken() throws Exception {
        mvc.perform(post("/api/echo").contentType("application/json").content("{\"name\":\"a\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void sessionModeAcceptsPostWithCsrfToken() throws Exception {
        mvc.perform(post("/api/echo").with(csrf()).contentType("application/json").content("{\"name\":\"a\"}"))
                .andExpect(status().isOk());
    }
}
