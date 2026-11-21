package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ghostbridge.cors.allowed-origins=https://allowed.example","ghostbridge.security.public-paths=/public/**"})
@AutoConfigureMockMvc
class CorsSecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void allowedOriginGetsCorsHeaders() throws Exception {
        mvc.perform(get("/public/ping").header("Origin", "https://allowed.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.example"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Correlation-Id"));
    }

    @Test
    void disallowedOriginIsRejected() throws Exception {
        mvc.perform(get("/public/ping").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightFromAllowedOriginSucceedsWithoutAuthentication() throws Exception {
        mvc.perform(options("/api/ping")
                        .header("Origin", "https://allowed.example")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.example"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/ping")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void credentialsAreNotAllowedByDefault() throws Exception {
        mvc.perform(get("/public/ping").header("Origin", "https://allowed.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
