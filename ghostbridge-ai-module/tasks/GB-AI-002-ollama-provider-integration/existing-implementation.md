# Existing Implementation — GB-AI-002 (Ollama Cloud Provider Integration)

Reviewed: 2026-11-22, branch `feature/gb-ai-002-ollama-provider` created from `origin/main` at `fc2875e` (GB-AI-001 M2 merged via PR #1).

## Phase 1 — credential and secret review (done before any change)
| Check | Result |
|---|---|
| Hard-coded credentials in working tree | None found. Fallback pattern scan (AWS/GitHub/Slack/`sk-` keys, private-key headers, `Bearer` tokens, `key/secret/token/password = <16+ chars>`) matched only synthetic fixtures in `SensitiveDataMaskerTest` (`abc.def.ghi`, `SECRET123`, `Bearer x`). |
| Credentials in Git history | The same pattern scan over every added line of all refs (4 refs, 3 commits + merge) found nothing beyond those fixtures. The previously shared (now compromised) Ollama key is not known to the repository and no Ollama reference existed anywhere before this task. |
| `.env*`, `*.pem`, `*.key`, `secrets/` files tracked or present | None. |
| `.gitignore` | Only `target/`, `.idea/`, `*.iml`, `.DS_Store`, `node_modules/`. **Gap:** no secret-file patterns. |
| Secret scanner | **Not available** (`gitleaks`, `trufflehog`, `detect-secrets` not installed). Only the regex fallback above was run; it is weaker than a real scanner and is reported as such. |
| Unrelated local change | `.vscode/settings.json` is modified in the working tree by the IDE; it is not part of this work and is never staged here. |
| Frontend | `ghostbridge-ai-frontend` has no source; nothing can contain a key. No `VITE_` variables exist. |

## Scope analysed
`rules/rules.md` (§6 shared infrastructure, §7 security, §8 quality, §9 reviews), `ghostbridge-ai-core` main and test sources, `ghostbridge-springboot` shared config (`GhostBridgeProperties`, exception handling), M2 task folder and its review reports, `.gitignore`, POMs.

## Current AI core (from GB-AI-001 M2, merged)
| Piece | State |
|---|---|
| `AiClient` | Public contract, implemented by `DefaultAiCoordinator` (validation → prompt → guard/retry/budget → provider → size check → structured validation → telemetry). |
| `AiProvider` SPI | `name()`, `invoke(AiProviderCall)`, `status()`. Only implementation: `MockAiProvider` (allowed only in `local`/`dev`/`test`, never with `prod`). |
| DTOs | `AiRequest`, `AiResponse`, `AiUsage`, `AiProviderCall` (model, system, user, maxOutputTokens, temperature, timeout, jsonOutput), `AiProviderResult`. |
| Config | `AiProperties` (`ghostbridge.ai.*`): `provider` (`mock`/`bedrock`), `model-id`, `aws.region`, `max-output-tokens`, `temperature`, `timeout` (per attempt), `max-input-length`, `max-response-size` (chars), `retry.*`, `rate-limit.*`, `budget.*`, `pricing.*`, `mock.*`, `redaction-patterns`. Duration bounds validated; unset provider = AI disabled (no mock fallback). |
| Errors | `AiException` + `AiErrorReason` (14 reasons → shared `ErrorCode` → HTTP 400/413/429/502/503/504). Retryable: `PROVIDER_UNAVAILABLE`, `PROVIDER_TIMEOUT`, `PROVIDER_THROTTLED`. |
| Safety | `AiRetryExecutor` (bounded, per-attempt timeout), `AiLimiter` (global rate/concurrency), `AiTokenBudget`, strict JSON structured-output validation (no trailing content/duplicate keys), delimiter sanitizing, MDC-validated correlation id, non-fatal telemetry. |
| Literals | `GhostBridgeAiLiterals` (`com.ghostbridge.constants`); normal class import, no static/wildcard imports (rules §3). |
| Bedrock | `provider=bedrock` is accepted by `AiProperties` validation but **no adapter exists**; the M2 plan assumed Bedrock for M3. Direction changed to Ollama Cloud by the user. |

## Functionality to preserve
All M2 behavior and tests (springboot-core 31, springboot-web 55, ai-core 174), the mock provider and its profile guard, the error contract, structured-output validation, Maven hierarchy, shared Spring Boot security/CORS/exception config (not duplicated), no AI Web or frontend change.

## Gaps and risks found
1. No real provider; no HTTP client in `ghostbridge-ai-core` (it has no `spring-web`; the JDK `java.net.http.HttpClient` is available without a new dependency).
2. **Credential leakage vectors in current design:** a plain `String apiKey` inside a record would appear in `AiProperties.toString()`, bind errors, and any debug dump. The existing masker is pattern-based, not a guarantee. A dedicated secret type is needed.
3. **G-M3-1 (mandatory before a real provider):** no provider byte limits yet — request bytes, response bytes read incrementally, `max_tokens` forwarded.
4. No outbound destination control: no https requirement, host allow-list, or redirect policy exists.
5. `.gitignore` has no secret patterns; no `.env.example`; no secret scanner installed.
6. Naming mismatch to resolve: the task brief says provider code should implement `AiClient` and use `RequestVO`/`ResponseVO` contracts. In this codebase `AiClient` is the *coordinator-facing public contract* (implemented by `DefaultAiCoordinator`) and the provider SPI is `AiProvider`; `RequestVO`/`ResponseVO` naming does not exist anywhere (rules.md has no such rule). See `implementation-plan.md` D1/D2.
7. The model id `deepseek-v4.1-flash` and the endpoint `https://ollama.com/v1/chat/completions` come from the task brief; they cannot be verified offline and must be treated as configuration, not facts.
8. `.kiro` has no Ollama reference; the Kiro spec still describes Bedrock as the real provider and must be updated. No Kiro run has occurred for any milestone (documented in `.kiro/README.md`).

## Evidence
`git status`, `git log --all`, `git ls-files` secret-file search, fallback regex scans of working tree and history (commands only; no secret values exist to print), `rules/rules.md`, `AiProperties.java`, `AiProvider.java`, `DefaultAiCoordinator.java`, `GhostBridgeAiLiterals.java`, M2 `security-review.md` and `milestone.md` (gate G-M3-1).
