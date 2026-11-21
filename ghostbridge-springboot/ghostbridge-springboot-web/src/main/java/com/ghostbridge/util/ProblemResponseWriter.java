package com.ghostbridge.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes ProblemDetail JSON from servlet-level components (security filters) that run outside MVC. */
public class ProblemResponseWriter {
    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, ErrorCode code, String detail) throws IOException {
        ProblemDetail problem = ProblemDetailFactory.create(status, code, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
