package com.ghostbridge.restcontroller;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.ConditionalOnAiWebSecurity;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.mapper.AiApiMapper;
import com.ghostbridge.mapper.AppAiMapper;
import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import com.ghostbridge.responsevo.AiGenerateResponseVO;
import com.ghostbridge.responsevo.AiProviderStatusResponseVO;
import com.ghostbridge.responsevo.AiStructuredGenerateResponseVO;
import com.ghostbridge.responsevo.AiUsageResponseVO;
import com.ghostbridge.security.AiRequesterLimiter;
import com.ghostbridge.security.AiRequesterResolver;
import com.ghostbridge.service.AppAiService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The browser-facing AI API of GhostBridge App: the only controller mapped to {@code /api/v1/ai/**}. It resolves the trusted
 * requester from the authentication context and delegates to {@link AppAiService}, which enforces the per-requester limit and
 * calls the AI core in process. Authentication is enforced by the shared chain and the AI role by AI Web's access interceptor
 * (which covers these paths) before this class is reached.
 */
@RestController
@AiApiEndpoint
@ConditionalOnAiWebSecurity
@RequestMapping(GhostBridgeAiLiterals.API_BASE_PATH)
public class AppAiController {
    private final AppAiService service;
    private final AiRequesterResolver resolver;
    private final AiRequesterLimiter limiter;
    private final AppAiMapper mapper;
    private final AiApiMapper responseMapper;
    private final AiProperties aiProperties;

    public AppAiController(AppAiService service, AiRequesterResolver resolver, AiRequesterLimiter limiter, AppAiMapper mapper,
                           AiApiMapper responseMapper, AiProperties aiProperties) {
        this.service = service;
        this.resolver = resolver;
        this.limiter = limiter;
        this.mapper = mapper;
        this.responseMapper = responseMapper;
        this.aiProperties = aiProperties;
    }

    @PostMapping(path = GhostBridgeAiLiterals.PATH_GENERATE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiGenerateResponseVO> generate(@Valid @RequestBody AiGenerateRequestVO request) {
        return noStore(responseMapper.toGenerateResponse(service.generate(mapper.toCommand(resolver.requireRequester(), request))));
    }

    @PostMapping(path = GhostBridgeAiLiterals.PATH_GENERATE_STRUCTURED, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiStructuredGenerateResponseVO> generateStructured(@Valid @RequestBody AiStructuredGenerateRequestVO request) {
        return noStore(responseMapper.toStructuredResponse(
                service.generateStructured(mapper.toCommand(resolver.requireRequester(), request)), request.schemaName()));
    }

    @GetMapping(GhostBridgeAiLiterals.PATH_PROVIDER_STATUS)
    public ResponseEntity<AiProviderStatusResponseVO> providerStatus() {
        resolver.requireRequester();
        return noStore(responseMapper.toProviderStatus(service.providerStatus()));
    }

    @GetMapping(GhostBridgeAiLiterals.PATH_USAGE)
    public ResponseEntity<AiUsageResponseVO> usage() {
        String requester = resolver.requireRequester();
        // The limiter is only read here, to show the caller their own limit; enforcement happens in the service.
        return noStore(responseMapper.toUsageResponse(service.usage(requester), aiProperties.historyMaxEntries(), limiter.status(requester)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
