package com.ghostbridge.restcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.STATUS;
import static com.ghostbridge.support.AiWebTestRequests.VALID_GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No provider configured: generation is a generic 503 (no configuration detail, no invented Retry-After); status says so. */
@SpringBootTest(properties = "ghostbridge.ai.provider=")
@AutoConfigureMockMvc
class AiProviderNotConfiguredTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void generationIsUnavailableWithoutDisclosingConfiguration() throws Exception {
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.reason").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("AI provider is unavailable"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void statusReportsNotConfiguredWithoutProviderOrModel() throws Exception {
        mvc.perform(get(STATUS).with(aiUser(uniqueUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.model").doesNotExist());
    }
}
