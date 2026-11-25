# Test Cases — GB-APP-001 (Milestone 6)

Types: unit, integration (Spring context, real security chain), real-HTTP (embedded server), component (frontend), manual-live (local Ollama, opt-in). No test uses Ollama Cloud, a credential, or network access beyond loopback. Status reflects executed runs only (see execution log).

| ID | Type | Scenario | Expected | Test | Status |
|---|---|---|---|---|---|
| TC-1 | integration | Context starts; AI Web controller absent, `AppAiController` present, one handler per AI path | startup succeeds (no ambiguous mapping) | `AppAuthenticationTest.theAppControllerOwnsTheAiPaths…` | Passed |
| TC-2 | integration | Anonymous call to every AI endpoint and `/auth/me` | 401 (403 without a CSRF token, because CSRF is checked first) | `AppAuthenticationTest.everyAiEndpointRejectsAnonymousCallers` | Passed |
| TC-3 | integration | csrf → login → me → generate → usage → status → logout → me | 200s then 204 then 401; no hash in `/me`; `no-store` | `AppAuthenticationTest.theFullSessionFlowSignsInCallsTheAiApiAndSignsOut` | Passed |
| TC-4 | integration | Login without or with a forged CSRF token; AI POST and logout without token while signed in | 403 | `AppAuthenticationTest.loginRequiresTheCsrfToken…` | Passed |
| TC-5 | integration | Signed in without `AI_USER` | 403 `ACCESS_DENIED` on all AI endpoints | `AppAuthenticationTest.aSignedInUserWithoutTheAiRole…` | Passed |
| TC-6 | integration | Wrong password vs unknown user | identical generic 401, no user or password in body | `AppAuthenticationTest.wrongPasswordAndUnknownUser…` | Passed |
| TC-7 | integration | Repeated failures for one user name | 429 + `Retry-After` even with the correct password | `AppAuthenticationTest.repeatedFailuresLockTheUserName…` | Passed |
| TC-8 | unit | Limiter thresholds, windows, reset, independence | as specified | `LoginAttemptLimiterTest` (4) | Passed |
| TC-9 | integration | Per-user AI limit through the App endpoint | third request in the window → 429, `Retry-After`, reason `RATE_LIMITED` | `AppAiPerRequesterLimitTest` | Passed |
| TC-10 | integration | Whole path with the real local-mode provider against a loopback stub | structured JSON, usage 21/6/27 tokens, request has no `Authorization`, model = local model, usage deltas for the caller, status hides the endpoint | `AppLocalOllamaFlowTest` | Passed |
| TC-11 | integration | Unapproved model | 400 "Model is not approved"; the model server is not contacted | `AppLocalOllamaFlowTest` | Passed |
| TC-12 | real-HTTP | Session cookie attributes; security headers on API errors | HttpOnly, Secure, SameSite=Strict; nosniff, DENY, CSP; problem+json without stack | `AppRealHttpTest` | Passed (see flakiness note) |
| TC-13 | unit | Configuration validation: hashes (plain, `{noop}`, malformed rejected), names, roles, duplicates, attempt limits, prod refuses empty user list | as specified | `AppSecurityConfigurationTest` (5) | Passed |
| TC-14 | unit | `AppAiService`: command→core request with the trusted key, approved schema → prompt mapping, unapproved schema rejected before the model, status without detail, usage per requester | as specified | `DefaultAppAiServiceTest` (6), `AppAiPropertiesTest` (5) | Passed |
| TC-15 | integration | AI Web with `rest-enabled=false`: no controller bean, advice and interceptor still apply to another `@AiApiEndpoint` controller | 404/401, `Retry-After` and `reason` mapped | `AiRestDisabledTest` (ai-web) | Passed |
| TC-16 | unit | Shared helpers moved to AI Core | per-requester totals, no estimation, readiness from the named provider, failing probe = not ready | `AiUsageSummarizerTest` (ai-core) | Passed |
| TC-17 | regression | AI Core and AI Web suites | unchanged and green | root build | Passed (final: ai-core 390 with 4 skipped, ai-web 88; see the final run) |
| TC-18 | component | App frontend: anonymous → sign-in page; session kept; sign-in sends a CSRF-protected form post and refreshes the token; return to the requested page; generic failure message and cleared password; 429 with `Retry-After`; no double submit; unreachable backend; expired session returns to sign-in; sign-out; only same-origin `/api/v1/(auth\|ai)` calls; nothing in web storage | as specified | `ghostbridge-app-frontend/src/App.test.tsx` (12) | Passed |
| TC-19 | component | AI frontend additions: form body, 204, unauthenticated handler, header slot | as specified | `client.test.ts`, `App.test.tsx` (ai-frontend, 156 total) | Passed |
| TC-20 | build | Both production bundles | no mock code, no secret patterns | `scripts/check-dist.mjs` in both builds | Passed |
| TC-21 | manual-live | Packaged jar, `dev` profile, real local `phi3:3.8b`, real HTTP with a cookie jar | see execution log | by hand with curl | Passed (once) |
| TC-24 | unit | Per-requester limit enforced in the service; structured schema allow-list; seconds rounding | second request rejected before the model call; unapproved schema is INVALID_INPUT; 1 ms → 1 s | `RequesterLimitedAppAiServiceTest` (4), `AiStructuredOutputsTest` (3), `AiDurationsTest` (3) | Passed |
| TC-25 | integration | Lockout scoped to the client address; anonymous session 120 s then normal lifetime after sign-in; session id changes on login | as specified | `AppAuthenticationTest` (3 new) | Passed |
| TC-22 | browser | Real browser (headless Google Chrome 158 via puppeteer-core) against the Docker stack through Caddy/HTTPS: sign-in page, wrong password, login + return to requested page, cookie flags, no web storage, dashboard/provider/playground/usage pages, validation error state, real structured inference with token usage, usage totals, CSRF refusal, lost session → sign-in, logout, protected routes after logout, only same-origin calls | 24 checks | `evidence/browser-e2e.mjs` | **Passed (24/24)** on the final images |
| TC-23 | deployment | `deploy/` compose stack built and run with Docker Desktop (see "Deployment verification" below) | images build, stack healthy, ports closed, no cloud contact | `evidence/proxy-smoke.sh`, `evidence/proxy-security.sh` + manual `docker` checks | **Passed locally**; public-host items not verified |

