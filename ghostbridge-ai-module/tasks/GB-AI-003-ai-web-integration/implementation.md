# Implementation — GB-AI-003

Record of what was actually done (2026-11-23).

## Milestone A: Task docs
- Inspected repository and wrote the planning docs before any code.

## Milestone B: Core retry metadata (G-M4-2 prerequisite)
- `AiException` gains an optional `retryAfter` (`getRetryAfter(): Optional<Duration>`); constructors without it are unchanged, non-positive values count as unknown.
- `AiLimiter` reports the remaining rate window on rate rejection (not for concurrency rejection: unknown). `AiTokenBudget` reports the remaining budget window.
- Files: `exception/AiException.java`, `service/impl/AiLimiter.java`, `service/impl/AiTokenBudget.java`; tests `AiExceptionTest`, `AiLimiterTest`, `AiTokenBudgetTest`.

## Milestone C: Security building blocks (G-M4-1) — delivered before any endpoint
- `AiWebProperties` (`ghostbridge.ai.web.*`): `required-role` (AI_USER), `max-request-bytes` (65536), `requester-limit.{requests-per-minute 20, max-concurrent 2, max-tracked-requesters 10000}`, `structured-schemas` (default `generic-summary → generic-structured`).
- `AiRequesterResolver`: identity only from `SecurityContextHolder`; rejects null/unauthenticated/anonymous/blank-name (→ 401) and callers without `ROLE_<required-role>` (→ 403); key = SHA-256 hex of the name.
- `AiRequesterLimiter`: per-requester fixed one-minute window + in-flight cap, single lock, bounded map (max tracked), lazy sweep once per window and when full, fail-closed with a known retry time when full of live entries; `status()` never allocates state. Reuses the core `AiLimiter.Permit`. **Local to one JVM.**
- `AiAccessInterceptor` on `/api/v1/ai/**`: authorization before body binding (independent of method security).
- `AiRequestBodyLimitFilter` on `/api/v1/ai/*`: declared Content-Length check plus bounded read of `max+1` bytes (covers chunked), 413 `PAYLOAD_TOO_LARGE`, replays the body.
- `GhostBridgeAiLiterals`: web constants (paths, header name, vocab, regex, bounds). `GlobalExceptionHandler.statusFor` made `public static`.

## Milestone D: Contracts, advice, service, controller, auto-configuration
- RequestVO (`com.ghostbridge.requestvo`): `AiGenerateRequestVO`, `AiStructuredGenerateRequestVO`, `AiGenerationOptionsRequestVO`. ResponseVO (`com.ghostbridge.responsevo`): `AiGenerateResponseVO`, `AiStructuredGenerateResponseVO`, `AiProviderStatusResponseVO`, `AiUsageResponseVO`, `AiRateLimitResponseVO`, `AiTokenUsageResponseVO`. All records. No requester field exists in any request; unknown JSON fields are rejected (`@JsonAnySetter` throws) — `@JsonIgnoreProperties(ignoreUnknown=false)` does **not** enforce this under Spring Boot's `FAIL_ON_UNKNOWN_PROPERTIES=false`, found and fixed during design.
- `MaxUtf8Bytes` + validator (allocation-free UTF-8 byte count). Bounds: 16384 bytes per variable value, 32768 bytes untrusted content, ≤ 32 variables, name pattern, prompt version ≤ 1000, options bounded; core re-validates against configured maximums.
- `AiApiMapper` (VO ↔ core models), `AiApiService`/`DefaultAiApiService` (per-requester permit → `AiClient`; structured: allow-list lookup, 400 if unapproved, 502 if core returned no validated structure; provider status from the `AiProvider` SPI only; usage from the retained `AiRequestRegistry` filtered by the caller's key + own limiter status).
- `AiRestController` (thin; `Cache-Control: no-store`), `AiExceptionAdvice` (scoped to the controller, `@Order(HIGHEST_PRECEDENCE)`: AiException → status via shared `statusFor`, `reason`, `Retry-After`; validation errors → 413 for size violations else 400), `AiWebAutoConfiguration` + `AutoConfiguration.imports`.
- **Decision found by testing:** a scanned `@RestController` was registered even with `ghostbridge.security.enabled=false` (apps scan `com.ghostbridge`). Fixed with the composed `@ConditionalOnAiWebSecurity`, applied to the auto-configuration, the controller and the advice.
- Reason masking: `MISSING_CONFIGURATION`, `MISSING_CREDENTIALS`, `INTERRUPTED` → `reason=PROVIDER_UNAVAILABLE`, detail "AI provider is unavailable".
- POM: ai-web gets test-scoped `spring-boot-starter-test`, `spring-security-test`, and optional `spring-boot-configuration-processor` (all BOM-managed; no versions added).
- `PROJECT_STRUCTURE.md` updated with the new ai-web packages.

## Known limitations (stated, not hidden)
- No authentication mechanism exists in the platform: endpoints return 401 to everyone until an application registers a `GhostBridgeHttpSecurityCustomizer` that yields `ROLE_AI_USER`.
- Per-requester limits are in-memory, per JVM. N instances ⇒ N× the limit; a distributed limiter is needed and is **not** implemented.
- No per-requester token budget; the token budget stays global and is not shown to callers.
- `Retry-After` is absent for provider throttling/unavailability (the Ollama provider does not surface upstream retry hints) and for concurrency rejections.
- Usage reflects only the bounded retained history (default 200 entries across all callers); busy systems evict older entries.
- Structured prompt↔schema pairing is operator configuration and is not cross-checked at startup.
- Nothing was run against Ollama Cloud.
