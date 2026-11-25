package com.ghostbridge.restcontroller;

import com.ghostbridge.support.AbstractAppIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The per-user limit of AI Web is enforced by the App controller too: moving the endpoints did not remove a limit. */
@SpringBootTest(properties = {"ghostbridge.ai.provider=mock", "ghostbridge.ai.web.requester-limit.requests-per-minute=2"})
class AppAiPerRequesterLimitTest extends AbstractAppIntegrationTest {
    private static final String BODY = "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"hi\"}}";

    @Test
    void theThirdRequestInTheWindowIsRejectedWithRetryAfter() throws Exception {
        Browser browser = signIn("alice");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ai/generate").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                    .contentType("application/json").content(BODY)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/ai/generate").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", not(emptyString())))
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"));
    }
}
