# Implementation — GB-APP-001 (Milestone 6)

Work performed by Claude Code (not Kiro), 2026-11-24. Defaults chosen for the open decisions in implementation-plan.md section 10 because the user directed implementation: session cookie + CSRF authentication, in-memory configured users, same-origin deployment, no new Playwright dependency. These remain open for the user to confirm.

## Final module structure
```
ghostbridge-ai-core      + AiUsageSummary (dto), AiUsageSummarizer, AiProviderReadiness (service.impl): logic extracted from AI Web, reused by both
ghostbridge-ai-web       + rest-enabled switch (ConditionalOnAiRestApi), AiApiEndpoint marker; AiExceptionAdvice now covers any @AiApiEndpoint controller
ghostbridge-app-core     + AppAiService / DefaultAppAiService, AppAiGenerateCommand, AppAiStructuredCommand, AppAiProviderStatus,
                           AppAiProperties, AppCoreConfiguration, GhostBridgeAppLiterals
ghostbridge-app-web      + GhostBridgeApplication (the single entry point), AppAiController, AuthRestController, AppAiMapper,
                           AppSecurityProperties/Configuration/Validator, LoginAttemptLimiter, LoginThrottleFilter, handlers,
                           application.yml, application-dev.yml, application-prod.yml
ghostbridge-app-frontend + React app: sign-in, auth gate, user menu, hosts the AI console; npm workspace with the AI frontend
ghostbridge-ai-frontend  + small host hooks: header slot, form-encoded and 204 support, session-expired hook, exports, mock aligned to the real schema
deploy/                  + Dockerfile.app, Dockerfile.web, Caddyfile, compose.yml, .env.example, README (built and run locally with Docker (see test-cases.md))
package.json (root)      + npm workspace root (hoists React once)
```
No new Maven module. No new Maven or npm runtime dependency (the App frontend declares the same React/router/toolchain versions as the AI frontend; the workspace root adds no package).

## Request flow (as built)
`React → /api/v1/ai/** (same origin) → AppAiController → AiRequesterResolver (role AI_USER, trusted requester key) → AiRequesterLimiter permit → AppAiService → AiClient (DefaultAiCoordinator: global limits, approved model, schema validation, retry, telemetry) → OllamaAiProvider (local mode: no credential) → local Ollama`. All calls are in-process Java; there is no HTTP hop to AI Web, and AI Web's `AiRestController`/`AiApiService` are not registered in this application (`ghostbridge.ai.web.rest-enabled=false`), so there are no duplicate public AI endpoints. AI Web's interceptor (role check before body binding), body-size filter, `AiRequesterResolver`, `AiRequesterLimiter`, `AiApiMapper` and error advice remain in use. The API paths and RequestVO/ResponseVO contract are unchanged, so the AI frontend needed no endpoint changes.
Why the controller reuses AI Web beans: the per-user limit, role check and error mapping are web-layer concerns; copying them would have duplicated tested security code. The core logic that was reusable was moved down to AI Core instead (usage totals, readiness) and AI Web's service now uses it too.

