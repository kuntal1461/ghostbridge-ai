package com.ghostbridge.controller;

import com.ghostbridge.exception.BusinessException;
import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.exception.GhostBridgeException;
import com.ghostbridge.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only endpoints used to exercise the shared infrastructure. */
@RestController
class WebTestProbeController {

    record EchoRequest(@NotBlank String name) {
    }

    @GetMapping("/api/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/public/ping")
    Map<String, String> publicPing() {
        return Map.of("status", "public");
    }

    @PostMapping("/api/echo")
    Map<String, String> echo(@Valid @RequestBody EchoRequest request) {
        return Map.of("name", request.name());
    }

    @GetMapping("/api/admin")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> admin() {
        return Map.of("status", "admin");
    }

    @GetMapping("/api/boom")
    String boom() {
        throw new IllegalStateException("jdbc:postgresql://internal-db:5432/app password=hunter2");
    }

    @GetMapping("/api/missing")
    String missing() {
        throw new ResourceNotFoundException("Thing 42 not found");
    }

    @GetMapping("/api/business")
    String business() {
        throw new BusinessException("Cannot do that");
    }

    @GetMapping("/api/error-code/{code}")
    String errorCode(@PathVariable ErrorCode code) {
        throw new GhostBridgeException(code, "client-safe message");
    }
}
