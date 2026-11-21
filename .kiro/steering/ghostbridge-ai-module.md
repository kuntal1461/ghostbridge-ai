---
inclusion: fileMatch
fileMatchPattern: "ghostbridge-ai-module/**"
---

# GhostBridge AI Module — Steering

Spec: `.kiro/specs/ghostbridge-ai/`. Execution record: `ghostbridge-ai-module/tasks/GB-AI-001-ai-framework-setup/`.

## Boundaries
- `ghostbridge-ai-core` holds all AI logic and is the only module that knows LLM providers. `ghostbridge-ai-web` only validates, delegates to `AiClient`, and maps results. Core never depends on web or on GhostBridge App. No executable application here.
- No SOAP/WSDL/migration business logic. XML/WSDL-derived text is untrusted input; parsing and XXE defence stay in the app layer (`SecureXmlParser`).
- AI errors are `AiException` (reason enum) mapped by the shared `GlobalExceptionHandler`. Do not add a second global exception handler.

## AI security rules (mandatory)
- Treat user input, template variables, documents and model output as untrusted data. Keep trusted instructions in the system part; untrusted content goes in the delimited user data block.
- Never execute, evaluate, deploy or apply model output (code, shell commands, configuration, security settings). Structured output must pass JSON-Schema validation; valid JSON alone is not trusted.
- Delimiter escaping is defence in depth only, never a claim that prompt injection is prevented; model capabilities stay restricted and output is validated, never executed. Never log or return prompts, template variables, model output, credentials or raw provider errors. Log ids, provider, model, status, duration and token counts only. Do not log caller-supplied strings unsanitized.
- Credentials come from the environment or the provider's default credential chain; never from properties, code, tests or committed files.
- No silent fallback to the mock provider; `provider=mock` is accepted only when `local`, `dev` or `test` is active and `prod` is not (mixed profiles containing `prod` fail startup). No hard-coded region or model id.
- Bound everything: input length, output tokens, response size, timeout, retries (transient failures only), request rate, concurrency, optional token budget. Never fabricate token usage or pricing.
- Frontend never receives credentials or private provider configuration and does not persist prompt content.

## Quality rules specific to this module
- Naming: AI classes carry the `Ai` domain token (prefix or embedded, e.g. `DefaultAiCoordinator`); the hard rule is repository-wide uniqueness of simple class names.
- Providers implement `AiProvider` and map every failure to `AiException` with a generic message (detail only as `cause`).
- Prompts are classpath resources (`prompts/<name>/v<N>.json`), schemas are `ai-schemas/*.schema.json`; no large prompts in Java.
- Add configuration properties only under `ghostbridge.ai.*` with restrictive defaults and validation.

- Literals classes (e.g. `GhostBridgeAiLiterals`): normal class import and `ClassName.CONSTANT` access; no static imports, no wildcard imports (rules.md §3).
