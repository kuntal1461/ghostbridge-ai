package com.ghostbridge.dto;

import java.util.List;

/** Safe provider readiness for a UI: names only, never endpoints, credentials or detail text. */
public record AiProviderOverview(boolean configured, String state, String provider, String model, List<String> capabilities) {
}
