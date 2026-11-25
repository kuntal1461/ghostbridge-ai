# Implementation Plan — GB-APP-001 (Milestone 6)

Design, with the **as-built deviations** recorded in implementation.md. Where this plan and implementation.md differ, implementation.md is authoritative.
User-directed architecture (2026-11-24): React App frontend → `AppAiController` (App Web) → `AppAiService` (App Core) → `AiClient`/coordinator (AI Core) → `OllamaAiProvider` → local Ollama; one Spring Boot application; AI Web is not called over HTTP and has no second public controller.

## 1. Target architecture
```
Browser ──https──▶ Reverse proxy (Caddy or nginx, TLS, security headers, body limit)
                     ├─ /            → static SPA (ghostbridge-app-frontend build)
                     └─ /api, /actuator/health → ghostbridge-app-web (Spring Boot, port 8080, internal only)
                                           │ SecurityFilterChain (shared) + login/session/CSRF (GB-APP-001)
                                           │ AiRestController → AiApiService → AiClient (DefaultAiCoordinator)
                                           ▼
                                   OllamaAiProvider (local mode, http, no credential)
                                           ▼
                                   Ollama container (internal network only, port 11434 never published)
```
Trust boundaries: (1) Internet → proxy (TLS, size limits); (2) proxy → app (only proxy network, trusted forwarded headers); (3) app → Ollama (private network, allow-listed host); (4) browser ↔ app is same-origin, credentials = session cookie + CSRF header. Nothing in the browser can name a model URL; the frontend only sees provider name/model from `/provider/status`.

## 2. Components and ownership (no duplication)
| Concern | Reused as is | New (Milestone 6) | Module |
|---|---|---|---|
| Application class | — | `GhostBridgeApplication` | `ghostbridge-app-web` |
| Config | shared defaults, `ghostbridge.*`, `ghostbridge.ai.*` | `application.yml`, `application-dev.yml`, `application-prod.yml`; `AppProperties` (`ghostbridge.app.users[*]`, session limits) | app-web (properties record in `config`) |
| AuthN | shared chain, `GhostBridgeHttpSecurityCustomizer` | one customizer bean enabling form-style login processing at `/api/v1/auth/login`, logout at `/api/v1/auth/logout`, session policy, JSON success/failure handlers; in-memory `UserDetailsService` from `AppProperties`; `LoginAttemptLimiter` | app-web (`security`, `config`) |
| Auth API | `ProblemDetail` errors | `AuthRestController`: `GET /api/v1/auth/csrf` (token + header name), `GET /api/v1/auth/me`; `AuthMeResponseVO`, `AuthCsrfResponseVO` | app-web (`restcontroller`, `responsevo`) |
| AuthZ | `AiRequesterResolver`/interceptor (`AI_USER`), method security, actuator role | role names in config only | — |
| AI API | `ai-web` controllers, advice, limiter | none | ai-web |
| Local model | GB-AI-005 properties/provider | profile values only | app-web yml |
| Frontend | AI console, API client, auth strategies, mock | app shell, login page, auth context/gate, route wiring, style import of the console, API base from env | `ghostbridge-app-frontend` (+ a small export addition in `ghostbridge-ai-frontend`) |
| Deployment | `.env.example` | `deploy/` (Dockerfile(s), compose, proxy config, env example, runbook) | repo `deploy/` (new folder: PROJECT_STRUCTURE.md updated when created) |

`ghostbridge-app-core` stays empty in Milestone 6 (no business logic yet); a user store service is deliberately kept in `app-web/config` so no persistence abstraction is invented before it is needed.

## 3. Authentication and authorization design
**Recommended: server-side session + CSRF (cookie auth).** Reasons: browser-only users; HttpOnly cookie keeps credentials away from JavaScript (XSS cannot read them); the AI frontend already supports `sessionCookieAuth({csrfHeaderName, getCsrfToken})`; the shared chain already switches CSRF on when `ghostbridge.security.stateless=false`; no JWT signing keys, token storage or refresh logic; no new dependency.
| Option | Verdict |
|---|---|
| Session cookie + CSRF (recommended) | Simplest secure fit for one origin; limits: sticky/single instance (fine for one VPS), session store in memory (restart logs users out; acceptable). |
| Bearer JWT (resource server) | Needs `spring-security-oauth2-*` (absent from the local Maven repo), key management, token storage in JS (XSS exposure) or refresh cookies; better only with a real IdP. |
| OIDC/SSO | Future: plug into the same customizer; out of scope. |
| HTTP Basic | Browser credential prompts, no logout, credentials on every request; rejected. |

