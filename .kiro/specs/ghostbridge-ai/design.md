# Design — GhostBridge AI Framework (Kiro spec)

Authoritative detail: [implementation-plan.md](../../../ghostbridge-ai-module/tasks/GB-AI-001-ai-framework-setup/implementation-plan.md). This file is the spec-level summary; keep them consistent.

## Architecture
```
Caller (GhostBridge App / REST)            ghostbridge-ai-web
  AiRestController ──validate/map──► AiClient (interface, ai-core)
                                          │
                                   DefaultAiCoordinator
        ┌──────────┬──────────────┬───────┴────────┬───────────────┐
 AiPromptService  AiLimiter   AiRetryExecutor  AiStructuredOutput  AiRequestRegistry
        │                          │             Service            + AiMetricsRecorder
        └────────────► AiProvider (SPI) ◄────────┘
                       ├─ MockAiProvider (local/test only)
                       └─ OllamaAiProvider (OpenAI-compatible /v1/chat/completions over HTTPS; backend-only OLLAMA_API_KEY held as AiSecret)
```
Dependency rules: `ai-web → ai-core → springboot-core`; `ai-web → springboot-web`; ai-core never references ai-web or app; no executable application.

## Components and interfaces
- `AiClient { AiResponse generate(AiRequest) }` — public contract.
- `AiProvider { String name(); AiProviderResult invoke(AiProviderCall); AiProviderStatus status(); }` — adapter SPI; adding a provider = new bean, no caller change.
- `AiPromptService { RenderedPrompt render(name, version, variables, untrustedContent) }`.
- `AiStructuredOutputService { JsonNode validate(String text, String schemaName) }`.
- `AiRequestRegistry` — bounded in-memory metadata (no content).
- Records: `AiRequest`, `AiResponse`, `AiUsage`, `AiGenerationOptions`, `AiRequestMetadata`.
- `AiProperties` (`ghostbridge.ai`), `AiAutoConfiguration`, `AiProductionValidator`.

## Error model
`AiException extends GhostBridgeException` carrying an `AiErrorReason` (14 reasons, each mapped to a shared `ErrorCode`). HTTP: invalid input 400; oversized input 413; rate limit/concurrency 429; token-budget exhaustion 429 with distinct code `QUOTA_EXCEEDED` (documented policy status); invalid/oversized/failed upstream response 502; unavailable/missing config or credentials/interrupted 503; timeout 504. Shared `ErrorCode`/`statusFor` changes were approved and implemented (exhaustive switch). Source of truth: task `implementation-plan.md` error-contract table.

## Security design
Authentication from the shared chain; roles `AI_USER`/`AI_ADMIN` (method security); deny by default; no CORS/security duplication; untrusted content delimited as data (defence in depth, not a complete prompt-injection control); output never executed; logs contain ids, provider, model, status, duration, token counts only; credentials never in properties; `provider=mock` starts only when `local`, `dev` or `test` is active and `prod` is not; per-requester limits from trusted identity are mandatory gate G-M4-1 before any web endpoint, provider byte limits gate G-M3-1 before any real provider.

## Data and state
No database. Prompts/schemas are classpath resources loaded and validated at startup. Registry is bounded and process-local.

## Testing strategy
Mock provider and a local stub HTTP server standing in for Ollama Cloud (synthetic credentials only); `@WebMvcTest`/`@SpringBootTest` slices with `spring-security-test`; live provider test tagged `live-provider` (disabled by default). Full list: task `test-cases.md`.

## Key decisions
D1–D14 in the task `implementation-plan.md` (Converse API, resource prompts, networknt schema validation, additive ErrorCode change, in-house limiter/retry, optional Micrometer, in-memory registry, role model, structural prompt-injection controls, springdoc deferred, Vite/Vitest/ESLint, tagged live tests).

## Out of scope (MVP)
Streaming, tool use/agents, RAG/retrieval, persistent history, multiple real providers, OpenAPI UI, SOAP/WSDL logic.
