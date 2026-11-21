# Implementation Plan — GB-AI-001 (AI Framework Setup)

Kiro spec mirror: [.kiro/specs/ghostbridge-ai/design.md](../../../.kiro/specs/ghostbridge-ai/design.md) and [tasks.md](../../../.kiro/specs/ghostbridge-ai/tasks.md). This folder is the execution record; if they disagree, fix both in the same change.

## Proposed components (all under `com.ghostbridge`)

### ghostbridge-ai-core
| Package | Type | Responsibility |
|---|---|---|
| `dto` | `AiRequest` (record) | `promptName`, `promptVersion?`, `variables` (Map), `untrustedContent?`, `modelId?`, `options?`, `responseSchemaName?` |
| `dto` | `AiGenerationOptions` (record) | `maxOutputTokens?`, `temperature?` — validated against configured ceilings |
| `dto` | `AiResponse` (record) | `requestId`, `provider`, `model`, `text`, `structured` (JsonNode, nullable via Optional accessor), `usage`, `durationMs` |
| `dto` | `AiUsage` (record) | `inputTokens`, `outputTokens`, `totalTokens` as `OptionalInt`-style; `estimatedCost?` only if pricing configured; never fabricated |
| `dto` | `AiRequestMetadata` (record) | id, correlationId, provider, model, promptName/version, status, errorCode, durationMs, usage, timestamp — **no content** |
| `service` | `AiClient` | Public contract: `AiResponse generate(AiRequest)` |
| `service` | `AiProvider` | Provider SPI: `String name()`, `AiProviderResult invoke(AiProviderCall)`, `AiProviderStatus status()` |
| `service` | `AiCoordinator` | Orchestration entry (extends/implements `AiClient`) |
| `service` | `AiPromptService` | Lookup + render prompt by name/version |
| `service` | `AiStructuredOutputService` | Parse + schema-validate model output |
| `service` | `AiRequestRegistry` | Bounded in-memory store of `AiRequestMetadata` |
| `service.impl` | `DefaultAiCoordinator` | Pipeline implementation |
| `service.impl` | `MockAiProvider` | Deterministic provider (response derived from a hash of prompt; configurable canned output; failure-injection hooks for tests only through test-scope helpers) |
| `service.impl` | `BedrockAiProvider` | Bedrock Converse adapter; maps SDK exceptions → typed errors |
| `service.impl` | `ClasspathAiPromptService` | Loads `classpath:prompts/<name>/<version>.json` (system text, user template, declared variables, optional schema ref) |
| `service.impl` | `JsonSchemaAiStructuredOutputService` | Jackson + JSON-Schema validation |
| `service.impl` | `AiRateLimiter` / `AiConcurrencyLimiter` (or one `AiLimiter`) | Fixed-window request limit + `Semaphore` concurrency; fail-fast |
| `service.impl` | `AiRetryExecutor` | Bounded retry with backoff on retryable typed errors |
| `service.impl` | `AiTokenBudget` | Optional rolling token budget, only counts reported usage |
| `service.impl` | `AiMetricsRecorder` | Micrometer (optional `ObjectProvider<MeterRegistry>`) + SLF4J safe summary line |
| `config` | `AiProperties` (record, prefix `ghostbridge.ai`) | provider, models allowlist, defaults, limits, timeouts, retry, rate-limit, budget, pricing, structured-output, `bedrock.region` |
| `config` | `AiAutoConfiguration` | Beans, `@ConditionalOnMissingBean`, provider selection by `ghostbridge.ai.provider` |
| `config` | `AiProductionValidator` | Fails prod startup if provider=`mock`, no model, etc. (same style as `GhostBridgeProductionValidator`) |
| `exception` | `AiException extends GhostBridgeException` + subclasses (`AiInvalidInputException`, `AiConfigurationException`, `AiCredentialsException`, `AiProviderUnavailableException`, `AiProviderTimeoutException`, `AiRateLimitException`, `AiInvalidResponseException`, `AiOutputValidationException`, `AiLimitExceededException`) | Client-safe messages only; provider detail kept in `cause`, never in `getMessage()` |
| `util` | `AiTextSanitizer` | Delimiting untrusted content, control-character stripping, optional redaction |
| resources | `prompts/**`, `ai-schemas/**`, `META-INF/spring/…AutoConfiguration.imports`, `ghostbridge-ai-defaults.properties` (non-secret) | |

