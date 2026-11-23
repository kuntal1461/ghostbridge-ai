package com.ghostbridge.responsevo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Safe provider metadata. Never carries credentials, endpoints, provider detail text or configuration values. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiProviderStatusResponseVO(
        boolean configured,
        String status,
        String provider,
        String model,
        List<String> capabilities) {
}
