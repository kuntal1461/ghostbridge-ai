# Requirements — GB-AI-002 (Ollama Cloud Provider Integration)

Kiro spec: [.kiro/specs/ghostbridge-ai/](../../../.kiro/specs/ghostbridge-ai/) (updated for Ollama Cloud). This file is authoritative for status.

## Scope
Add Ollama Cloud as the real provider behind the existing `AiProvider` SPI in `ghostbridge-ai-core`, with backend-only credential handling and the G-M3-1 byte limits. No AI Web, frontend, app business logic, new Maven modules or live requests in automated builds. M4 is not started.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | `ghostbridge.ai.provider=ollama` selects an `OllamaAiProvider` implementing the existing `AiProvider` SPI; `DefaultAiCoordinator`/`AiClient`, request/response DTOs, retry, limiter, budget, telemetry and structured validation are reused unchanged. |
| FR-2 | Requests go to `POST <base-url>/v1/chat/completions` (OpenAI-compatible) with `model`, `messages` (system + user), `temperature`, `max_tokens` (from the call's `maxOutputTokens`), `stream=false`, and `response_format` JSON mode when structured output is required. |
| FR-3 | Model id, base URL, mode, timeouts, byte limits, temperature and output tokens are validated `ghostbridge.ai.*` configuration; nothing is hard-coded in Java except fixed protocol constants in `GhostBridgeAiLiterals`. |
| FR-4 | Responses are parsed safely: `choices[0].message.content` (string) → `AiProviderResult.text`; `usage.prompt_tokens/completion_tokens` → `AiUsage` only when present and non-negative. Any other shape → `INVALID_MODEL_RESPONSE`. |
| FR-5 | Provider failures map to typed `AiException` reasons with generic messages: 401/403 → `MISSING_CREDENTIALS`; 429 → `PROVIDER_THROTTLED`; 408 → `PROVIDER_TIMEOUT`; 5xx → `PROVIDER_UNAVAILABLE`; connect/read timeout → `PROVIDER_TIMEOUT`; I/O failure → `PROVIDER_UNAVAILABLE`; interrupt → `INTERRUPTED`; other 4xx/3xx → `PROVIDER_FAILURE`. |
| FR-6 | Mock provider and its profile guard are unchanged and still pass their tests. |
| FR-7 | Optional manual live test (tagged, disabled by default) that needs an explicit opt-in and a locally configured credential. |

## Security requirements (highest priority)
| ID | Requirement |
|---|---|
| SR-1 | The API key exists only in the backend runtime environment (`OLLAMA_API_KEY`, mapped to `ghostbridge.ai.ollama.api-key`). It is never in Java literals, committed YAML/properties, tests (synthetic dummies only), docs, review reports, Kiro specs, frontend code, `VITE_` variables, REST responses, Actuator output, logs, telemetry, exception messages/stack traces, URLs, Git history or build artifacts. |
| SR-2 | The key is held in a dedicated secret type whose `toString()`, JSON and logging representation is masked, so records/maps containing it cannot leak it by accident. Its value is read only when building the `Authorization` header. |
| SR-3 | The key is sent only as `Authorization: Bearer …` to the validated provider endpoint; never in a URL; the header is never logged or placed in telemetry or error text. |
| SR-4 | Outbound destination is restricted: HTTPS only, host must be on the configured allow-list (default `ollama.com`), no user-info/query/fragment in the base URL, no user-controlled URL ever reaches the client, redirects are never followed (so the key cannot be forwarded). TLS certificate validation stays at JDK defaults (no custom trust manager, no hostname-verifier override). |
| SR-5 | Missing/blank key with `provider=ollama` fails startup or provider initialization with a safe message that names the property, never a value. Safe presence checks only; the value is never printed. |
| SR-6 | G-M3-1: serialized request body limited in UTF-8 bytes (multibyte-safe); response body read incrementally and rejected beyond the byte limit (also when `Content-Length` exceeds it); `max_tokens` always sent; no unbounded buffering. |
| SR-7 | `.gitignore` covers `.env`, `.env.*` (except `.env.example`), `*.local`, `secrets/`, `*.pem`, `*.key`; `.env.example` holds only empty placeholders. Before any commit/push: staged-file and tracked-file secret checks, history check, scanner (Gitleaks or equivalent when available; otherwise the fallback is reported as such). A detected credential blocks the commit (pre-commit hook; advisory, bypassable with `--no-verify`); push-time protection needs a CI/server-side scan, which is out of scope here. |
| SR-8 | Frontend never talks to Ollama Cloud and never receives the key. The model is invoked only from the trusted backend after authentication/authorization (enforced in M4: G-M4-1 per-requester limits remain mandatory before any AI Web endpoint). No unauthenticated model invocation path is added. |
| SR-9 | Prompt-injection boundaries from M2 are preserved (sanitized/delimited data, schema-validated output, no execution of output). |

## Non-functional
Java 21, Spring Boot 3.5, constructor injection, no wildcard or static literals imports, cognitive complexity ≤ 15, `com.ghostbridge` packages only, class names unique, core never depends on web/app, no new Maven module. A new dependency is added only if unavoidable (none planned: JDK `HttpClient` + existing Jackson).

## Acceptance criteria
1. `mvn clean verify` green; all M2 tests still pass; new provider tests (list in `test-cases.md`) pass without any real key or network access to Ollama.
2. Tests prove: endpoint and request format, Authorization header only on the approved endpoint, status/timeout/JSON error mapping, byte limits (multibyte, request and response), retry limits via the coordinator, redirects not followed and key not forwarded, no secret in logs, error messages, `toString` or config metadata.
3. Missing key → safe failure; invalid key (401/403) → safe typed error.
4. Secret scan (fallback or scanner) on working tree, staged files and history shows no credential; result recorded honestly with the tool actually used.
5. Independent security and code-quality reviews written to this folder; no unresolved Critical/High.
6. Documentation and Kiro spec updated; provider reported as **mock-/stub-server-tested, not live-verified** unless the user explicitly runs the manual test.
7. No commit or push without explicit user approval.
