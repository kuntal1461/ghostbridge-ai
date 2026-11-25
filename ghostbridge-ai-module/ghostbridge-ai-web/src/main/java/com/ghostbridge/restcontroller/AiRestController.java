package com.ghostbridge.restcontroller;

import com.ghostbridge.config.ConditionalOnAiRestApi;
import com.ghostbridge.config.ConditionalOnAiWebSecurity;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import com.ghostbridge.responsevo.AiGenerateResponseVO;
import com.ghostbridge.responsevo.AiProviderStatusResponseVO;
import com.ghostbridge.responsevo.AiStructuredGenerateResponseVO;
import com.ghostbridge.responsevo.AiUsageResponseVO;
import com.ghostbridge.security.AiRequesterResolver;
import com.ghostbridge.service.AiApiService;
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
 * Thin HTTP adapter: resolves the trusted requester, delegates to {@link AiApiService} and returns the ResponseVO.
 * Authentication is enforced by the shared security chain and authorization by the access interceptor before this
 * class is reached. No provider, credential or prompt logic lives here.
 *
 * <p>Gated by {@link ConditionalOnAiWebSecurity}: even when an application component-scans {@code com.ghostbridge},
 * the controller is not registered while the shared security chain is disabled.
 */
@RestController
@AiApiEndpoint
@ConditionalOnAiWebSecurity
@ConditionalOnAiRestApi
@RequestMapping(GhostBridgeAiLiterals.API_BASE_PATH)
public class AiRestController {
    private final AiApiService service;
    private final AiRequesterResolver resolver;

    public AiRestController(AiApiService service, AiRequesterResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @PostMapping(path = GhostBridgeAiLiterals.PATH_GENERATE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiGenerateResponseVO> generate(@Valid @RequestBody AiGenerateRequestVO request) {
        return noStore(service.generate(resolver.requireRequester(), request));
    }

    @PostMapping(path = GhostBridgeAiLiterals.PATH_GENERATE_STRUCTURED, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiStructuredGenerateResponseVO> generateStructured(@Valid @RequestBody AiStructuredGenerateRequestVO request) {
        return noStore(service.generateStructured(resolver.requireRequester(), request));
    }

    @GetMapping(GhostBridgeAiLiterals.PATH_PROVIDER_STATUS)
    public ResponseEntity<AiProviderStatusResponseVO> providerStatus() {
        resolver.requireRequester();
        return noStore(service.providerStatus());
    }

    @GetMapping(GhostBridgeAiLiterals.PATH_USAGE)
    public ResponseEntity<AiUsageResponseVO> usage() {
        return noStore(service.usage(resolver.requireRequester()));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
