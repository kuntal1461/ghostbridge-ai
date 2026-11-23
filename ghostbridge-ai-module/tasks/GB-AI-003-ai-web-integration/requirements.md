# Requirements — GB-AI-003

## Objective
Expose AI Core through secure, versioned REST endpoints under `/api/v1/ai` in the reusable `ghostbridge-ai-web` library JAR, reusing the centralized Spring Boot security, exception handling and CORS. Gates G-M4-1 (per-requester limits from trusted identity) and G-M4-2 (limits, HTTP mapping, Retry-After, `reason`) are complete before the generation endpoints are exposed.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | `POST /api/v1/ai/generate`: authenticate, authorize, validate, resolve trusted requester, apply per-requester limits, delegate to `AiClient`, return `AiGenerateResponseVO`. Only registered prompt templates are invocable (no free-form prompt, no arbitrary model). |
| FR-2 | `POST /api/v1/ai/generate/structured`: client sends an **approved schema identifier** (operator allow-list mapping schema → registered prompt); unknown id → 400; result returned only if core produced schema-validated JSON, else 502. |
| FR-3 | `GET /api/v1/ai/provider/status`: availability, configured model id, capabilities. No credentials, no detail text, no inference call. |
| FR-4 | `GET /api/v1/ai/usage`: caller's own request counts, provider-reported tokens (from retained metadata), own rate-limit status. Budget is not exposed (global only). No cross-user data. |
| FR-5 | HTTP contracts are `*RequestVO` in `com.ghostbridge.requestvo` and `*ResponseVO` in `com.ghostbridge.responsevo` (records); mappers isolate them from core models. |
| FR-6 | Per-requester rate limit and concurrency limit (G-M4-1), configurable, bounded, expiring; global limits remain. |
| FR-7 | HTTP status/`code`/`reason` mapping per table below; `Retry-After` only when the source knows the retry time. |

### HTTP mapping (G-M4-2)
| Scenario | Status | `code` | Notes |
|---|---|---|---|
| Invalid input, unknown schema/prompt, unknown JSON field, malformed JSON | 400 | VALIDATION_ERROR / INVALID_INPUT / MALFORMED_REQUEST | |
| Unauthenticated | 401 | AUTHENTICATION_REQUIRED | shared entry point |
| Missing AI role | 403 | ACCESS_DENIED | |
| Body or field too large (bytes) | 413 | PAYLOAD_TOO_LARGE | body filter and `AiException(INPUT_TOO_LARGE)` |
| Per-requester or global rate/concurrency limit | 429 | RATE_LIMITED | `Retry-After` for rate windows only |
| Token budget exhausted | 429 | QUOTA_EXCEEDED, reason BUDGET_EXHAUSTED | `Retry-After` = budget window remainder (known) |
| Invalid/oversized model output, provider failure | 502 | UPSTREAM_ERROR | |
| Provider unavailable / not configured | 503 | UPSTREAM_UNAVAILABLE | no Retry-After (unknown) |
| Provider timeout | 504 | UPSTREAM_TIMEOUT | |
| Unexpected | 500 | INTERNAL_ERROR | generic message |

`reason` = `AiErrorReason` name, except configuration/credential/interrupted reasons which are reported as `PROVIDER_UNAVAILABLE` so configuration state is not disclosed.

## Non-functional requirements
| ID | Requirement |
|---|---|
| NFR-1 | Java 21, Boot 3.5, constructor injection, cognitive complexity ≤ 15 (target ≤ 10), no static/wildcard imports of literals, one public type per file. |
| NFR-2 | Library JAR only; registered through `AutoConfiguration.imports`; `@ConditionalOnMissingBean` everywhere. |
| NFR-3 | No new Maven dependency beyond test-scope `spring-boot-starter-test` and `spring-security-test` (versions managed by the Boot BOM). No AWS SDK. |
| NFR-4 | Limiter memory bounded (max tracked requesters) with deterministic expiry; thread-safe. |

## Security requirements
| ID | Requirement |
|---|---|
| SEC-1 | Requester identity comes only from the Spring Security `Authentication`; body fields/headers are ignored (unknown body fields rejected). Identity is mapped to a fixed-format SHA-256 key (no raw name in metadata, no collisions via user-controlled formatting). |
| SEC-2 | Every `/api/v1/ai/**` request needs an authenticated, non-anonymous principal holding the configured role (default `AI_USER`), enforced in the shared chain (deny by default) **and** by a MVC interceptor that does not depend on method security. |
| SEC-3 | If `ghostbridge.security.enabled=false` the AI web endpoints are not registered. |
| SEC-4 | Body limit enforced on actual bytes (default 64 KiB), UTF-8 byte limits on string fields, bounded map sizes, key patterns. |
| SEC-5 | Responses never contain credentials, provider headers, provider detail text or config values. `Cache-Control: no-store`. |
| SEC-6 | CORS only via the shared configuration; CSRF stays on when `stateless=false`. |

## Out of scope
AI Frontend; App business logic; distributed (multi-instance) limiter; per-requester token budget; live Ollama calls; Sonar server and vulnerability scanner runs unless installed; authentication mechanism itself (blocker, documented).

## Acceptance criteria
- [ ] G-M4-1 implemented and tested before the generate endpoints are registered.
- [ ] G-M4-2: 400/401/403/413/429/502/503/504/500 verified with code, `reason`, correct `Retry-After`.
- [ ] All endpoints reject unauthenticated callers; spoofed requester body field/header has no effect.
- [ ] `mvn clean verify` passes from root; dependency tree shows no cycle, no AWS SDK, no ai-web→OllamaAiProvider coupling.
- [ ] Independent security and quality reviews run; no unresolved Critical/High.
