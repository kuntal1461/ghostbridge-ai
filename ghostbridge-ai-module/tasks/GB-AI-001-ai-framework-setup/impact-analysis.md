# Impact Analysis — GB-AI-001 (AI Framework Setup)

## Dependencies on Spring Boot infrastructure
| Shared piece | How AI uses it | Change needed in `ghostbridge-springboot`? |
|---|---|---|
| Parent POM / BOM | Inherits Spring Boot 3.5.7, Java 21 | **Yes, additive:** AWS SDK BOM import + `json-schema-validator` managed version (rules §4: versions only there) |
| `GhostBridgeProperties` | Pattern only; `ghostbridge.ai.*` is a separate record | No |
| `GhostBridgeProductionValidator` | Pattern; AI has `AiProductionValidator` | No |
| `ErrorCode` / `GlobalExceptionHandler.statusFor` | AI exceptions extend `GhostBridgeException` | **Yes, additive (pending approval, plan D5):** codes `RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`, `UPSTREAM_TIMEOUT`, `UPSTREAM_ERROR` and their status mapping. Existing codes/behaviour unchanged |
| `ProblemDetailFactory`, `CorrelationIdFilter`, `SensitiveDataMasker` | Reused as-is | No |
| Security chain / method security | AI endpoints inherit deny-by-default; roles enforced with `@PreAuthorize` | No new chain; consumer app supplies authentication through `GhostBridgeHttpSecurityCustomizer` |
| CORS | Reused; frontend origin is added by the host app's `ghostbridge.cors.allowed-origins` | No |
| `RequestSizeLimitFilter` | HTTP body ceiling; AI adds a smaller semantic `max-input-length` | No |
| Actuator / Micrometer | Optional metrics | No |

## Changes affecting GhostBridge App
- `ghostbridge-app-core` will depend on `ghostbridge-ai-core` and call `AiClient` — **no app code is changed in this task**.
- `ghostbridge-app-web` (future executable) will pull `ghostbridge-ai-web`; it must then supply: `ghostbridge.ai.*` environment values, AWS credentials via environment/IAM role, authentication through `GhostBridgeHttpSecurityCustomizer`, and role mapping for `AI_USER`/`AI_ADMIN`.
- Adding `ai-web` to the app will register `/api/v1/ai/**` endpoints; they are denied by default until authentication exists.
- App must treat WSDL/XML-derived text as untrusted and parse it with `SecureXmlParser` before handing text to AI; AI does not parse XML.

## API compatibility
- New API surface only (`/api/v1/ai/*`); no existing endpoint changes. Versioned in the path; breaking changes require `/v2`.
- Error format is the existing `ProblemDetail` contract (`code`, `correlationId`, `timestamp`); additive property `reason` for AI errors.
- Existing `ErrorCode` consumers: adding enum constants is source-compatible; any consumer with an exhaustive `switch` without default would need review (none found in the repository — to be re-verified with a grep during M2).
- Java contract (`AiClient`, `AiRequest`, `AiResponse`) becomes a public API for the app; record components are the compatibility surface, so extend by adding optional components deliberately.

## Security implications
- New outbound network path to AWS Bedrock (TLS, IAM credentials). Credentials never in config files; least-privilege IAM (`bedrock:InvokeModel`/`Converse` on approved models only) is an operational requirement.
- Sensitive data flows to a third-party processor: only minimal, optionally redacted content is sent; this must be stated in operational docs.
- New cost/abuse surface (LLM10): mitigated by limits, authorization and budget; residual risk is a compromised authorized user.
- Prompt-injection residual risk is accepted and documented; compensating controls: no execution of output, schema validation, no tool/agent capabilities in MVP.
- Mock provider is a development-only feature and is blocked in `prod`.

## Operational implications
- New configuration keys under `ghostbridge.ai.*` (documented with safe defaults; provider default is **unset → AI disabled**, not mock).
- New metrics (`ghostbridge.ai.requests`, `ghostbridge.ai.request.duration`, `ghostbridge.ai.tokens`) when a `MeterRegistry` is present.
- Health: provider readiness is exposed through the authenticated capabilities endpoint, not the public health probe.
- Build: AWS SDK increases artifact size; sync Apache client only. Live tests are excluded from CI by default.
- Rollback: revert milestone commits; no data migration, no persistent state.

## Affected files (planned)
New: all files under `ghostbridge-ai-module/**` (code, resources, tests, frontend), `.kiro/specs|steering|hooks/**`, this task folder.
Modified (additive): `ghostbridge-springboot/pom.xml`, `ghostbridge-springboot-core/…/ErrorCode.java`, `ghostbridge-springboot-web/…/GlobalExceptionHandler.java` (+ tests), `ghostbridge-ai-core/pom.xml`, `ghostbridge-ai-web/pom.xml`, `ghostbridge-ai-frontend/package.json`, `doc-architecture/PROJECT_STRUCTURE.md`, `doc-architecture/architecture.md`, `.kiro/README.md`, `.kiro/agents/**` (reviewer prompt extensions).
Not touched: `ghostbridge-app/**`, root `pom.xml`.
