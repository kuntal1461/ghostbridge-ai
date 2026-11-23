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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The core's global limit stays active behind the per-requester limit (the per-requester default is 20/minute). */
@SpringBootTest(properties = "ghostbridge.ai.rate-limit.requests-per-minute=2")
@AutoConfigureMockMvc
class AiGlobalLimitIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void globalLimitStillAppliesAcrossDifferentRequesters() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }
}