## Execution log
| Date | Command | Result |
|---|---|---|
| 2026-11-24 | `mvn -o -B clean verify` (root, JDK 21) | BUILD SUCCESS: springboot 31 + 55, ai-core 386 (3 skipped), ai-web 88, app-core 9, app-web 24 |
| 2026-11-24 | `npm run typecheck|lint|test|build -w ghostbridge-ai-frontend` | clean; 156 tests; build + check-dist OK |
| 2026-11-24 | same for `ghostbridge-app-frontend` | clean; 12 tests; build + check-dist OK; `npm audit`: 0 vulnerabilities (workspace) |
| 2026-11-24 | Manual end-to-end: `java -jar ghostbridge-app-web-0.1.0-SNAPSHOT.jar` (profile dev, port 8089), `OLLAMA_API_KEY` unset, local Ollama `127.0.0.1:11434`, model `phi3:3.8b`, demo user created with a bcrypt hash in a temporary environment file (deleted afterwards) | health UP; anonymous AI call 401; login without CSRF 403; wrong password generic 401; sign-in 200 `{demo, [AI_USER]}`; `/provider/status` AVAILABLE ollama phi3:3.8b; **structured request HTTP 200 in 6.7 s (durationMs 6666), schema-valid JSON, tokens 232 in / 33 out**; `/usage` count 1, rate limit remaining 9/10; logout 204, `/me` then 401. Log scan: password, hash, prompt text, `Authorization`/`Bearer`, `ollama.com`: 0 matches. The app's only outbound connection was `127.0.0.1:11434`. |
| 2026-11-24 | Vite dev server (App frontend) with `DEV_PROXY_TARGET` at the running app | `/` 200; `/api/v1/auth/csrf` proxied 200; anonymous `/api/v1/ai/usage` proxied 401 |

Flakiness note (superseded by the round-2 investigation below): one early run of `AppRealHttpTest` failed (401 on `/auth/csrf`, no security headers) and passed on every later run (3 reruns, plus the full builds). The cause was not determined; it is reported to the reviewers and listed as a remaining risk.