### ghostbridge-ai-web
| Package | Type | Responsibility |
|---|---|---|
| `restcontroller` | `AiRestController` | `/api/v1/ai/capabilities`, `POST /generate`, `GET /requests`, `GET /requests/{id}`, `POST /mock/test` — validate, delegate, map |
| `dto` | `AiGenerateRequestDto`, `AiGenerateResponseDto`, `AiCapabilitiesDto`, `AiRequestMetadataDto`, `AiMockTestRequestDto` | Bean-validated HTTP DTOs; mapping in a small `AiWebMapper` (`util`) |
| `advice` | `AiExceptionHandler` | `@Order(HIGHEST_PRECEDENCE)`, handles `AiException` only → `ProblemDetail` with HTTP status per error; leaves everything else to shared `GlobalExceptionHandler` |
| `config` | `AiWebAutoConfiguration` | Registers controller/advice via `AutoConfiguration.imports`; no security/CORS beans |
| security | `@PreAuthorize("hasRole('AI_USER')")` etc. on controller methods | Uses shared method security; role names from `ghostbridge.ai.security.*` |

### ghostbridge-ai-frontend (React + TypeScript)
`src/api/aiClient.ts` (env `VITE_AI_API_BASE_URL`, `Authorization` header supplied by the host app's token provider — no own auth), `src/components/*`, `src/views/{AiOverview,AiPlayground,AiStructuredResponseViewer,AiRequestHistory}.tsx`, session history in React state only.

## Technical design decisions
| # | Decision | Rationale |
|---|---|---|
| D1 | `AiClient` = public contract, `AiCoordinator` implements it; providers behind `AiProvider` SPI | Satisfies FR-1/2; callers never see providers |
| D2 | Bedrock **Converse** API | One request shape across models; token usage returned; fewer per-model adapters |
| D3 | Prompts as JSON resource files `prompts/<name>/<version>.json` | Simple, versioned, reviewable; no DB (MVP) |
| D4 | JSON Schema validation with `com.networknt:json-schema-validator`; schemas in `ai-schemas/` resources | Real schema validation without hand-rolled validator; version added to springboot POM |
| D5 | Error codes: add domain-neutral codes `RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`, `UPSTREAM_TIMEOUT`, `UPSTREAM_ERROR` to shared `ErrorCode` + `statusFor` (429/503/504/502). AI-specific reason stays in `AiException` subclass and a `reason` ProblemDetail property | Keeps AI vocabulary out of springboot while giving correct HTTP statuses. **Needs user confirmation before editing `ghostbridge-springboot`** (see open questions). Fallback if declined: AI-only advice builds ProblemDetail with `INTERNAL_ERROR`/`BUSINESS_RULE_VIOLATION` codes and explicit statuses. |
| D6 | In-house limiter (fixed window + `Semaphore`) and retry executor | Avoids Resilience4j for MVP; small, testable |
| D7 | Micrometer optional in core via `ObjectProvider<MeterRegistry>`; core adds only `micrometer-core` (managed by Boot BOM) | Core stays usable without actuator; web already has actuator |
| D8 | Request registry in memory, bounded, metadata only | Meets "basic metadata where securely available"; no sensitive content persisted |
| D9 | Authorization by roles `AI_USER` (generate, capabilities, history) and `AI_ADMIN` (mock test); history filtered to caller's own requests unless admin | Least privilege; prevents cross-user leakage |
| D10 | Untrusted content passed as a separate, delimited user-content block; system prompt never templated with user text | Prompt-injection structural control (not the only control: output is validated and never executed) |
| D11 | Swagger/OpenAPI via springdoc is **deferred** (architecture.md lists OpenAPI as deferred); controllers carry Javadoc and the contract is documented in `implementation.md`. Revisit only on user request | Avoid new dependency + public docs endpoint in a security-first stack |
| D12 | Frontend uses Vite + Vitest + ESLint (dev dependencies in the AI frontend `package.json` only) and adds `build`, `lint`, `test` scripts | Required by verification commands; scripts do not exist today |
| D13 | Live provider tests tagged `@Tag("live-provider")`, excluded by default via Surefire `excludedGroups` configured in the ai-module POM, enabled with `-Dlive.provider=true` | Deterministic default build |
| D14 | Provider calls bounded by `Semaphore` + per-call timeout; SDK-level retries disabled | One retry policy, no retry storms |

## Dependency changes (all versions in `ghostbridge-springboot/pom.xml`)
| Where | Change |
|---|---|
| `ghostbridge-springboot/pom.xml` | Add `software.amazon.awssdk:bom` import (version property) and `com.networknt:json-schema-validator` managed version. (Cross-project change; documented in impact-analysis.) |
| `ghostbridge-ai-core/pom.xml` | + `software.amazon.awssdk:bedrockruntime` (exclude Netty async client, keep `apache-client` sync), `json-schema-validator`, `micrometer-core`, `spring-boot-starter-test` (test) |
| `ghostbridge-ai-web/pom.xml` | + `spring-boot-starter-test`, `spring-security-test` (test) |
| `ghostbridge-ai-module/pom.xml` | Surefire `excludedGroups=live-provider` (if not placed in springboot pom; plugin config prefers springboot pom per rules §4) |
| `ghostbridge-ai-frontend/package.json` | + react-dom, vite, @vitejs/plugin-react, vitest, @testing-library/react, jsdom, eslint + typescript-eslint; scripts `build`, `lint`, `test` |

## Implementation milestones
| # | Milestone | Key deliverables | Exit criteria |
|---|---|---|---|
| M1 | Discovery and planning | This folder, Kiro spec, steering, hooks plan, reviewer-prompt plan | Docs reviewed by user; **no code written** |
| M2 | AI Core foundation | DTOs, `AiProperties`, errors, `AiClient/AiProvider/AiCoordinator`, prompts, structured output, limiter/retry, mock provider, unit tests | `mvn -pl ghostbridge-ai-module/ghostbridge-ai-core -am verify` green, coverage ≥ 80 % new code |
| M3 | Real provider | `BedrockAiProvider`, credentials/readiness, timeouts, bounded retry, mapped errors, tagged live test | Mocked-SDK tests green; live test "not run" unless credentials |
| M4 | AI Web | Controller, DTOs, advice, auth annotations, tests | Web tests for 200/400/401/403/413/429/50x green |
| M5 | AI Frontend | Overview, Playground, Viewer, History, API client, tests | `npm run build`, `lint`, `test` green |
| M6 | Review and hardening | Security + quality reviews by separate agents, fixes, re-review, full build | No open Critical/High; `mvn clean verify` green |
| M7 | App readiness | Contract doc, demonstration test of structured request through `AiClient`, integration examples | Documented and tested; no SOAP logic added |

## Risks and rollback
| Risk | Mitigation |
|---|---|
| Edits to shared `ghostbridge-springboot` (POM, `ErrorCode`) affect other projects | Additive only; run full root build and springboot tests; separate commit; revert is a single commit |
| Bedrock access/credentials unavailable | Mock-first; mocked-SDK tests; live test opt-in; readiness reports "not configured" |
| Local JDK 25 vs target 21 | Rely on `maven.compiler.release=21` from Boot parent; confirm in M2 build |
| AWS SDK footprint / duplicate HTTP stack | Sync apache client only; exclusions documented; checked with `dependency:tree` |
| Over-engineering | MVP limits: one real provider, in-memory registry, no DB, no streaming, no springdoc |
| Prompt injection cannot be fully prevented | Defence in depth (structure, validation, no execution, authorization); documented as residual risk |
| Frontend tooling absent | M5 adds only the minimum toolchain, recorded in implementation.md |

**Rollback:** each milestone is committed separately; reverting a milestone's commit removes its classes, POM edits and docs. The shared-module change (D5, POM) is in its own commit.

## Open questions for the user (blocking only where stated)
1. Approve D5 (additive edit to `ErrorCode`/`GlobalExceptionHandler` in `ghostbridge-springboot`) — blocks M2 error design; default = approve.
2. Is Bedrock access (region, model access, AWS credentials) available for M3 live verification? Default = assume not.
3. Which Bedrock model id and region should be the default approved model? Default = configuration only, no hard-coded id.

## M2 amendments (2026-11-21, user approval received)
- **D5 approved:** additive `ErrorCode` codes `RATE_LIMITED`(429), `UPSTREAM_UNAVAILABLE`(503), `UPSTREAM_TIMEOUT`(504), `UPSTREAM_ERROR`(502) + `GlobalExceptionHandler.statusFor`; regression tests for existing and new mappings; **no duplicate exception handler in the AI module** (the planned `AiExceptionHandler` in M4 is dropped; `AiException` maps through the shared handler).
- **Bedrock not confirmed:** M2 implements no Bedrock adapter, adds **no AWS SDK dependency, no paid calls**. Architecture is prepared (`AiProvider` SPI, `AiProperties.aws.region` validation when `provider=bedrock`). The Bedrock adapter and AWS BOM are deferred to M3.
- **Configuration keys (fixed by user):** `ghostbridge.ai.provider`, `ghostbridge.ai.model-id`, `ghostbridge.ai.aws.region`, `ghostbridge.ai.max-output-tokens`, `ghostbridge.ai.temperature`, `ghostbridge.ai.timeout`. No region/model hard-coded. Required values depend on provider: `bedrock` requires `model-id` and `aws.region`; `mock` needs nothing (defaults to model `mock-model`). Provider unset → AI disabled (no silent mock). `max-output-tokens` is both default and ceiling; `temperature` is the default (request may override within 0–1). Additional keys (`approved-model-ids`, `max-input-length`, `max-response-size`, `retry.*`, `rate-limit.*`, `budget.*`, `pricing.*`, `mock.response`, `redaction-patterns`, `history.max-entries`) have restrictive defaults.
- **Simplifications vs. plan (documented deviations):** one `AiException` with an `AiErrorReason` enum (carrying `ErrorCode` + retryable flag) instead of nine subclasses; `DefaultAiCoordinator implements AiClient` (no separate `AiCoordinator` interface); schema is chosen by the prompt descriptor, not by the request; `AiLimiter` combines rate limit and concurrency; dependencies added in M2 are only `json-schema-validator` (version in springboot POM), `micrometer-core` and test starters. Surefire live-test exclusion is deferred to M3 (no live tests exist in M2).
- **Process order (user):** interfaces/models → properties → mock provider → coordinator → exceptions → unit tests → security review agent → quality review agent → fix findings → update docs.

## M2 amendment 2 — literals class (user request, 2026-11-21)
Add `com.ghostbridge.constants.GhostBridgeAiLiterals` (`public final class`, private constructor) in `ghostbridge-ai-core` holding reusable AI string literals, compiled regex patterns, prompt/untrusted-content delimiters, resource locations, provider identifiers, metric names/tags and fixed numeric constants; replace hard-coded references in main and test code. Out of scope for the class (stay as configuration or local): runtime configuration values, credentials, model settings (`DEFAULT_MOCK_MODEL` stays in `AiProperties`), client-facing exception messages, log format strings. Behavior and security semantics must be unchanged (verified by the existing 87 ai-core tests plus a new literals test). `constants` is added to the allowed functional packages in `PROJECT_STRUCTURE.md` in the same change (rules §4).

## M2 remediation plan (user-approved 2026-11-21; supersedes "Open" statuses in the first review reports)

Findings were re-read from `security-review.md` (F-01…F-16) and `code-quality-review.md` (CQ-01…CQ-26). Line numbers in those reports predate the literals refactor and are re-resolved against current source in the follow-up reviews. Key findings are revalidated by writing adversarial tests first and confirming they fail on the current code.

### Error contract (decision, replaces earlier `LIMIT_EXCEEDED` → 422)
| Situation | `AiErrorReason` | Shared `ErrorCode` | HTTP |
|---|---|---|---|
| Invalid input / invalid options (incl. null variables, bad requester id, option above ceiling) | `INVALID_INPUT` | `INVALID_INPUT` | 400 |
| Oversized input (length, variable count, variable-name length) | `INPUT_TOO_LARGE` | `PAYLOAD_TOO_LARGE` (new) | 413 |
| Per-window request-rate or concurrency limit | `RATE_LIMITED` | `RATE_LIMITED` | 429 |
| Provider throttled us (transient, retried) | `PROVIDER_THROTTLED` | `RATE_LIMITED` | 429 |
| Token-budget exhausted (**policy status**: server-side quota, not a caller error and not a provider fault; the caller may retry after the budget window) | `BUDGET_EXHAUSTED` | `QUOTA_EXCEEDED` (new) | 429 with distinct `code` |
| Invalid / empty / malformed model output, schema failure | `INVALID_MODEL_RESPONSE`, `OUTPUT_VALIDATION_FAILED` | `UPSTREAM_ERROR` | 502 |
| Model response larger than configured maximum | `RESPONSE_TOO_LARGE` | `UPSTREAM_ERROR` | 502 |
| Unexpected provider/runtime failure | `PROVIDER_FAILURE` | `UPSTREAM_ERROR` | 502 |
| Provider unavailable / missing config / missing credentials / interrupted | `PROVIDER_UNAVAILABLE`, `MISSING_CONFIGURATION`, `MISSING_CREDENTIALS`, `INTERRUPTED` | `UPSTREAM_UNAVAILABLE` | 503 |
| Provider timeout | `PROVIDER_TIMEOUT` | `UPSTREAM_TIMEOUT` | 504 |
Shared change (additive): `ErrorCode.PAYLOAD_TOO_LARGE`→413, `ErrorCode.QUOTA_EXCEEDED`→429; `GlobalExceptionHandler.statusFor` becomes an explicit exhaustive switch (no `default`). `reason` exposure in ProblemDetail stays an M4 decision (shared handler must not learn AI types).

### Disposition of findings
| Finding | Disposition |
|---|---|
| F-01 trailing JSON / fence handling | **Fix:** `FAIL_ON_TRAILING_TOKENS`, strict duplicate-key detection, strict whole-text fence stripping; `AiResponse.text` becomes the canonical validated JSON when a schema applies |
| F-02 variables undelimited | **Fix:** each rendered variable is sanitized and wrapped in the untrusted-data delimiters; generic prompt reworded to process supplied text (variable `text`) |
| F-03 delimiter bypass | **Fix (defence in depth, not a guarantee):** NFKC + format-char (Cf) + U+2028/2029 handling on a detection copy, whitespace/underscore/hyphen-tolerant delimiter match; adversarial tests. Residual: homoglyphs outside NFKC (e.g. Cyrillic look-alikes) remain possible. Delimiting is one layer only; the real controls are untrusted-data handling, no model capabilities/tools, schema-validated output and no automatic execution |
| F-04 per-requester limits | **Deferred — MANDATORY GATE G-M4-1** (see below). Global limits stay enabled as secondary safeguard |
| F-05 retry amplification / CQ-09 deadline | **Documented** (timeout is per attempt; worst-case in `AiProperties` Javadoc). Revisit in M3 with provider adapter |
| F-06 / CQ-06 budget race, negative usage | **Fix:** negative usage → unknown; overshoot bound documented; saturating total |
| F-07 / CQ-10 / F-15 duplicates, remote `$ref` | **Fix:** fail on duplicate schema and provider names; reject remote `$ref` in schemas |
| F-08 reflected model keys | **Fix:** report schema evaluation paths only |
| F-09 / CQ-02 non-AiException | **Fix:** wrap as `PROVIDER_FAILURE`, record FAILURE telemetry |
| F-10 mock guard | **Fix:** mock allowed only when `local`, `dev` or `test` is active **and** `prod` is not active (mixed profiles cannot bypass); validator tests |
| F-11 / CQ-03 config bounds | **Fix:** timeout, backoff, window, pricing, rate bounds with startup tests |
| F-12 correlation/requester id | **Fix:** MDC correlation id validated with the shared filter's pattern; requester id length/control-char validated. Requester identity must come from trusted authentication context (see G-M4-1) |
| F-13 ReDoS in redaction patterns | **Documented** (operator-supplied; input already length-bounded) |
| F-14 partial size bounds | **Fix (core part):** variable count and name length bounded. **Deferred:** provider byte limits → GATE G-M3-1; HTTP body/DTO bounds → M4 |
| F-16 `Retry-After`, status contract | Status contract **fixed** above; `Retry-After` → M4 |
| CQ-01 null variables | **Fix** (typed `INVALID_INPUT`) |
| CQ-04 fail-fast untested | **Fix:** injectable resource patterns + fixtures + tests |
| CQ-05 status mapping | **Fix** (table above) |
| CQ-07 permit idempotence, CQ-08 interrupt reason | **Fix** |
| CQ-11 duplicate length limit | **Fix:** remove per-variable limit from prompt service (coordinator owns it) |
| CQ-12 DI overridability | **Fix:** separate `@ConditionalOnMissingBean` beans |
| CQ-13 explicit switch | **Fix** |
| CQ-14/15/16 tests | **Fix** (bean-validation startup tests, root-cause assertions, concurrency test) |
| CQ-17…CQ-25 | **Fix** where cheap (throws clause, imports, join, normalize, constants, cached prompt names); CQ-26 naming clarified in steering |
| CQ-18 Javadoc | **Fix** on public contracts and entry points |

### Deferred security gates (mandatory)
- **G-M3-1 (before any real provider is enabled):** enforce provider byte-level limits — cap bytes read from the provider response while streaming/reading, pass `maxOutputTokens` to the provider, enforce request byte size; add tests with oversize provider responses.
- **G-M4-1 (before exposing any AI Web endpoint):** per-requester rate, concurrency and token-budget buckets (bounded, expiring key map) keyed by identity taken from the trusted authentication context (`SecurityContext` principal), never from a client-supplied field; global limits remain as secondary safeguard; test that requester A exhausting quota does not block B and that a client-supplied requester id is ignored.
- **G-M4-2:** `Retry-After` on 429, HTTP body/variable-count bounds in DTOs, decision on exposing `reason`.
