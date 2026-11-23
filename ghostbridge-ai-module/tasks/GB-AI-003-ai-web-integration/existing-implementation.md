# Existing Implementation — GB-AI-003 (Milestone 4: Secure AI Web & REST API)

## Scope analysed
`rules/rules.md`, `rules/claude/CLAUDE.md`, `doc-architecture/PROJECT_STRUCTURE.md`, GB-AI-001/002 task folders (gates G-M3-1, G-M4-1, G-M4-2), `ghostbridge-springboot-core` and `-web` sources and tests, `ghostbridge-ai-core` sources and tests, the `ghostbridge-ai-web` scaffold, `ghostbridge-app-web/pom.xml`, Maven POMs.

## Confirmed status (verified against the repository, 2026-11-23)
- M2 (AI Core foundation) complete; M3 (Ollama Cloud provider) complete, **stub-tested, not live-verified**. No real Ollama Cloud request has been made in this task either.
- `ghostbridge-ai-web` is a scaffold: a POM (deps: ai-core, springboot-web, starter-validation) and `.gitkeep` files only. No Java, no tests.
- G-M4-1 (per-requester limits from trusted identity) and G-M4-2 (`Retry-After`, body/DTO bounds, `reason` exposure, `AiRequest` construction errors mapped to 400/413) are open and block M4 completion (`GB-AI-001/tasks.md:96-97`).

## Current behaviour relevant to M4
| Area | Today |
|---|---|
| Orchestration | `AiClient.generate(AiRequest)` → `DefaultAiCoordinator`: validates, resolves provider/model, renders a **registered, versioned prompt template** (no free-form prompt), guard (global rate+concurrency, global token budget, bounded retry), schema validation when the prompt declares a schema, telemetry into a bounded `AiRequestRegistry` (metadata only). |
| Request model | `AiRequest(promptName, promptVersion, variables, untrustedContent, modelId, options, requesterId)`; constructor throws `AiException` (variable count/name/null, requester-id length/control chars). `requesterId` is documented as "must come from a trusted authentication context". |
| Errors | `AiException extends GhostBridgeException`; `AiErrorReason` → `ErrorCode` (INVALID_INPUT 400, INPUT_TOO_LARGE→PAYLOAD_TOO_LARGE 413, RATE_LIMITED 429, BUDGET_EXHAUSTED→QUOTA_EXCEEDED 429, INVALID_MODEL_RESPONSE/OUTPUT_VALIDATION_FAILED/RESPONSE_TOO_LARGE/PROVIDER_FAILURE→UPSTREAM_ERROR 502, unavailable/misconfigured→503, timeout→504). Message is client-safe. **No retry-after metadata and no `reason` exposure.** |
| Limits | `AiLimiter` (global fixed 1-minute window + semaphore, rejects, never queues) and `AiTokenBudget` (global window). Neither knows a requester; neither reports when to retry. |
| Spring Boot Web | `GlobalExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`, ProblemDetail + `code`, `correlationId`, `timestamp`; `statusFor(ErrorCode)` is private and exhaustive), shared `SecurityFilterChain` (deny by default, JSON 401/403, CSP/HSTS/frame headers, central CORS, CSRF off only when `stateless=true`), `RequestSizeLimitFilter` (declared Content-Length only, 12 MB default, uses code INVALID_INPUT), `CorrelationIdFilter`, `ProblemResponseWriter`. |
| Authentication | **No authentication mechanism is configured** anywhere; apps plug one in through `GhostBridgeHttpSecurityCustomizer`. Today every non-public path answers 401. |
| Method security | `@EnableMethodSecurity` exists only inside the shared security auto-configuration; it disappears if an app supplies its own chain. |
| App | `ghostbridge-app-web` already depends on `ghostbridge-ai-web`; it has no authentication mechanism either. |
| Provider status | `AiProvider.status()` is local (no network, no paid call) for both providers. |
| Usage data | `AiRequestRegistry.recent(n)` holds metadata incl. `requesterId`, `usage` (provider-reported tokens), status; bounded by `historyMaxEntries`. No per-requester or budget accessor exists. |

## Functionality that must be preserved
- Mock and Ollama providers, `DefaultAiCoordinator`, global limiter/budget/retry behaviour and all existing tests (springboot-core 31, springboot-web 55, ai-core 269 + 1 skipped live test).
- `GlobalExceptionHandler` remains the single global handler; no second default `SecurityFilterChain`; CORS stays central.
- AI Core never depends on AI Web; AI Web never touches `OllamaAiProvider`.

## Gaps and risks found
- No trusted identity source exists until an application plugs authentication in (integration blocker, see implementation-plan).
- Shared 413 filter checks only the declared Content-Length; chunked bodies are unmeasured.
- Shared handler cannot add `Retry-After` or `reason`; core exceptions carry no retry metadata.
- Global limiter lets one caller exhaust everyone's quota (F-04).
- Only one prompt/schema exists (`generic-structured` / `generic-summary`).

## Evidence
Files listed above read on 2026-11-23; `mvn -v` → Maven 3.9.11 (wrapper dist in `~/.m2`), JDK 25 default, JDK 21 installed.
