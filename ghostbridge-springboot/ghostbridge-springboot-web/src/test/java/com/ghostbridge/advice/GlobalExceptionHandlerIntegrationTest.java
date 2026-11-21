package com.ghostbridge.advice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class GlobalExceptionHandlerIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void validationErrorListsFieldsOnly() throws Exception {
        mvc.perform(post("/api/echo").contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJsonIsMalformedRequestWithoutParserDetail() throws Exception {
        mvc.perform(post("/api/echo").contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("Unexpected"))));
    }

    @Test
    void notFoundBusinessExceptionMapsTo404() throws Exception {
        mvc.perform(get("/api/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Thing 42 not found"));
    }

    @Test
    void businessExceptionMapsTo422() throws Exception {
        mvc.perform(get("/api/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void unexpectedErrorsNeverLeakInternals() throws Exception {
        mvc.perform(get("/api/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    @Test
    void unsupportedMethodUsesSharedContract() throws Exception {
        mvc.perform(post("/api/ping"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void correlationIdInBodyMatchesResponseHeader() throws Exception {
        mvc.perform(get("/api/missing").header("X-Correlation-Id", "test-correlation-1"))
                .andExpect(header().string("X-Correlation-Id", "test-correlation-1"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-1"));
    }
}
