# Requirements — GhostBridge AI Framework (Kiro spec)

Execution record and status: [task GB-AI-001](../../../ghostbridge-ai-module/tasks/GB-AI-001-ai-framework-setup/requirements.md). Rules: [rules/rules.md](../../../rules/rules.md).

## Introduction
A provider-neutral, reusable AI subsystem (`ghostbridge-ai-core`, `-web`, `-frontend`) for GhostBridge App and future applications. MVP: Amazon Bedrock plus a deterministic mock provider.

## Requirements (EARS)

### R1 — Provider-neutral client
**User story:** As an application developer, I want one `AiClient` interface so that I never depend on a provider.
1. WHEN `generate(AiRequest)` is called THEN the system SHALL return an `AiResponse` containing request id, provider, model, text, optional structured JSON, usage and duration.
2. The request and response types SHALL NOT reference GhostBridge App, SOAP or migration types.

### R2 — Provider adapters and selection
1. WHEN `ghostbridge.ai.provider` names a registered adapter THEN the system SHALL route calls to it.
2. IF the provider is unset or unknown THEN the system SHALL report a missing-configuration error and SHALL NOT fall back to another provider.
3. IF the provider is `mock` AND (the profile `prod` is active OR none of `local`, `dev`, `test` is active) THEN startup SHALL fail.
4. Only the `mock` and `bedrock` adapters SHALL exist.

### R3 — Configuration
1. The system SHALL bind validated properties under `ghostbridge.ai.*`: provider, approved models, default/maximum output tokens, temperature bounds, timeout, retry, rate limit, concurrency, max input length, max response size, optional token budget, optional pricing, structured-output settings.
2. Credentials SHALL NOT be configuration properties; they come from the environment or the AWS default credential chain.

### R4 — Orchestration
WHEN a request is received THEN `AiCoordinator` SHALL: validate → resolve provider/model → select prompt → apply options → invoke provider (limited, retried within bounds) → validate output → return result and record metadata.

### R5 — Prompt management
1. Prompts SHALL be named, versioned classpath resources with system text, user template and declared variables.
2. IF a variable is missing or undeclared THEN rendering SHALL fail naming the variable (not its value).
3. User and untrusted content SHALL be rendered into a delimited data section and SHALL NOT be inserted into the system instructions.

### R6 — Structured output
1. WHEN a schema is required THEN the system SHALL parse the output as JSON, validate it against the JSON Schema, and enforce the response-size limit.
2. IF validation fails THEN the system SHALL raise a typed error listing JSON paths only, never prompt text.

### R7 — Errors
The system SHALL expose typed, client-safe errors for: invalid input, missing configuration, missing credentials, provider unavailable, provider timeout, rate limited, invalid model response, output validation failure, limit exceeded. Raw provider messages SHALL NOT reach API consumers.

### R8 — Observability
1. Each request SHALL have a correlation id (reusing the shared `X-Correlation-Id`).
2. The system SHALL record provider, model, duration, status and reported token usage as Micrometer metrics when a registry exists.
3. Prompts and responses SHALL NOT be logged by default.

### R9 — Cost and abuse controls
The system SHALL enforce max input length, max output tokens, max concurrency, request-rate limit and optional token budget, and SHALL report cost only from explicitly configured pricing and provider-reported usage.

### R10 — REST API
1. The system SHALL expose `GET /api/v1/ai/capabilities`, `POST /api/v1/ai/generate`, `GET /api/v1/ai/requests`, `GET /api/v1/ai/requests/{id}`, `POST /api/v1/ai/mock/test`.
2. Endpoints SHALL require authentication and a role (`AI_USER`; `AI_ADMIN` for mock test) using the shared security chain.
3. The mock test endpoint SHALL be unavailable unless the mock provider is active and the profile is not `prod`.
4. Errors SHALL be `ProblemDetail` with `code`, `correlationId`, `timestamp`.
5. No raw model-execution endpoint SHALL be exposed.

### R11 — Frontend
The UI SHALL provide Overview, Playground, Structured Response Viewer and session Request History, SHALL use an environment-configured API URL and the host application's backend authentication, and SHALL NOT display or store credentials or prompt content in persistent storage.

### R12 — AI security
1. Model output SHALL NOT be executed, applied to configuration, or deployed automatically.
2. Untrusted content (including WSDL/XML-derived text) SHALL be treated as data.
3. Redaction SHALL be applied to configured patterns before provider calls.

### R13 — GhostBridge App readiness
The public core contract SHALL be documented and demonstrated by a test that performs a structured request through `AiClient`.

### R14 — Governance
Reviews by `ghostbridge-security-reviewer` and `ghostbridge-code-quality-reviewer` SHALL be produced independently and recorded in the task folder; unresolved Critical/High findings SHALL block completion.

## Acceptance
See task `requirements.md` → Acceptance criteria 1–11 and `test-cases.md`.
