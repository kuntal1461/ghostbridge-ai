package com.ghostbridge.restcontroller;

import com.ghostbridge.advice.AiExceptionAdvice;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code ghostbridge.ai.web.rest-enabled=false} (an application hosting the AI API through its own controller) AI Web
 * maps no AI endpoint of its own, yet the access interceptor and the AI error advice still protect and shape the
 * responses of any controller marked {@link AiApiEndpoint}.
 */
@SpringBootTest(properties = "ghostbridge.ai.web.rest-enabled=false")
@AutoConfigureMockMvc
class AiRestDisabledTest {
    @TestConfiguration
    static class HostController {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    @AiApiEndpoint
    static class ProbeController {
        @GetMapping("/api/v1/ai/probe")
        String probe() {
            throw new AiException(AiErrorReason.PROVIDER_THROTTLED, "AI provider is rate limiting requests", Duration.ofSeconds(7));
        }
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MockMvc mvc;

    @Test
    void theAiWebControllerIsNotRegisteredButTheAdviceAndSecurityAre() throws Exception {
        assertThat(context.getBeansOfType(AiRestController.class)).isEmpty();
        assertThat(context.getBeansOfType(AiExceptionAdvice.class)).hasSize(1);
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType("application/json").content("{}")).andExpect(status().isNotFound());
        mvc.perform(post(GENERATE).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherControllerMarkedAsAnAiEndpointGetsTheAiErrorMapping() throws Exception {
        mvc.perform(get("/api/v1/ai/probe").with(aiUser(uniqueUser())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.reason").value("PROVIDER_THROTTLED"));
        mvc.perform(get("/api/v1/ai/probe")).andExpect(status().isUnauthorized());
    }
}
