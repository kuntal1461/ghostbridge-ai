package com.ghostbridge.advice;

import com.ghostbridge.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression table: every {@link ErrorCode} maps to a fixed HTTP status through the shared handler. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class ErrorCodeStatusMappingTest {
    private static final Set<String> TABLE = Set.of(
            "VALIDATION_ERROR", "INVALID_INPUT", "MALFORMED_REQUEST", "AUTHENTICATION_REQUIRED", "ACCESS_DENIED",
            "RESOURCE_NOT_FOUND", "BUSINESS_RULE_VIOLATION", "INTERNAL_ERROR",
            "RATE_LIMITED", "QUOTA_EXCEEDED", "PAYLOAD_TOO_LARGE", "UPSTREAM_UNAVAILABLE", "UPSTREAM_TIMEOUT", "UPSTREAM_ERROR");

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "VALIDATION_ERROR,400", "INVALID_INPUT,400", "MALFORMED_REQUEST,400",
            "AUTHENTICATION_REQUIRED,401", "ACCESS_DENIED,403", "RESOURCE_NOT_FOUND,404",
            "BUSINESS_RULE_VIOLATION,422", "INTERNAL_ERROR,500",
            "RATE_LIMITED,429", "QUOTA_EXCEEDED,429", "PAYLOAD_TOO_LARGE,413", "UPSTREAM_UNAVAILABLE,503", "UPSTREAM_TIMEOUT,504", "UPSTREAM_ERROR,502"})
    void errorCodeMapsToExpectedStatus(String code, int expectedStatus) throws Exception {
        mvc.perform(get("/api/error-code/" + code))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value("client-safe message"));
    }

    @Test
    void tableCoversEveryErrorCode() {
        Set<String> actual = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(TABLE);
    }
}
