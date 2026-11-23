package com.ghostbridge.responsevo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Provider-reported token usage; fields the provider did not report are omitted, never fabricated. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiTokenUsageResponseVO(Integer inputTokens, Integer outputTokens, Integer totalTokens, BigDecimal estimatedCost) {
}