## Round-2 execution log (2026-11-24)
| Command | Result |
|---|---|
| `mvn -o -B clean verify` (root) | BUILD SUCCESS: springboot-core 31, springboot-web 55, ai-core 388 (0 failures, 4 skipped: 3 opt-in live Ollama tests + 1 configuration-metadata test whose assumption "metadata generated by the annotation processor" is not met on this JDK), ai-web 88, app-core 9, app-web 30. `ollama.com` appears 0 times in the build log; `OLLAMA_API_KEY` was not set; live tests did not run. |
| `npm run lint / typecheck / test / build` in `ghostbridge-ai-frontend` | clean; 17 files, 156 tests; build + `check-dist` OK |
| same in `ghostbridge-app-frontend` | clean; 15 tests (3 new: unsafe post-login targets; token now fetched before login); build + `check-dist` OK |

### CQ-05 investigation: `AppRealHttpTest`
- Original failure (from the session record): the very first run failed **both** tests: `GET /api/v1/auth/csrf` returned 401 (public path) and `GET /api/v1/ai/usage` returned a 401 without `X-Content-Type-Options`, i.e. the response did not come through the shared security filter chain. The next run, minutes later, only after adding a diagnostic message to the assertion, passed, and every run since passed.
- Reviewed for the listed causes: server startup uses `RANDOM_PORT` (dynamic, no fixed port); no shared mutable state, no ordering dependency (the two tests are read-only GETs with no cookie reuse and no CSRF/session state); no sleeps or async behaviour; configuration comes from `application-test.yml`, nothing environment-dependent (no env variable is read); the `HttpClient` per call was replaced by one static client (hygiene only).
- Reproduction attempts: 10 consecutive runs of the class in separate Maven/JVM invocations (10/10 passed), 3 runs of all App Web test classes with `-Dsurefire.runOrder=random` (3/3 passed), several full `clean verify` builds (all passed). Not reproduced.
- Side finding: running the module with `surefire:test -pl` (without `-am`) resolves `ghostbridge-ai-core` from the local repository and fails the whole context with `NoClassDefFoundError` — a stale-artifact failure that looks different from the original symptom but shows that stale module classes can break context start-up.
- **Root cause: not confirmed.** Most plausible: the first execution used an inconsistent `target` state (the repository had earlier incremental-compile problems that required `clean` builds with `-am`), so the security configuration was not the one under test. Residual risk: low; if it recurs, the assertion messages now print status, headers and body for both tests, which identifies the responder immediately.

Final run after round-2 fixes: `mvn -o -B clean verify` BUILD SUCCESS: springboot-core 31, springboot-web 55, ai-core 389 (4 skipped, as above), ai-web 88, app-core 9, app-web 33 (new: cookie-less CSRF session lifetime, decorator release-on-failure and pass-through, `requireStructured`, `AiDurationsTest`). Frontend results above were taken before the last two backend-only edits; the frontends were not changed afterwards.

## Deployment verification (2026-11-24, Docker Desktop 28.4.0, local, `SITE_ADDRESS=localhost`, model `phi3:3.8b` copied into the ollama volume)
| Check | Result |
|---|---|
| `docker compose build` (app: `eclipse-temurin:21-jre` + jar; proxy: Node build of the React app + Caddy 2.11.9 built with `caddy-ratelimit`) | Built (twice: initial and after the hardening changes; app image rebuilt again for the final code) |
| Stack start | `app` healthy in ~5-25 s, `proxy` and `ollama` up; `ai.ollama.selected mode=local model=phi3:3.8b`, profile `prod`, started in ~3-4 s |
| SPA through Caddy | `/` and a deep link return the app (200); HTTP redirects to HTTPS (308) |
| App controllers registered / reachable | `/api/v1/auth/*` and `/api/v1/ai/*` answer through the proxy (401/403/200 as designed); `/actuator/health` 200; `/actuator/env`, `/actuator/metrics` 404 at the edge |
| App → self-hosted Ollama | `provider/status` AVAILABLE ollama phi3:3.8b; structured request 200, schema-valid JSON, 232-237 in / 26-33 out tokens, 27-40 s (CPU container) |
| Port 11434 | not published: `docker compose port ollama 11434` empty, `NetworkSettings.Ports` `{}`; only the proxy publishes 80/443. (A process listening on host port 8080 belongs to another program, not to this stack, which publishes no 8080.) |
| Cloud API key | none configured or needed (`OLLAMA_API_KEY` unset; local mode sends no Authorization header) |
| Unexpected cloud endpoint | App container: no route to the internet (`connect: Network is unreachable` to 1.1.1.1:443); app log has 0 matches for `ollama.com`, `Authorization`, `Bearer`, `bcrypt`, the demo password, prompt text. **Finding:** the stock Ollama server itself tried to reach `ollama.com` (cloud model-cache refresh) at start-up; it failed because the `models` network has no route out. Fixed by `OLLAMA_NO_CLOUD=1`; the log now reads "Ollama cloud disabled: true". |
| Restart/health | `docker compose restart app` healthy again in ~9 s; an in-container SIGTERM of the java process (exit not requested through Docker) was restarted by `unless-stopped` (restartCount 1) and became healthy; a plain `docker kill` was **not** restarted on Docker Desktop (Docker treats it as a manual stop), so crash recovery was shown only through the in-container exit |
| Hardening in effect | app: user `ghostbridge` (uid 999), read-only root fs, all capabilities dropped, no-new-privileges, 1 GB limit, on `web` and `models` networks only; proxy: read-only, NET_BIND_SERVICE only; ollama: pinned digest, caps dropped, no-new-privileges, on `models` only (the proxy cannot reach it) |

