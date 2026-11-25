# Existing Implementation — GB-AI-004

## Scope analysed
- `ghostbridge-ai-module/ghostbridge-ai-frontend` (the whole project), `doc-architecture/PROJECT_STRUCTURE.md`, `rules/rules.md`, `.kiro/steering/ghostbridge-ai-module.md`, `.kiro/specs/ghostbridge-ai/`, `.env.example`.
- AI Web contracts: `AiRestController`, `requestvo/*`, `responsevo/*`, `AiExceptionAdvice`, `AiWebProperties`, `AiRequesterResolver`, `AiAccessInterceptor`; shared `ProblemDetailFactory` and `GhostBridgeSecurityAutoConfiguration`; GB-AI-003 task folder and its two review reports.

## Current behaviour
### Frontend scaffold (before this task)
| Item | State |
|---|---|
| Files | `package.json`, `tsconfig.json`, and `.gitkeep` in `src/components`, `src/api`, `src/views`. No source, no `index.html`, no bundler config. |
| Dependencies | `react ^19` (peer + dev), `@types/react ^19`, `typescript ^5.6`. No `react-dom`, router, bundler, test runner or linter. |
| Scripts | `typecheck` only (`tsc --noEmit`). No `build`, `dev`, `test` or `lint`. |
| tsconfig | `strict`, `ES2022`, `Bundler` resolution, `react-jsx`, `noEmit`, includes `src`. |
| API integration | None. No client, types, auth hook or mock. |
| Design system | None in this project (`ghostbridge-springboot-frontend` and `ghostbridge-app-frontend` are also empty). |

### AI Web API (read from source, not guessed)
All endpoints are under `/api/v1/ai`, authenticated, and require the role `AI_USER` (configurable, `ghostbridge.ai.web.required-role`). `Cache-Control: no-store` on success.

| Endpoint | Request VO | Response VO |
|---|---|---|
| `POST /generate` | `AiGenerateRequestVO`: `promptName` (required, `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`), `promptVersion` (int 1..1000, optional), `variables` (≤32 entries; key `[A-Za-z][A-Za-z0-9_]{0,63}`; value non-null, ≤16384 UTF-8 bytes), `untrustedContent` (≤32768 bytes), `modelId` (≤128 chars), `options` {`maxOutputTokens` 1..32768, `temperature` 0.0..1.0} | `AiGenerateResponseVO`: `requestId`, `model`, `text`, `structured` (JSON, optional), `usage`, `durationMs` |
| `POST /generate/structured` | `AiStructuredGenerateRequestVO`: `schemaName` (required, `[a-z0-9][a-z0-9-]{0,63}`, must be operator-approved), `variables`, `untrustedContent`, `options` (no `modelId`, no `promptName`) | `AiStructuredGenerateResponseVO`: `requestId`, `schemaName`, `model`, `structured`, `usage`, `durationMs` |
| `GET /provider/status` | none | `AiProviderStatusResponseVO`: `configured` (boolean), `status`, `provider`, `model`, `capabilities[]` |
| `GET /usage` | none | `AiUsageResponseVO`: `scope`, `historyLimit`, `requestCount`, `successCount`, `failureCount`, `requestsWithTokenUsage`, `inputTokens`, `outputTokens`, `totalTokens`, `rateLimit` {`limitPerMinute`, `remaining`, `resetInSeconds`, `maxConcurrent`} |

`AiTokenUsageResponseVO`: `inputTokens`, `outputTokens`, `totalTokens`, `estimatedCost` (all optional; omitted when the provider did not report them). Null fields are omitted (`NON_NULL`).

Key facts that shape the UI:
- **There is no free-form prompt.** `/generate` invokes a registered prompt template by name plus variables. Only template `generic-structured` (v1, variable `text`, schema `generic-summary`) ships in core.
- **There is no discovery endpoint** for prompt names or approved schema names. The default approved schema is `generic-summary` (`AiWebProperties`); operators can configure more.
- Unknown request fields (including a spoofed `requesterId`) are rejected with 400.
- Budget data is global and is **not** exposed by `/usage`; usage is the caller's slice of a bounded retained history (`scope` = `RETAINED_HISTORY`, `historyLimit` entries), not lifetime usage.
- `status` reflects configuration, not a live probe (`OllamaAiProvider.status()` always reports ready; SEC-M4-11).
- Errors are RFC 9457 `application/problem+json` with `status`, `detail`, `code`, optional `correlationId`, `timestamp`, `reason` (AI errors), `errors[]` {`field`,`message`} (validation). 413 covers size violations (body limit filter, `@Size`, `@MaxUtf8Bytes`). 429 carries `Retry-After` (whole seconds) when known. 401 code `AUTHENTICATION_REQUIRED`, 403 code `ACCESS_DENIED`.
- Authentication: **no application authentication exists.** The shared chain is stateless by default (credentials via an explicit header, CSRF disabled). With `ghostbridge.security.stateless=false` sessions are used and Spring CSRF protection is on. Until an application supplies a `GhostBridgeHttpSecurityCustomizer`, every call returns 401.
- CORS: restrictive (no origin allowed by default); exposed headers default to `X-Correlation-Id` only, so `Retry-After` is not readable cross-origin unless configured (SEC-M4-10).

## Functionality that must be preserved
- Backend API contracts and Maven module structure (no backend change except the Milestone 4 closure test).
- Credentials never reach the browser; the frontend never talks to Ollama.
- `.kiro` agents, steering and specs.

## Gaps and risks found
- Everything in the frontend is missing: bundler, entry, routing, API client, types, components, tests, lint.
- API cannot be exercised end to end without authentication; a development mock is the only way to build and demo the UI.
- Playground "prompt input" must map to template variables, not a raw prompt.
- Node 24 and npm are installed under `~/.nvm` but are not on the default `PATH`; Maven is under `~/.sdkman`. Commands in this task export `PATH`/`JAVA_HOME` explicitly (JDK 21).

## Evidence
`ls`/`cat` of the scaffold, source reads of the classes above, `mvn -o -B clean verify` (BUILD SUCCESS, all 10 reactor projects) on 2026-11-23.
