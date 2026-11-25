package com.ghostbridge.mapper;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiGenerationOptions;
import com.ghostbridge.dto.AiProviderOverview;
import com.ghostbridge.dto.AiUsageSummary;
import com.ghostbridge.util.AiDurations;
import com.ghostbridge.security.AiRequesterLimiter;
import com.ghostbridge.responsevo.AiProviderStatusResponseVO;
import com.ghostbridge.responsevo.AiRateLimitResponseVO;
import com.ghostbridge.responsevo.AiUsageResponseVO;
import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiGenerationOptionsRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import com.ghostbridge.responsevo.AiGenerateResponseVO;
import com.ghostbridge.responsevo.AiStructuredGenerateResponseVO;
import com.ghostbridge.responsevo.AiTokenUsageResponseVO;

/**
 * Converts between the HTTP contracts and the provider-neutral core models, so neither side depends on the other's
 * types. The requester key is always supplied by the caller from the authentication context, never from a VO.
 */
public class AiApiMapper {

    public AiRequest toAiRequest(AiGenerateRequestVO vo, String requesterKey) {
        return new AiRequest(vo.promptName(), vo.promptVersion(), vo.variables(), vo.untrustedContent(), vo.modelId(),
                toOptions(vo.options()), requesterKey);
    }

    public AiRequest toAiRequest(AiStructuredGenerateRequestVO vo, String promptName, String requesterKey) {
        return new AiRequest(promptName, null, vo.variables(), vo.untrustedContent(), null, toOptions(vo.options()), requesterKey);
    }

    public AiGenerateResponseVO toGenerateResponse(AiResponse response) {
        return new AiGenerateResponseVO(response.requestId(), response.model(), response.text(), response.structured(),
                toUsage(response.usage()), response.durationMs());
    }

    public AiStructuredGenerateResponseVO toStructuredResponse(AiResponse response, String schemaName) {
        return new AiStructuredGenerateResponseVO(response.requestId(), schemaName, response.model(), response.structured(),
                toUsage(response.usage()), response.durationMs());
    }

    public AiTokenUsageResponseVO toUsage(AiUsage usage) {
        return new AiTokenUsageResponseVO(usage.inputTokens(), usage.outputTokens(), usage.totalTokens().orElse(null),
                usage.estimatedCost());
    }

    public AiProviderStatusResponseVO toProviderStatus(AiProviderOverview overview) {
        return new AiProviderStatusResponseVO(overview.configured(), overview.state(), overview.provider(), overview.model(),
                overview.capabilities());
    }

    public AiUsageResponseVO toUsageResponse(AiUsageSummary totals, int historyLimit, AiRequesterLimiter.Status status) {
        AiRateLimitResponseVO rateLimit = new AiRateLimitResponseVO(status.limit(), status.remaining(),
                AiDurations.ceilSeconds(status.resetIn()), status.maxConcurrent());
        return new AiUsageResponseVO(GhostBridgeAiLiterals.USAGE_SCOPE_RETAINED_HISTORY, historyLimit, totals.requests(),
                totals.successes(), totals.failures(), totals.requestsWithTokenUsage(), totals.inputTokens(), totals.outputTokens(),
                totals.totalTokens(), rateLimit);
    }

    public AiGenerationOptions toOptions(AiGenerationOptionsRequestVO options) {
        return options == null ? AiGenerationOptions.defaults()
                : new AiGenerationOptions(options.maxOutputTokens(), options.temperature());
    }
}