## Reverse-proxy security (real HTTP through Caddy, `evidence/proxy-smoke-result.txt`, `proxy-security-result.txt`)
| Property | Result |
|---|---|
| Authentication / authorization | anonymous AI and `/auth/me` calls 401; AI POST without CSRF 403; after logout `/me` and AI calls 401 |
| Session cookie over HTTPS | `Set-Cookie: JSESSIONID=…; Path=/; Secure; HttpOnly; SameSite=Strict` |
| CSRF | login without token 403; AI POST without token 403; forged token 403 (browser and curl) |
| Security headers | SPA: CSP, HSTS (`max-age=31536000; includeSubDomains`), nosniff, DENY, Referrer-Policy, Permissions-Policy, no `Server`; API: the application's own stricter CSP (`default-src 'none'; frame-ancestors 'none'`) is kept, `Cache-Control: no-store` |
| Client address behind the proxy | 7 failed logins for one user name, each with a different forged `X-Forwarded-For`/`X-Real-IP`: attempts 6 and 7 are 429, so a client-supplied header does not change the key. A second client on another address (a container on the edge network) was **not** locked out by the host's failures (401, not 429): the application tells client addresses apart (S-1 verified for a private second address) |
| Login lockout | 5 failures then 429 + `Retry-After`; unknown user names behave the same |
| Edge rate limit | `/api/v1/auth/*`: 40 quick requests with forged XFF → 429 after the zone limit of 30/min counted from the real peer; `Retry-After` present |
| Anonymous session expiry | token of an anonymous session older than 120 s → login refused 403 |
| Bypass of App controllers | the application and Ollama publish no host port; Ollama is on a network the proxy is not on |
| Not verified | a real public address and real TLS certificate (Caddy used its local CA, `curl -k`); a client behind another proxy/CDN; an absolute session lifetime (S-4, accepted); signed-in 30-minute expiry (configured and unit tested only) |

## Final run after all changes (2026-11-24)
`mvn -o -B clean verify` BUILD SUCCESS: springboot-core 31, springboot-web 55, ai-core 389 (4 skipped: 3 opt-in live Ollama tests + 1 metadata-assumption test), ai-web 88, app-core 9, app-web 33; no `ollama.com` in the log. Both frontends: lint, typecheck, tests (ai 156, app 15), build + `check-dist` clean. Deployment smoke, proxy security and browser E2E were re-run on images rebuilt from the final jar: all as above.

## CQ-05 conclusion (final)
Hypotheses checked against the original record (first run only, both tests failed with 401 and no security headers, the next run passed): test isolation (no shared state, read-only GETs), stale build artifacts (plausible: earlier incremental-compile problems; a stale `ghostbridge-ai-core` really does break context start-up when the module is run without `-am`), security filter initialization (a new `@BeforeEach` now asserts the filter chain bean and `/actuator/health` before each test, so a missing chain fails with an unambiguous message), test profiles (`@ActiveProfiles("test")` plus an explicit property; the same configuration passes in 10 isolated JVM runs and 3 random-order runs), container/environment differences (the test runs in-JVM on a random port; the identical application in Docker behaved correctly), authentication configuration (none involved: `/csrf` is a public path), Spring context reuse (this test has its own property set, hence its own context), startup ordering (Tomcat is started before the test method). **No reproducible cause.** Residual risk: low. **Recommendation: accept as a documented, unreproduced, low risk**, with the new preconditions and failure messages in place to diagnose a recurrence; no retries or sleeps were added.
