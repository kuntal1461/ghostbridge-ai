# Requirements — GB-AI-001 (AI Framework Setup)

Kiro spec mirror: [.kiro/specs/ghostbridge-ai/requirements.md](../../../.kiro/specs/ghostbridge-ai/requirements.md) (EARS form of the same requirements; this file is authoritative for status and acceptance).

## Scope
An isolated, reusable AI subsystem: `ghostbridge-ai-core` (logic), `ghostbridge-ai-web` (REST), `ghostbridge-ai-frontend` (developer dashboard). MVP first. No SOAP/WSDL/migration business logic.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | Provider-neutral `AiClient.generate(AiRequest) → AiResponse`; request/response are immutable records with no GhostBridge App types. |
| FR-2 | Provider-adapter SPI; adding a provider requires no change to calling services. |
| FR-3 | Deterministic `mock` provider for local/test; real provider: Amazon Bedrock (Converse API). Only these two are implemented. |
| FR-4 | Provider and model selection by configuration; request may select only models on an approved allowlist. |
| FR-5 | `AiCoordinator` pipeline: validate → resolve provider/model → select prompt → apply options → invoke → handle errors → validate structured output → return normalized result + usage metadata. |
| FR-6 | Versioned, named prompt templates loaded from classpath resources, with system instructions, declared variables, variable validation, safe rendering, reusable structured-output instructions. No large prompts in Java. |
| FR-7 | Structured output: JSON parse, JSON-Schema validation, required-field validation, response-size limit; malformed output yields a clear error that never contains prompt text. |
| FR-8 | Typed errors for: invalid input, missing provider configuration, missing credentials, provider unavailable, provider timeout, rate limited, invalid model response, structured-output validation failure, limit exceeded. |
| FR-9 | Tracing/observability: correlation id, provider/model, duration, status, token usage (when reported), safe diagnostics, Micrometer metrics when a `MeterRegistry` exists. |
| FR-10 | Cost safeguards: max output tokens, max input length, concurrency limit, request-rate limit, optional token budget, usage tracking, optional cost estimate **only** from explicitly configured pricing. |
| FR-11 | REST `/api/v1/ai/*`: capabilities; structured generate; request metadata (recent, in-memory, no prompt/response content); mock test endpoint (enabled only when mock provider active and not `prod`). |
| FR-12 | Frontend: Overview, Playground, Structured Response Viewer, session Request History. |
| FR-13 | Public Core contract (`AiClient`/`AiCoordinator` + DTOs) documented for GhostBridge App, with a demonstrated structured request (Milestone 7). |
| FR-14 | Review agents extended for AI risks; Kiro spec, steering and hooks maintained; Kiro evidence recorded honestly. |

## Nonfunctional requirements
- Java 21, Spring Boot 3.5.x, constructor injection, records for DTOs/properties, SOLID, Sonar-compliant, cognitive complexity ≤ 15 (target ≤ 10), ≥ 80 % coverage on new code.
- Packages: `com.ghostbridge.<function>` only (`config`, `dto`, `service`, `service.impl`, `exception`, `util`, `restcontroller`, `advice`). Class names unique repo-wide → `Ai` prefix.
- Core must not depend on web; AI must not depend on GhostBridge App; no executable application in the AI module.
- Configuration under `ghostbridge.ai.*`, validated `@ConfigurationProperties` record, restrictive defaults, registered through `AutoConfiguration.imports`, `@ConditionalOnMissingBean`.
- Versions only in `ghostbridge-springboot/pom.xml`.
- Deterministic tests; no live paid API in ordinary tests; live tests tagged and disabled by default.

## AI provider integration requirements
- Bedrock via AWS SDK v2 `bedrockruntime` **Converse API** (model-agnostic request shape).
- Credentials only from the AWS default credential provider chain / environment / IAM role; never from `ghostbridge.ai.*` properties, never committed.
- Region and model id from configuration; missing region/credentials → typed error at first use and a clear readiness status (not silent fallback).
- TLS verification on (SDK default); API call timeout and attempt timeout configured; SDK retries disabled in favour of the bounded retry policy of the AI layer.
- Retries: only for transient/idempotent-safe failures (throttling, 5xx, timeout), maximum configured attempts, backoff; generation is treated as retryable because it has no side effects, bounded by the budget.
- **No silent mock fallback**; `prod` startup fails if provider = `mock`.

## Security requirements
- Prompt injection: system instructions and untrusted user/document content are separate message parts; untrusted content is delimited and labelled as data; model output is never executed, never used to change configuration/security, never auto-deployed.
- No credentials or unnecessary personal data sent to providers; optional redaction via `SensitiveDataMasker`-style rules before provider call.
- Prompts and responses are not logged by default; error messages and ProblemDetails never include prompt text or raw provider errors/metadata.
- Endpoints authenticated by the shared chain; authorization by role (`AI_USER`, `AI_ADMIN`; names configurable) via method security; the playground/mock endpoints are least-privilege and mock is disabled in `prod`.
- Request size, input length, output tokens, response size, concurrency and rate are bounded.
- Frontend never receives credentials or provider private configuration.
- XML/WSDL-derived content is treated as untrusted text; parsing and XXE remain in the application layer.
- Relevant OWASP LLM Top 10 items addressed in `security-review.md`: LLM01 prompt injection, LLM02 sensitive disclosure, LLM05 improper output handling, LLM06 excessive agency, LLM10 unbounded consumption (others reviewed as applicable).

## Performance requirements
- Mock provider round-trip (core) under 50 ms in unit tests; orchestration overhead (excluding provider) negligible and non-blocking beyond configured limiters.
- Default request timeout 30 s, max concurrency bounded (default 4), default rate limit configurable; excess is rejected fast with a rate-limit error rather than queued unboundedly.
- Prompt templates are loaded and parsed once at startup and cached; request metadata store is bounded (default 200 entries, oldest evicted).

## Acceptance criteria
1. `mvn clean verify` passes from the repository root; Maven hierarchy unchanged; `ai-core` has no dependency on `ai-web`/app; `ai-web` depends on `ai-core`.
2. `AiClient` demonstrably returns a deterministic `AiResponse` with the mock provider; unit tests cover FR-1…FR-10 scenarios listed in `test-cases.md`.
3. Selecting provider `mock` under profile `prod` fails startup; missing Bedrock config/credentials yields typed errors, not a fallback.
4. Prompt rendering rejects missing/unknown variables; untrusted input cannot alter system instructions in the rendered structure (tested).
5. Structured output rejects malformed JSON, schema violations and missing required fields; error text contains no prompt content (tested).
6. Web endpoints: 401 unauthenticated, 403 wrong role, 400 invalid payload, 413/400 oversized payload, 429 rate limited, 502/503/504 provider failures — all as `ProblemDetail` with `code` and `correlationId` and no sensitive detail (tested).
7. No competing `SecurityFilterChain`, CORS config or unordered exception advice (verified by test/inspection).
8. Frontend `npm run build`, `npm run lint`, `npm test` pass (scripts created and documented); no credential appears in the bundle or UI.
9. Security and code-quality reviews written to this folder by the separate agents; no unresolved Critical/High finding (or listed as blocker); Sonar status reported as "not verified" unless actually run.
10. Real Bedrock integration test exists, tagged `live-provider`, disabled by default; its result is reported as "not run" unless credentials are available.
11. Task documents, `PROJECT_STRUCTURE.md`, Kiro spec/steering/hooks and evidence are updated and consistent.
