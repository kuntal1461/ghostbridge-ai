# Existing Implementation — GB-APP-001 (Milestone 6 preparation)

Status: **analysis only. No Milestone 6 code has been written.**

## Scope analysed
`rules/rules.md`, `doc-architecture/{PROJECT_STRUCTURE,architecture}.md`, `hackathon-requirements/HACKATHON_REQUIREMENTS.md`, `.kiro/steering/*`; modules `ghostbridge-springboot-{core,web,frontend}`, `ghostbridge-ai-{core,web,frontend}`, `ghostbridge-app-{core,web,frontend}`; task folders GB-SPRINGBOOT-001, GB-AI-001..005.

## What exists and must be reused (not rebuilt)
| Concern | Existing implementation | Where |
|---|---|---|
| Security chain | Deny-by-default, JSON 401/403 (`ProblemDetail` with `code`, `correlationId`), security headers (CSP `default-src 'none'`, nosniff, frame deny, HSTS), CORS (empty allow-list by default), CSRF on when `ghostbridge.security.stateless=false`, `GhostBridgeHttpSecurityCustomizer` (runs last) as the **only** authentication integration point; form login, HTTP basic and logout are disabled by default | `ghostbridge-springboot-web` |
| Config contract | `ghostbridge.*` validated records; `ghostbridge-defaults.properties` (Secure/HttpOnly/SameSite=strict session cookie, graceful shutdown, no stack traces, actuator health/info only); `prod` startup validation | `ghostbridge-springboot-core` |
| Errors | `GlobalExceptionHandler`, `ProblemDetailFactory`, correlation-id filter, request-size filter | springboot-web |
| AI REST API | `/api/v1/ai/{generate,generate/structured,provider/status,usage}`; role `AI_USER` (`ghostbridge.ai.web.required-role`), per-requester limiter, body limit, approved structured schemas (`ghostbridge.ai.web.structured-schemas`), `AiExceptionAdvice` | `ghostbridge-ai-web` (auto-configured, gated by `ghostbridge.security.enabled`) |
| AI core | `AiClient`/`DefaultAiCoordinator`, prompts (`generic-structured`), schema `generic-summary` (`{summary}` only), providers mock/ollama(local, cloud), approved models, budget, retry, telemetry | `ghostbridge-ai-core` |
| Ollama mode | `ghostbridge.ai.ollama.local-enabled` (explicit, no default; cloud only when explicit), `local.base-url`, `local.model`, `local.allowed-hosts`; validated loopback/private/allow-listed hosts; no credential in local mode; verified with `phi3:3.8b` (plain request, structured JSON-mode request through the coordinator) | GB-AI-005 |
| AI frontend | React 19/Vite console: Dashboard, Playground, Structured, Provider Status, Usage; typed client, `ApiError` kinds, in-memory `AuthStrategy` (`bearerAuth`, `sessionCookieAuth` with explicit CSRF header), dev proxy `/api`, dev-only mock, `check-dist` guard; exports `AiConsole`, `setAiAuthStrategy`, API functions from `src/index.ts` | `ghostbridge-ai-frontend` (GB-AI-004) |
| Maven wiring | `ghostbridge-app-web` already depends on `ghostbridge-app-core`, `ghostbridge-springboot-web`, `ghostbridge-ai-web`; Boot plugin present, repackage **skipped** until a main class exists | `ghostbridge-app/ghostbridge-app-web/pom.xml` |

## What is missing (Milestone 6 scope)
- **No runnable application:** `ghostbridge-app-web` has no `@SpringBootApplication`, no `application.yml`, only `.gitkeep` files; `app-core` and `app-frontend` are empty scaffolds (`package.json` + `tsconfig.json`).
- **No authentication mechanism:** every AI call returns 401. No user store, login, logout or session handling.
- **No Local Ollama wiring in a runnable config:** only tests exercise it.
- **No frontend host:** the AI console is a standalone app (`main.tsx`); nothing mounts it inside the app frontend, handles login, or configures `setAiAuthStrategy`.
- **No deployment artefacts:** no Dockerfile, compose file, reverse-proxy config, or environment documentation beyond `.env.example`.
- **No end-to-end tests** across frontend, app, AI Web and a model.
- Not in scope here: the SOAP-to-REST modernization workflow itself (a later milestone; `.kiro` spec) — Milestone 6 delivers the runnable, authenticated platform and the AI console it will build on.

## Constraints discovered
- `.gitignore` ignores `application-local.*`, `.env*` (except `.env.example`), `secrets/`: committed profile files must be `application.yml`, `application-dev.yml`, `application-prod.yml`; `application-local.yml` stays a developer-only, untracked file.
- Shared defaults set `server.servlet.session.cookie.secure=true` and `same-site=strict`: plain-http local development needs an explicit dev override (browsers other than Chromium/Firefox on `localhost` drop Secure cookies over http).
- The AI mock provider is accepted only with profile `local`, `dev` or `test` active and `prod` inactive.
- `AiRequesterResolver` identifies callers by SHA-256 of `Authentication.getName()` (single-issuer assumption, SEC-M4-04): usernames must be unique across auth sources.
- AI Web per-requester token budgets do not exist (SEC-M4-01 accepted); the global `ghostbridge.ai.budget` must be enabled in production.
- `Retry-After` is not in the default CORS exposed headers (SEC-M4-10); irrelevant for same-origin deployment.
- Spring Security OAuth2/JOSE artifacts are **not** in the local Maven repository (offline builds would need them downloaded); the recommended design needs no new Spring dependency.
- The frontend mock and its fixtures use a structured payload (`keyPoints`, `confidence`) that the real `generic-summary` schema forbids (`additionalProperties: false`, only `summary`): to be corrected (done as part of this preparation, see implementation.md).
- Hackathon constraints: public working app (or demo/test build) free of charge for judging, test credentials in the submission, submission deadline **December 4, 2026 2:00 PM PT**.

## Evidence
Source reads on 2026-11-24; `mvn -o -B clean verify` BUILD SUCCESS (ai-core 376 tests with 2 skipped manual live tests, ai-web 86); local Ollama on `127.0.0.1:11434` (loopback only) with `phi3:3.8b`.
