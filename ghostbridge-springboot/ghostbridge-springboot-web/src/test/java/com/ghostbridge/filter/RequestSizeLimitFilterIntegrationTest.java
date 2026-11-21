package com.ghostbridge.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ghostbridge.upload.max-request-size=1KB")
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class RequestSizeLimitFilterIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void oversizedBodyIsRejectedWith413BeforeParsing() throws Exception {
        String body = "{\"name\":\"" + "a".repeat(2000) + "\"}";
        mvc.perform(post("/api/echo").contentType("application/json").content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void bodyWithinLimitIsAccepted() throws Exception {
        mvc.perform(post("/api/echo").contentType("application/json").content("{\"name\":\"ok\"}"))
                .andExpect(status().isOk());
    }
}
