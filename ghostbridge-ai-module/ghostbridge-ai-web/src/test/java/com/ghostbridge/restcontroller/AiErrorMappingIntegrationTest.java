package com.ghostbridge.restcontroller;

import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

/** G-M4-2: every documented failure maps to the documented status, code, reason and Retry-After. */
@SpringBootTest
@AutoConfigureMockMvc
class AiErrorMappingIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AiClient aiClient;

    @BeforeEach
    void resetClientStub() {
        reset(aiClient);
    }

    private ResultActions call() throws Exception {
        return mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.APPLICATION_JSON).content(VALID_GENERATE));
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
            "INVALID_INPUT,400,INVALID_INPUT,INVALID_INPUT",
            "INPUT_TOO_LARGE,413,PAYLOAD_TOO_LARGE,INPUT_TOO_LARGE",
            "RATE_LIMITED,429,RATE_LIMITED,RATE_LIMITED",
            "PROVIDER_THROTTLED,429,RATE_LIMITED,PROVIDER_THROTTLED",
            "BUDGET_EXHAUSTED,429,QUOTA_EXCEEDED,BUDGET_EXHAUSTED",
            "INVALID_MODEL_RESPONSE,502,UPSTREAM_ERROR,INVALID_MODEL_RESPONSE",
            "OUTPUT_VALIDATION_FAILED,502,UPSTREAM_ERROR,OUTPUT_VALIDATION_FAILED",
            "RESPONSE_TOO_LARGE,502,UPSTREAM_ERROR,RESPONSE_TOO_LARGE",
            "PROVIDER_FAILURE,502,UPSTREAM_ERROR,PROVIDER_FAILURE",
            "PROVIDER_UNAVAILABLE,503,UPSTREAM_UNAVAILABLE,PROVIDER_UNAVAILABLE",
            "MISSING_CONFIGURATION,503,UPSTREAM_UNAVAILABLE,PROVIDER_UNAVAILABLE",
            "MISSING_CREDENTIALS,503,UPSTREAM_UNAVAILABLE,PROVIDER_UNAVAILABLE",
            "INTERRUPTED,503,UPSTREAM_UNAVAILABLE,PROVIDER_UNAVAILABLE",
            "PROVIDER_TIMEOUT,504,UPSTREAM_TIMEOUT,PROVIDER_TIMEOUT"})
    void everyCoreFailureMapsToItsDocumentedContract(AiErrorReason reason, int httpStatus, String code, String publicReason) throws Exception {
        when(aiClient.generate(any())).thenThrow(new AiException(reason, "provider said: api-key=sk-123"));
        call().andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.status").value(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.reason").value(publicReason))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void retryAfterIsSentOnlyWhenTheSourceKnowsIt() throws Exception {
        when(aiClient.generate(any())).thenThrow(new AiException(AiErrorReason.RATE_LIMITED, "limited", Duration.ofSeconds(30)));
        call().andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"));

        reset(aiClient);
        when(aiClient.generate(any())).thenThrow(new AiException(AiErrorReason.BUDGET_EXHAUSTED, "budget", Duration.ofMillis(2500)));
        call().andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));

        reset(aiClient);
        when(aiClient.generate(any())).thenThrow(new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "down", Duration.ofSeconds(5)));
        call().andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"));

        reset(aiClient);
        when(aiClient.generate(any())).thenThrow(new AiException(AiErrorReason.RATE_LIMITED, "limited"));
        call().andExpect(status().isTooManyRequests()).andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void unexpectedFailuresAre500WithAGenericMessage() throws Exception {
        when(aiClient.generate(any())).thenThrow(new IllegalStateException("jdbc:postgresql://internal-db:5432/app password=hunter2"));
        call().andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("internal-db"))));
    }

    @Test
    void errorBodiesNeverEchoTheProviderMessage() throws Exception {
        when(aiClient.generate(any())).thenThrow(new AiException(AiErrorReason.MISSING_CREDENTIALS, "OLLAMA_API_KEY missing"));
        call().andExpect(content().string(not(containsString("OLLAMA_API_KEY"))));
    }
}
