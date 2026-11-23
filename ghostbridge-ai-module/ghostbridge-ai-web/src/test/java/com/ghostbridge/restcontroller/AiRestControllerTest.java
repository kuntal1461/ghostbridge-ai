package com.ghostbridge.restcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.ghostbridge.support.AiWebTestRequests.GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.STATUS;
import static com.ghostbridge.support.AiWebTestRequests.STRUCTURED;
import static com.ghostbridge.support.AiWebTestRequests.USAGE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_GENERATE;
import static com.ghostbridge.support.AiWebTestRequests.VALID_STRUCTURED;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static com.ghostbridge.support.AiWebTestRequests.uniqueUser;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiRestControllerTest {
    @Autowired
    private MockMvc mvc;

    private ResultActions postAs(String user, String path, String body) throws Exception {
        return mvc.perform(post(path).with(aiUser(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void authorizedGenerationReturnsTheResponseVoWithoutLeakingInternals() throws Exception {
        postAs(uniqueUser(), GENERATE, VALID_GENERATE)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.model").value("mock-model"))
                .andExpect(jsonPath("$.text").isNotEmpty())
                .andExpect(jsonPath("$.structured.summary").isNotEmpty())
                .andExpect(jsonPath("$.usage.inputTokens").isNumber())
                .andExpect(jsonPath("$.usage.outputTokens").isNumber())
                .andExpect(jsonPath("$.usage.totalTokens").isNumber())
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.requesterId").doesNotExist());
    }

    @Test
    void generationAcceptsBoundedOptionsAndAnApprovedModel() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"promptVersion\":1,\"modelId\":\"mock-model\","
                + "\"variables\":{\"text\":\"hi\"},\"untrustedContent\":\"doc\",\"options\":{\"maxOutputTokens\":200,\"temperature\":0.5}}")
                .andExpect(status().isOk());
    }

    @Test
    void structuredGenerationReturnsOnlyValidatedStructuredOutput() throws Exception {
        postAs(uniqueUser(), STRUCTURED, VALID_STRUCTURED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaName").value("generic-summary"))
                .andExpect(jsonPath("$.structured.summary").isNotEmpty())
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unknownSchemaIsRejectedAs400() throws Exception {
        postAs(uniqueUser(), STRUCTURED, "{\"schemaName\":\"not-approved\",\"variables\":{\"text\":\"x\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.reason").value("INVALID_INPUT"));
    }

    @Test
    void clientCannotSubmitASchemaOrPickAPrompt() throws Exception {
        postAs(uniqueUser(), STRUCTURED, "{\"schemaName\":\"generic-summary\",\"schema\":{\"type\":\"object\"},\"promptName\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownPromptIsRejectedAs400() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"does-not-exist\",\"variables\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void emptyOrMissingRequiredFieldsAreRejectedWithFieldErrors() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("promptName"));
        postAs(uniqueUser(), GENERATE, "{}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), STRUCTURED, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void invalidFieldValuesAreRejected() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"bad name!\"}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"variables\":{\"1bad\":\"x\"}}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":null}}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"options\":{\"temperature\":2.5}}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"options\":{\"maxOutputTokens\":0}}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"promptVersion\":0}").andExpect(status().isBadRequest());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"promptVersion\":99999}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"options\":{\"maxOutputTokens\":99999999}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unapprovedModelIsRejectedByTheCore() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"modelId\":\"gpt-evil\",\"variables\":{\"text\":\"x\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void oversizedFieldIsMeasuredInUtf8BytesNotCharacters() throws Exception {
        String euros = "€".repeat(6000); // 6000 characters, 18000 bytes > 16384
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"" + euros + "\"}}")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void tooManyVariablesAndTooLongContentAreRejectedAs413() throws Exception {
        StringBuilder vars = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            vars.append(i == 0 ? "" : ",").append("\"v").append(i).append("\":\"x\"");
        }
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"variables\":{" + vars + "}}")
                .andExpect(status().isPayloadTooLarge());
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"untrustedContent\":\"" + "a".repeat(40000) + "\"}")
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void inputBeyondTheConfiguredCoreLimitIsMappedFromTheCoreTo413() throws Exception {
        // 9000 characters pass the HTTP bounds but exceed ghostbridge.ai.max-input-length (8000)
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"" + "a".repeat(9000) + "\"}}")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.reason").value("INPUT_TOO_LARGE"));
    }

    @Test
    void oversizedBodyIsRejectedBeforeParsing() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"" + "a".repeat(70000) + "\"}}")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void malformedJsonAndWrongContentTypeAreClientErrors() throws Exception {
        postAs(uniqueUser(), GENERATE, "{\"promptName\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(GENERATE).with(aiUser(uniqueUser())).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void providerStatusIsSafeAndPerformsNoInference() throws Exception {
        mvc.perform(get(STATUS).with(aiUser(uniqueUser())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.model").value("mock-model"))
                .andExpect(jsonPath("$.capabilities[0]").value("TEXT_GENERATION"))
                .andExpect(jsonPath("$.capabilities[1]").value("STRUCTURED_OUTPUT"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void usageShowsOnlyTheCallersOwnRequestsAndLimits() throws Exception {
        String alice = uniqueUser();
        String bob = uniqueUser();
        postAs(alice, GENERATE, VALID_GENERATE).andExpect(status().isOk());
        postAs(alice, GENERATE, VALID_GENERATE).andExpect(status().isOk());
        postAs(alice, GENERATE, "{\"promptName\":\"does-not-exist\"}").andExpect(status().isBadRequest());
        postAs(bob, GENERATE, VALID_GENERATE).andExpect(status().isOk());

        mvc.perform(get(USAGE).with(aiUser(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("RETAINED_HISTORY"))
                .andExpect(jsonPath("$.requestCount").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.requestsWithTokenUsage").value(2))
                .andExpect(jsonPath("$.totalTokens").isNumber())
                .andExpect(jsonPath("$.rateLimit.limitPerMinute").value(20))
                .andExpect(jsonPath("$.rateLimit.remaining").value(17))
                .andExpect(jsonPath("$.rateLimit.maxConcurrent").value(2))
                .andExpect(jsonPath("$.budget").doesNotExist());
        mvc.perform(get(USAGE).with(aiUser(bob)))
                .andExpect(jsonPath("$.requestCount").value(1))
                .andExpect(jsonPath("$.rateLimit.remaining").value(19));
        mvc.perform(get(USAGE).with(aiUser(uniqueUser())))
                .andExpect(jsonPath("$.requestCount").value(0))
                .andExpect(jsonPath("$.totalTokens").value(0))
                .andExpect(jsonPath("$.rateLimit.remaining").value(20));
    }

    @Test
    void responsesNeverContainCredentialMaterial() throws Exception {
        for (String body : new String[]{
                postAs(uniqueUser(), GENERATE, VALID_GENERATE).andReturn().getResponse().getContentAsString(),
                mvc.perform(get(STATUS).with(aiUser(uniqueUser()))).andReturn().getResponse().getContentAsString(),
                mvc.perform(get(USAGE).with(aiUser(uniqueUser()))).andReturn().getResponse().getContentAsString()}) {
            org.assertj.core.api.Assertions.assertThat(body.toLowerCase()).doesNotContain("authorization", "bearer", "apikey", "api-key", "api_key", "password", "secret");
        }
        postAs(uniqueUser(), GENERATE, "{\"promptName\":\"x!\"}").andExpect(content().string(not(containsString("Authorization"))));
    }
}
