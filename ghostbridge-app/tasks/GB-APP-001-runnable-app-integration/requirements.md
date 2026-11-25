# Requirements — GB-APP-001 (Milestone 6)

## Objective
Deliver a runnable, authenticated platform that connects **React frontend → Spring Boot App → AI Web → AI Core → Local Ollama**, configured centrally, deployable to a single private-network VPS, and demonstrable to hackathon judges without paid inference. The SOAP-to-REST workflow is out of scope; this milestone builds the platform it will run on.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | `ghostbridge-app-web` contains the only executable application (`com.ghostbridge.GhostBridgeApplication`, default scanning from `com.ghostbridge`); repackaging is enabled; libraries stay plain JARs. |
| FR-2 | One centralized `application.yml` (plus `application-dev.yml`, `application-prod.yml`) in `ghostbridge-app-web/src/main/resources`; no duplicate environment config in other modules; secrets only through environment/secret store. |
| FR-3 | Authentication: browser users sign in with username and password, receive an HttpOnly session cookie, can query who they are, and can sign out. Authorization: role `AI_USER` required for `/api/v1/ai/**` (existing), `APP_ADMIN` reserved for administration; actuator role unchanged. |
| FR-4 | A user store configured from `ghostbridge.app.users[*]` (username, bcrypt hash, roles); hashes come from environment/secret store, never from committed files; at least one demo user for judges. |
| FR-5 | Local Ollama is selected by `ghostbridge.ai.ollama.local-enabled=true` with `local.base-url` and `local.model` from configuration (dev: loopback; prod: private container/VPN name via `local.allowed-hosts`); no cloud key anywhere in the stack. |
| FR-6 | `ghostbridge-app-frontend` is a React app that hosts the existing AI console (`ghostbridge-ai-frontend`) behind a login screen and an auth gate; on 401 it shows the login screen, on 403 the access-denied state; it never stores credentials in web storage. |
| FR-7 | CORS: same-origin by default (reverse proxy / Vite proxy); cross-origin allowed only through explicit `ghostbridge.cors.*` origins (no wildcard, credentials only with explicit origins, `Retry-After` and CSRF header exposed when used). |
| FR-8 | All API errors use the shared `ProblemDetail` contract; the frontend maps them through the existing `ApiError` kinds; login failures return a generic 401 (no user enumeration). |
| FR-9 | Operational endpoints: `/actuator/health` (liveness/readiness) public; everything else restricted (existing). |
| FR-10 | Deployment artefacts for a single VPS: container images, compose file, reverse proxy with TLS, Ollama on an internal-only network, documented environment variables and model provisioning. |
| FR-11 | End-to-end test suite and a documented manual live checklist (login → AI call → local model) that makes no paid or cloud request. |

## Non-functional requirements
| ID | Requirement |
|---|---|
| NFR-1 | Java 21, Spring Boot 3.5, constructor injection, centralized literals, cognitive complexity ≤ 15, `com.ghostbridge` packages only, unique class names. |
| NFR-2 | No new Maven modules; no new Maven or npm dependency without a documented justification (target: none for the backend; React Router and the existing AI frontend for the app frontend). |
| NFR-3 | `mvn clean verify` passes from the root; frontend `typecheck`, `lint`, `test`, `build` pass. |
| NFR-4 | Startup fails clearly (never silently degrades) on unsafe configuration: missing user hashes in `prod`, undetermined Ollama mode, mock provider in `prod`, wildcard CORS, insecure cookies in `prod`. |
| NFR-5 | CPU-only VPS must stay responsive: bounded concurrency, timeouts and token limits sized to the chosen model. |

## Security requirements
| ID | Requirement |
|---|---|
| SEC-1 | Session cookie: HttpOnly, Secure (except dev http), SameSite=Strict, session fixation protection (new session id on login), idle and absolute timeouts, invalidated on logout. |
| SEC-2 | CSRF protection on every state-changing request (stateful mode); the token is delivered by an authenticated-or-anonymous `GET` endpoint and held in memory by the frontend (`sessionCookieAuth`), never in web storage. |
| SEC-3 | Brute-force defence on login: bounded attempts per username and per client address with lockout window; generic failure message; no password or hash in logs; constant-time comparison (bcrypt). |
| SEC-4 | Ollama is reachable only from the app (loopback or an internal network); port 11434 is never published; the app never forwards the browser's request to a model URL; no credentials are sent in local mode. |
| SEC-5 | The frontend never receives provider endpoints, keys or configuration; prompts and responses are not logged (existing rule). |
| SEC-6 | Production reverse proxy sets HSTS, a CSP that allows only the SPA's own origin (`connect-src 'self'`), `X-Content-Type-Options`, `frame-ancestors 'none'`, request body limit, and passes `X-Forwarded-*` only from the proxy (Spring `forward-headers-strategy` configured, trusted proxy only). |
| SEC-7 | Secrets (user hashes, any future API key, TLS keys) only in environment/secret files outside Git (`.env`, `secrets/` are ignored); Gitleaks/regex hook remains in force. |
| SEC-8 | A SOAP/WSDL-derived document is untrusted input (future milestone); Milestone 6 adds no document handling. |

## Out of scope
SOAP-to-REST generation workflow, database persistence, user self-registration or password reset, OIDC/SSO (extension point documented), multi-instance scaling and distributed rate limiting, GPU provisioning decisions, Ollama Cloud, Bedrock, a second frontend theme, internationalisation.

## Acceptance criteria
- [ ] `java -jar ghostbridge-app-web.jar` starts with `dev` profile and local Ollama; `GET /actuator/health` is UP.
- [ ] Anonymous `POST /api/v1/ai/generate` → 401; wrong role → 403; signed-in `AI_USER` → 200 with a response from the local model (live, manual) or the mock (automated).
- [ ] Login/logout/me/CSRF flow works through the browser against the dev stack and in automated tests; CSRF-less POST → 403; brute-force lockout verified.
- [ ] The AI console works inside the app frontend: Dashboard, Playground, Structured Output, Provider Status, Usage.
- [ ] Production profile refuses to start with each unsafe configuration in NFR-4 (tested).
- [ ] Compose stack starts on a clean host with Ollama unreachable from outside; TLS reverse proxy serves SPA and API on one origin.
- [ ] Security and code-quality reviews run with no unresolved Critical/High; `mvn clean verify` and frontend checks pass; no secret in the repository.
