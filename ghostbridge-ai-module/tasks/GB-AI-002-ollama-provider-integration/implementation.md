# Implementation — GB-AI-002 (Ollama Cloud Provider Integration)

Record of what was actually done (2026-11-22). Plan: `implementation-plan.md`.

## Milestone A: documentation and secret hygiene — done
- Phase 1 inspection and fallback secret scans (tree, staged, history, tracked files): no credential found; secret scanner (Gitleaks etc.) **not installed**, so only the regex fallback ran.
- `.gitignore` extended (`.env`, `.env.*` except `.env.example`, `*.local`, `secrets/`, `*.pem`, `*.key`); existing rules preserved; verified with `git check-ignore`. `.env.example` added with empty placeholders and comments only.

## Milestone B: configuration and credential type — done
- `config/AiSecret`: masked `toString()`/JSON (`****`), value only via `reveal()`, constant-time equals, constant hashCode, presence check; binds from a plain string property.
- `config/AiProperties`: new `ollama` record (`mode` = `cloud` only, `base-url` default `https://ollama.com`, `allowed-hosts` default `ollama.com`, `api-key` as `AiSecret`, `connect-timeout` 5s, `max-request-bytes` 256 KiB, `max-response-bytes` 1 MiB) with destination validation (https, allow-listed host, no user-info/path/query/fragment/custom port; messages never echo the URL); provider `ollama` accepted and **requires `model-id`** (no default). Existing keys reused (`model-id`, `max-output-tokens`, `temperature`, `timeout`, `retry.*`).
- `config/AiAutoConfiguration`: `OllamaAiProvider` bean when `provider=ollama`; credential from `ghostbridge.ai.ollama.api-key` else the `OLLAMA_API_KEY` environment variable; missing key fails startup with a message naming the settings only; bean kept eager under lazy initialization via `LazyInitializationExcludeFilter`.
- `constants/GhostBridgeAiLiterals`: fixed protocol constants (path, header names, roles, status thresholds, mode, env variable name, provider id).

## Milestone C/D: provider and integration — done
- `service/impl/OllamaAiProvider` implements the `AiProvider` SPI (see plan D1), JDK `HttpClient` (HTTP/1.1, `Redirect.NEVER`, JDK default TLS validation, connect timeout), async send with an overall deadline (`ghostbridge.ai.timeout`), `Authorization: Bearer` header built only from `AiSecret.reveal()`, request serialized to bytes and rejected above `max-request-bytes` (UTF-8 bytes) before any I/O, status mapping (401/403 → `MISSING_CREDENTIALS`, 429 → `PROVIDER_THROTTLED`, 408 → timeout, 5xx → `PROVIDER_UNAVAILABLE`, other → `PROVIDER_FAILURE`), error bodies discarded unread, transport failures mapped without attaching JDK causes, tolerant response parsing into `OllamaChatResponseVO` (reported usage only if a non-negative integer).
- `OllamaBoundedBodySubscriber`: reads the success body incrementally, cancels the connection beyond `max-response-bytes`, rejects a declared `Content-Length` above the limit before reading.
- `OllamaChatRequestVO`, `OllamaChatMessageVO`, `OllamaChatResponseVO`: package-private provider wire contracts (naming per the brief; existing public `AiRequest`/`AiResponse` unchanged).
- Retry is the coordinator's existing bounded policy; the provider does not retry.
- No new dependency, no new module, no change outside `ghostbridge-ai-core`, `.gitignore`, `.env.example`, docs and `.kiro` spec.

