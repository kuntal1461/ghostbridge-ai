package com.ghostbridge.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With cookie/session authentication (stateless=false) CSRF protection stays on for the AI endpoints. */
@SpringBootTest(properties = "ghostbridge.security.stateless=false")
@AutoConfigureMockMvc
class AiWebStatefulCsrfTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrfTokenIsAccepted() throws Exception {
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isOk());
    }
}