Flow: `GET /api/v1/auth/csrf` (anonymous allowed; also creates the session) → `POST /api/v1/auth/login` (`application/x-www-form-urlencoded` username/password + CSRF header, handled by Spring Security's `UsernamePasswordAuthenticationFilter` so session-fixation protection, context persistence and CSRF come from the framework) → 200 `AuthMeResponseVO {username, roles}` / 401 generic problem → `GET /api/v1/auth/me` → AI calls with `X-CSRF-TOKEN` on POSTs → `POST /api/v1/auth/logout` (invalidate session, delete cookie). `ghostbridge.security.public-paths`: `/api/v1/auth/csrf`, `/api/v1/auth/login` (login still requires CSRF), nothing else.
Users: `ghostbridge.app.users[0].username`, `.password-hash` (bcrypt `{bcrypt}…` from env, e.g. `GHOSTBRIDGE_APP_USERS_0_PASSWORD_HASH`), `.roles=[AI_USER]`. Startup validation: at least one user outside `local`/`dev`/`test`; hash format; roles valid; no duplicate usernames. Demo judge account created by the operator on the VPS; credentials go into the Devpost submission text, never into Git.
Login throttling: `LoginAttemptLimiter` (in-memory, bounded map, per username and per remote address, e.g. 5 failures / 15 min → 429 with `Retry-After`), reusing the same fixed-window approach as `AiRequesterLimiter` (shared helper only if it can be reused without new coupling; otherwise a small class).
Authorization: `AI_USER` for AI APIs (existing interceptor + method checks); `/api/v1/auth/me` any authenticated user; `ACTUATOR_ADMIN` as today.

## 4. Configuration (draft, not created yet)
```yaml
# ghostbridge-app-web/src/main/resources/application.yml  (committed, no secrets)
spring:
  application.name: ghostbridge-app
  profiles.default: dev
server:
  forward-headers-strategy: none            # prod: framework (behind the trusted proxy only)
  servlet.session.timeout: 30m
ghostbridge:
  security:
    enabled: true
    stateless: false                         # cookie session + CSRF (see section 3)
    public-paths: [/api/v1/auth/csrf, /api/v1/auth/login]
  cors:
    allowed-origins: []                      # same-origin deployment; add explicit origins only if split
  ai:
    provider: ollama
    ollama:
      local-enabled: ${GHOSTBRIDGE_AI_OLLAMA_LOCAL_ENABLED:true}   # explicit; dev default local
      local:
        base-url: ${GHOSTBRIDGE_AI_OLLAMA_LOCAL_BASE_URL:http://127.0.0.1:11434}
        model: ${GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL}                # required, no default
    timeout: 120s
    rate-limit: {requests-per-minute: 30, max-concurrent: 2}
    budget: {max-tokens: 200000, window: 1h}                       # required in prod (SEC-M4-01)
    web:
      requester-limit: {requests-per-minute: 10, max-concurrent: 1}
  app:
    users: []                                # supplied by environment/secret files
```
`application-dev.yml`: `server.servlet.session.cookie.secure=false`, HSTS off, mock provider allowed via `GHOSTBRIDGE_AI_PROVIDER=mock` for UI-only work. `application-prod.yml`: Secure cookie (default), `forward-headers-strategy: framework`, HSTS on, `ghostbridge.ai.ollama.local.base-url=http://ollama:11434` with `local.allowed-hosts: [ollama]`, budget on, actuator minimal. Developer-only overrides: untracked `application-local.yml`.
Restart is required for any change (properties bound once).

## 5. Frontend integration design
- `ghostbridge-app-frontend`: Vite + React + TypeScript, `react-router-dom`; depends on the AI console through a local file dependency (`"ghostbridge-ai-frontend": "file:../../ghostbridge-ai-module/ghostbridge-ai-frontend"`) with `resolve.dedupe` for `react`, `react-dom`, `react-router-dom`; **no copy** of AI code.
- Small additions to `ghostbridge-ai-frontend`: export the stylesheet entry (`./styles`), `AiConsole` accepting an optional `basePath`/routes so the host router owns navigation, and make the nav items overridable (so the host can add its own links).
- App shell: `AuthProvider` (state: loading | anonymous | authenticated), `LoginPage`, `RequireAuth`, header with user name and sign-out. On startup: `GET /auth/csrf` then `GET /auth/me`. `setAiAuthStrategy(sessionCookieAuth({csrfHeaderName, getCsrfToken}))` with the token kept in memory and refreshed after login/logout. A global handler turns `ApiError.kind === 'unauthenticated'` into an auth-state reset (redirect to login), `forbidden` shows the existing access-denied panel.
- Environment: `VITE_API_BASE_URL` empty (same origin); dev uses the Vite `/api` proxy; no mock in the integrated app by default (`VITE_AI_MOCK` stays an explicit dev switch).
- The mock payload and fixtures are aligned to the real `generic-summary` schema (done in this preparation).

## 6. CORS and error handling
Same-origin ⇒ CORS stays disabled. Split-origin option (documented, not default): `ghostbridge.cors.allowed-origins=[https://app.example.org]`, `allow-credentials=true`, `allowed-headers` + `X-CSRF-TOKEN`, `exposed-headers` + `Retry-After`, `cookie.same-site=none` is **not** used (SameSite=Strict stays; split-origin would need a same-site parent domain). Errors: shared `ProblemDetail` for 400/401/403/404/413/429/5xx; login failure = 401 `AUTHENTICATION_FAILED` without user hints; lockout = 429 + `Retry-After`; frontend renders via existing `ErrorPanel`. No stack traces, no provider detail (existing rules).

## 7. Deployment (future VPS) — design
`deploy/` (created at implementation time): `Dockerfile.app` (multi-stage Maven build or copy of the jar; JRE 21 distroless/temurin-jre, non-root, read-only root fs, `-XX:MaxRAMPercentage`, no JDK debug flags), `Dockerfile.web` (build SPA, serve with the proxy), `compose.yml` (services `proxy`, `app`, `ollama`; one internal network for `app`↔`ollama`, `ollama` has **no** `ports`, volume for models, resource limits), `Caddyfile` (automatic TLS, `/api` → `app:8080`, headers, body limit), `.env.example` (placeholders only), `RUNBOOK.md` (provision host, firewall allowing only 22/80/443, `ufw`, create secrets file with mode 600, `docker compose up`, pull the model once, create the demo user hash, health checks, upgrade, rollback, log inspection without prompts). Model/capacity: pick a model that fits RAM (3–4B class for CPU-only; measured laptop latency for `phi3:3.8b` 3.7–5.3 s per small request is **not** a VPS number); keep `ai.timeout` ≥ 120 s, `max-concurrent` ≤ 2, `max-output-tokens` ≤ 512, and run the model warm (`OLLAMA_KEEP_ALIVE`). Public availability must remain free during judging (hackathon rule): the VPS cost, not inference cost, is the budget.

## 8. Phases
| Phase | Goal | Deliverables | Gate |
|---|---|---|---|
| P0 | Decisions confirmed | open questions below answered | user sign-off |
| P1 | Runnable skeleton | `GhostBridgeApplication`, yml files, repackage on, `AppProperties`, startup validation, health test | `mvn clean verify`, jar starts |
| P2 | Authentication | customizer, user store, CSRF/me endpoints, limiter, tests | security tests green |
| P3 | Frontend host | app frontend, login/auth gate, console mounting, small ai-frontend export change | typecheck/lint/test/build |
| P4 | E2E | backend integration tests with stub Ollama + mock; browser E2E (Playwright if approved) against dev stack; manual live checklist with local model | tests + checklist recorded |
| P5 | Deployment | `deploy/` artefacts, runbook, dry run locally with compose (Docker availability to be checked) | compose up locally, ports verified closed |
| P6 | Reviews and closure | security + code-quality reviews, docs, final verify | no open Critical/High |

## 9. Technical decisions
| Decision | Alternatives | Reason |
|---|---|---|
| Cookie session + CSRF | JWT, OIDC, Basic | see section 3 |
| Spring Security login processing via customizer | custom login controller | reuses tested session-fixation/context/CSRF handling |
| In-memory users from config | database, file store | no persistence in scope; hashes from secrets; replaceable later behind `UserDetailsService` |
| Same origin via reverse proxy | CORS split | removes CORS/cookie complexity; matches SameSite=Strict |
| `file:` npm dependency on the AI console | copy, npm workspace, publish package | zero duplication, no registry, no new tooling |
| Playwright for browser E2E (proposed, needs approval) | Cypress, manual only | only new test dependency; optional gate |
| Caddy for TLS | nginx + certbot | fewer moving parts; nginx acceptable (decision open) |
| Ollama in its own container on an internal network | host install | isolation; port never published; host install also supported via loopback |

## 10. Open decisions (need the user before P1)
1. Approve **session cookie + CSRF** authentication (recommended) or choose another.
2. Where will the public demo run (VPS size, CPU-only vs GPU, domain/TLS) and which model should it serve? Local `phi3:3.8b` works on a laptop; a VPS must be sized for it.
3. Demo credentials: one shared judge account, or several users? (Hashes are created by the operator, not committed.)
4. Approve one new test dependency (Playwright) for browser E2E, or keep E2E to backend integration tests + a manual checklist.
5. Docker available on the dev machine for the compose dry run? **Checked then: no (Docker Desktop was installed but not running; started and used on 2026-11-25)** (neither Docker nor Podman is installed), so `deploy/` is unverified.

## 11. Rollback
Each phase is additive: delete `ghostbridge-app-web` application/config files and set `spring-boot.repackage.skip` back to true; remove `ghostbridge-app-frontend` sources and `deploy/`; the AI and framework modules are unchanged apart from the small, optional frontend export additions.