## Review round 1 fixes (independent reviews, 2026-11-22)
- **SEC-01 (Medium, confirmed by a throwaway probe):** the JDK quotes the whole header value when it rejects an `Authorization` header (for example a key containing a line break or zero-width character). `AiSecret.isHeaderSafe()` (printable ASCII 0x21-0x7E only) is enforced in the provider constructor; request-building failures are mapped to a generic `PROVIDER_FAILURE` with no cause.
- SEC-02: constructor refuses launch-time `jdk.httpclient.HttpClient.log` modes that print headers and `javax.net.debug` plaintext modes (best effort; documented limits).
- SEC-03 / CQ-12: the package-private test constructor accepts loopback endpoints only (`127.0.0.1`, `localhost`, `[::1]`), so another module in the shared package cannot bypass the https/allow-list validation.
- SEC-04 / CQ-06/07: `OllamaAiProvider implements AutoCloseable` (`shutdownNow()`), Spring infers `close()`; stub server executor shut down.
- SEC-05 / CQ-11: the `OLLAMA_API_KEY` fallback reads the OS `systemEnvironment` property source only.
- SEC-06 / CQ-02: `base-url` trimmed once and stored; endpoint URI built without echoing the URL; byte limits have a 1 KiB floor.
- CQ-01: Ollama auto-config tests replace the real OS environment with an empty one (verified with a synthetic `OLLAMA_API_KEY` exported). CQ-03: interrupt and serialization-failure branches tested. CQ-05: connect-timeout mapping tested deterministically through `translate(...)` (no off-host network probe). CQ-09/10: duplicate message and unused constant removed, `Content-Length` literal moved to `GhostBridgeAiLiterals`.
- SEC-07: `.gitignore` extended (`.envrc`, `application-local.*`, `*.p12`, `*.jks`, `*.pfx`); versioned `.githooks/pre-commit` + `.githooks/secret-scan-allowlist` added (documented in `PROJECT_STRUCTURE.md`).
- S-04 finding while fixing: annotation processing does not run by default on JDK 23+, so no configuration metadata is generated there; the earlier metadata assertion was vacuous. Replaced by an unconditional annotation-based check (the key has no `@DefaultValue`) plus the metadata check where metadata exists (JDK 21, the project target).

## Review round 2 fixes
- R2-01 (Medium, hook): key/secret/token/password assignments now matched case-insensitively (so `OLLAMA_API_KEY=<value>` is caught); filled-in values in `.env.example` refused; lines beginning with `++` scanned; non-ASCII file names handled (`core.quotepath=false`, `-z`); `application-local.*` refused; staged-diff read failures fail closed; gitleaks, when installed, runs **in addition** to the file-name and regex checks (previously it replaced them). Verified in a throwaway repo with synthetic values (12 cases, no value ever printed) and against the real change set (exit 0).
- Hook false positives fixed by renaming a test variable; the two independent review reports (which quote synthetic probe commands) are allow-listed by exact path.
- R2-03 partly: `javax.net.debug` plaintext modes refused; remaining limits documented (see `impact-analysis.md`). R2-04: documentation no longer claims a push is blocked. Tests added: `javax.net.debug` guard, behavior after `close()`, Spring destroy-method inference.

## Deviations from the brief (flagged)
1. The adapter implements `AiProvider` (provider SPI), not `AiClient` (coordinator contract); chain is `DefaultAiCoordinator(AiClient) → AiProvider → OllamaAiProvider`.
2. `RequestVO`/`ResponseVO` naming applied to the provider-internal wire records only; existing public records were not renamed (no such repository rule existed).
3. Model id `deepseek-v4.1-flash` is **not** hard-coded or defaulted; it must be supplied via `ghostbridge.ai.model-id`.
4. Connect timeout is a new `ollama.connect-timeout`; the response timeout reuses `ghostbridge.ai.timeout` (per attempt, overall including body) instead of introducing a duplicate setting.
5. Secret scanner (Gitleaks) not installed: regex fallback used (hook and ad-hoc scans) and reported as weaker.
6. Pre-commit hook added (`.githooks/`, requires `git config core.hooksPath .githooks`, now set in this clone's local config) because the brief requires blocking commits that contain credentials; it is advisory and not a substitute for a CI/server-side scan.

## Not done (out of scope / by design)
No AI Web controller, no frontend, no per-requester limits (G-M4-1 still mandatory before any endpoint), no live request, no Bedrock adapter, no commit or push.
