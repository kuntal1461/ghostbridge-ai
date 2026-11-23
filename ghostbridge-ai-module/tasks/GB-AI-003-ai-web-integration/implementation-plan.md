# Implementation Plan — GB-AI-003

## Phases
| Phase | Goal | Deliverables |
|---|---|---|
| A | Task docs | this folder (before any code) |
| B | Core additions for G-M4-2 | `AiException.retryAfter`; `AiLimiter`/`AiTokenBudget` report retry time; literals; tests |
| C | Shared web: expose status mapping | `GlobalExceptionHandler.statusFor` made `public static` (additive) |
| D | G-M4-1 + security (before endpoints) | `AiWebProperties`, `AiRequesterResolver`, `AiRequesterLimiter`, `AiAccessInterceptor`, `AiRequestBodyLimitFilter`; tests |
| E | Contracts and error advice | RequestVO/ResponseVO records, `MaxUtf8Bytes`, `AiApiMapper`, `AiExceptionAdvice`; tests |
| F | Service, controller, auto-config | `AiApiService`/`DefaultAiApiService`, `AiRestController`, `AiWebAutoConfiguration`, imports file |
| G | Integration tests with mock provider | MockMvc tests: controller, security, errors |
| H | Reviews, build, docs | security + quality review reports, `mvn clean verify`, `dependency:tree` |

## Affected modules and files
| Module | Files | Change |
|---|---|---|
| ai-core | `exception/AiException.java`, `service/impl/AiLimiter.java`, `AiTokenBudget.java`, `constants/GhostBridgeAiLiterals.java` + tests | additive |
| springboot-web | `advice/GlobalExceptionHandler.java` | `statusFor` visibility only |
| ai-web | new packages `restcontroller`, `requestvo`, `responsevo`, `mapper`, `service`, `service.impl`, `security`, `filter`, `advice`, `validation`, `config`; resource `META-INF/spring/...AutoConfiguration.imports`; `pom.xml` (test deps); tests + test application | new |
| docs | `doc-architecture/PROJECT_STRUCTURE.md`, `.kiro/steering/ghostbridge-ai-module.md` (only if needed), `GB-AI-001` gate rows | update |

## Technical decisions
| Decision | Alternatives | Reason |
|---|---|---|
| Authorization via MVC interceptor + resolver (role `AI_USER`, configurable), shared chain for authentication | `@PreAuthorize` only | `@EnableMethodSecurity` vanishes if an app supplies its own chain; interceptor runs before body binding so 403 precedes 400. |
| Per-requester limiter lives in ai-web (`security` package), uses `AiException(RATE_LIMITED)` | in ai-core `AiLimiter` | Identity is a Spring Security concept; core keeps its optional `requesterId`. Core is not changed to require it. |
| Limiter: single lock, `HashMap<String,Entry>`, fixed 1-minute window + in-flight counter, max 10 000 tracked requesters, lazy sweep of expired entries; when full and nothing expired → reject new requester with 429 (fail closed) | Caffeine; evict-oldest | No new dependency; eviction would reset an abuser's quota; operations are O(1) next to multi-second LLM calls. **Local to one JVM** — multi-instance deployments need a shared limiter (documented, not claimed). |
| Requester key = SHA-256 hex of `Authentication.getName()` | raw name | Fixed length/charset (passes core validation), no PII in metadata, no delimiter/format collisions. |
| Unknown JSON fields rejected (`@JsonIgnoreProperties(ignoreUnknown=false)` on VOs) | ignore | A spoofed `requesterId` body field becomes a visible 400. |
| Body limit by buffering up to `max+1` bytes in a filter (default 64 KiB) | wrap stream and throw | Deterministic 413 incl. chunked; body small by design. |
| Structured endpoint takes `schemaName`; operator allow-list `ghostbridge.ai.web.structured-schemas` maps schema → registered prompt (default `generic-summary`→`generic-structured`) | accept client schema; add listing API to core | Clients cannot submit schemas; no core interface change. Pairing prompt↔schema is operator-owned (residual risk noted). |
| Usage computed from retained `AiRequestRegistry` metadata filtered by requester key; scope field states "retained history" | new usage store | No new infrastructure; honest bounded semantics. Budget not exposed (global only). |
| `AiExceptionAdvice` with explicit `@Order(HIGHEST_PRECEDENCE)` only for `AiException`; delegates status to shared `statusFor` and body to `ProblemDetailFactory` | extend shared handler | Core-specific `reason`/`Retry-After` stay out of shared framework; no competing handler for other types. |
| Status endpoint returns enum status, never `AiProviderStatus.detail` | return detail | Detail is operator-oriented text. |

## Dependencies (Maven / runtime)
- ai-web: `spring-boot-starter-test` (test), `spring-security-test` (test). Both managed by the Boot BOM; no versions added. Main dependencies unchanged (ai-core, springboot-web, starter-validation).

## Authentication blocker (documented, not worked around)
No authentication mechanism exists. The endpoints are deny-by-default (401 for everyone) until an application registers a `GhostBridgeHttpSecurityCustomizer` (JWT/OIDC/session) that produces authorities including `ROLE_AI_USER`. Tests use `spring-security-test` principals; security is never disabled.

## Rollback
Remove the ai-web classes and imports file; revert the additive core/shared changes (each is a separate small diff). No data migration.