## Authentication and authorization
- Session cookie (HttpOnly, SameSite=Strict, Secure by shared default; dev profile turns Secure off for http) + Spring CSRF (`ghostbridge.security.stateless=false`). Endpoints: `GET /api/v1/auth/csrf` (public; returns header name and token), `POST /api/v1/auth/login` (form post processed by Spring Security's filter via `GhostBridgeHttpSecurityCustomizer`, needs the CSRF token, answers `{username, roles}`), `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` (204, invalidates the session). The CSRF token is rotated by login and logout; the frontend refetches it.
- Users from `ghostbridge.app.security.users[*]` (username, `{bcrypt}` hash only, roles); plain, `{noop}` and malformed hashes are refused at startup; hashes are never printed (`toString`). Startup fails outside the `local|dev|test` profiles when no user is configured.
- Brute force: `LoginAttemptLimiter` (bounded, in memory) blocks a user name after 5 failures and a client address after 30, for 15 minutes, with 429 + `Retry-After`, before the password is checked. Failures answer one generic 401.
- Authorization stays server-side: shared deny-by-default chain, AI role interceptor, method checks, actuator role.

## Configuration
Single `application.yml` plus `application-dev.yml` and `application-prod.yml` in `ghostbridge-app-web`; no duplicated environment config elsewhere. Local Ollama: `local-enabled` (explicit, defaults to true in this application through an overridable placeholder), `local.base-url`, required `GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL` (no default). Dev binds to loopback (`server.address=127.0.0.1`).

## Frontend integration
npm workspace at the repository root so React exists once; the App frontend imports `ghostbridge-ai-frontend` (`AiConsole`, `requestJson`, `ApiError`, `sessionCookieAuth`, `setAiAuthStrategy`, `setUnauthenticatedHandler`) instead of copying code; one HTTP client (the AI frontend's) is used for both auth and AI calls; the base URL is empty (same origin; Vite proxies `/api` in development). The mock stays a development-only switch and is absent from production bundles (`check-dist` run for both builds).

## Deviations from the plan
- Same API paths kept (`/api/v1/ai/**`) instead of a new prefix: one contract for the existing frontend and tests.
- npm workspaces instead of a `file:` dependency with aliases: simplest way to guarantee one copy of React.
- `ConditionalOnAiRestApi`/`AiApiEndpoint` added to AI Web (not in the plan) to avoid duplicate endpoints while keeping its advice.
- Login failures use `AUTHENTICATION_REQUIRED` (no dedicated error code exists in the shared contract).
- `APP_ADMIN` role is not used yet (no admin feature exists).

## Verification (see test-cases.md for details)
Root `mvn clean verify` passes; both frontends pass typecheck, lint, tests, build; one manual end-to-end request through the packaged jar to local `phi3:3.8b` succeeded (structured JSON, 232/33 tokens, 6.7 s). Round 3 (final acceptance work): `deploy/` built and run with Docker Desktop and exercised through Caddy over HTTPS, including real local inference; 24 real-browser checks (headless Chrome) pass. Not verified: a public host (real certificate, public firewall, real remote client addresses, IPv6), Sonar and coverage.

## Local setup (development)
1. Ollama running locally with an installed model. 2. Create a bcrypt hash (`htpasswd -nbBC 10 x 'pw' | cut -d: -f2`) and export `GHOSTBRIDGE_APP_SECURITY_USERS_0_USERNAME`, `…_PASSWORD_HASH='{bcrypt}<hash>'` (single quotes), `…_ROLES_0=AI_USER`, `GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL=<model>`. 3. `mvn -B clean package -DskipTests` then `java -jar ghostbridge-app/ghostbridge-app-web/target/ghostbridge-app-web-0.1.0-SNAPSHOT.jar` (port 8080). 4. `npm install` at the repository root, then `npm run dev -w ghostbridge-app-frontend` (http://localhost:5173).

## Round-2 changes (code-quality gate, 2026-11-24)
- **Shared behaviour owned by AI Core** (both entry points depend on it, no new module dependency): `AiStructuredOutputs` (approved schema → prompt, structured-output check), `AiProviderReadiness.overview` + `AiProviderOverview` (safe provider status), `AiDurations.ceilSeconds`; the two client messages moved to `GhostBridgeAiLiterals`. `DefaultAiApiService` (AI Web) and `DefaultAppAiService` (App) now call these instead of carrying copies; `AppAiProviderStatus` and the App copy of the messages were removed.
- **Per-requester limit in the service layer**: `RequesterLimitedAppAiService` (app-web, `@Primary`, gated like the controller) wraps `DefaultAppAiService` and acquires the permit; `AppAiController` only resolves the requester, maps and delegates (it reads the limiter only to display the caller's own status).
- **Mapping**: `AppAiMapper` builds commands only and reuses `AiApiMapper.toOptions`; usage/provider-status responses come from `AiApiMapper.toUsageResponse/toProviderStatus` (one implementation for both controllers).
- **Defect found by a new test**: the session lifetime restored after login was read from the servlet context (0 in MockMvc, so a mis-set value would have meant "never expires" semantics). `AppLoginSuccessHandler` now takes the configured `server.servlet.session.timeout` (`ServerProperties`).
- Left as is, by decision: the two structured-schema allow-lists (`ghostbridge.app.ai.structured-schemas`, `ghostbridge.ai.web.structured-schemas`) stay separate properties because they configure different entry points (AI Web's is inactive in the App); the logic they feed is now shared.

## Round-3 changes (final acceptance, 2026-11-25)
- **Deployment** (`deploy/`): Caddy rebuilt with `caddy-ratelimit@v0.1.0` (edge limits per client address: 30/min `/api/v1/auth/*`, 60/min `/api/v1/ai/*`); CSP default only when the app sent none (`?`), HSTS with subdomains, Permissions-Policy; three networks (`edge`, `web`, `models`) so the proxy cannot reach Ollama; Ollama pinned by digest with `OLLAMA_NO_CLOUD=1` (found: the stock Ollama server tried to reach ollama.com at start-up; blocked by the network, now disabled), caps dropped; `pids_limit` everywhere; IPv4-only published ports; base images pinned by digest; stricter healthcheck; runbook gained public-host conditions, a tested model-pull procedure, and the allow-list section.
- **Code**: `AuthRestController` gated like the other security-dependent controllers; login key prefixes and session cookie name are constants; `AiDurations.retryAfterSeconds`; `AppAiProperties` default only when absent; login form a11y attributes; logout rejection handled in `UserMenu`; `AppRealHttpTest` asserts its preconditions.
- **Evidence** (`evidence/`): browser E2E script and results, proxy smoke and proxy security scripts and results, sanitized screenshots; scripts take the password from `DEMO_PASSWORD` (environment only).
