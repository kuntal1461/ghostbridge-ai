# Security Review — GB-APP-001 (Milestone 6)

Reviewer: `ghostbridge-security-reviewer` (independent, read-only; ran no build, test or scan). Date: 2026-11-24. Round 1.
Result: **no Critical/High; pass with conditions.** `deploy/` is not approved until it is built and run with Docker.

Scope: login/session, brute-force limiter, authorization of `/api/v1/ai/**` via `AiAccessInterceptor`, duplicate-endpoint switch, credentials/SSRF in local mode, headers/errors, frontend token handling, deploy files, tests.

## Findings and disposition

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| S-1 | Medium | Per-address lockout keyed on `getRemoteAddr()`; behind the proxy every client may look like the proxy, so 30 failures lock everyone | **Fixed.** `application-prod.yml` now uses `forward-headers-strategy: native` (Tomcat RemoteIpValve) with `internal-proxies` limited to private ranges. Not exercised through a real proxy (no Docker): verify in the deploy run. |
| S-2 | Medium | User-name lock lets anyone lock the published demo user | **Fixed.** User key is now `u:<name>@<address>`; a stranger can only lock the name for their own address. Per-address cap (30) still bounds guessing. |
| S-3 | Medium | Public `GET /auth/csrf` creates a session per request (memory DoS) | **Mitigated.** Signed-out sessions get a 120 s idle lifetime (`ANONYMOUS_SESSION_SECONDS`), restored to the server timeout on login. No edge rate limit yet: open for deploy. |
| S-4 | Low | No absolute session lifetime | **Accepted for the demo**, recorded as an open item (idle 30 min only). |
| S-5 | Low | Nothing refuses an insecure cookie under `prod` (NFR-4) | **Fixed.** `AppSecurityValidator` rejects `cookie.secure=false` with `prod`; test added. |
| S-6 | Low | Stale CSRF token after idle expiry makes login 403 | **Fixed.** `signIn` always fetches a fresh token first; post-login refresh failure is non-fatal. Tests updated. |
| S-7 | Low | `from` redirect target not validated | **Fixed.** `safeDestination` accepts only a single-slash in-app path; test for `//x`, `/\x`, `https://x`. |
| S-8 | Low | Limiter eviction can drop locked keys at 10 000 keys | Accepted (needs ≥330 addresses); documented. |
| S-9 | Low | Ollama image unpinned/unhardened, shares network with proxy | Open for the deploy run (pin digests, split networks). |
| S-10 | Low | Caddy headers override app CSP on API; no HSTS subdomains, no edge rate limit | Open for the deploy run. |
| S-11 | Info | `$` in a bcrypt hash is interpolated by Compose | Documented (single-quote the value) in `deploy/README.md`. |
| S-12 | Info | Workspace dependency `*` | ai-frontend is `"private": true`; lockfile committed. No change. |

## Missing tests noted
Session-id change on login; absolute timeout; anonymous session flooding; proxy `X-Forwarded-For` behaviour; malformed body by a non-`AI_USER` returning 403; anything under `deploy/`.

## Limits
No build/test/scan run by the reviewer; `Dockerfile.app` unread; Spring/Tomcat forwarded-header behaviour reasoned, not tested; remaining risk includes in-memory per-JVM limiters/sessions and a shared demo credential on a CPU-bound model.

---

# Round 2 (2026-11-24) — final changed code

Reviewer: independent, read-only (no commands run). **Verdict: conditional pass, no Critical/High.** No path found that skips authentication, CSRF, the `AI_USER` interceptor or the per-requester limit; no duplicate endpoint; no new information disclosure. `deploy/` remains **not approved** (never built or run; `Dockerfile.app` could not be read by the reviewer's tool).

Round-1 fixes confirmed in code: S-1 (config only, not exercised through a proxy), S-2, S-5, S-6, S-7. S-3 was **found not effective** (R2-1).

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| R2-1 | Medium | `AuthRestController` looked up the session before reading the lazy `CsrfToken`, so for a cookie-less request (the flooding case) the 120 s anonymous lifetime was never applied | **Fixed**: the token is read first, then the session is shortened. New test `aCookielessCsrfRequestCreatesASessionWithTheShortAnonymousLifetime` (the old test used a pre-created session, so could not see this). Not checked against the old code by running it. |
| R2-2 | Low | The earlier timeout test could not catch R2-1 | **Fixed** by the cookie-less test above. |
| R2-3 | Low | `providerStatus` and `usage` are not per-requester limited | Accepted (unchanged since round 1; read-only, local readiness only, bounded history). |
| R2-4 | Low | Prod check fires only on an explicit `cookie.secure=false` | Verified the shared default (`ghostbridge-defaults.properties`: `secure=true`); accepted. |
| R2-5 | Info | Distributed guessing bounded only by the per-address key | Accepted for the demo; strong demo password required. |
| R2-6 / R2-7 | Info | Ollama image/hardening, HSTS subdomains, no edge rate limit (S-9, S-10) | Open until the Docker run. |

Remaining open/accepted from round 1: S-4 (no absolute session lifetime), S-8, S-9, S-10. Residual risks: in-memory per-JVM limiters/sessions; shared demo credential on a CPU-bound model.

---

# Round 3 (2026-11-25) — final code and deployment

Reviewer: independent, read-only (no commands). The real `deploy/` files were read (`Dockerfile.app` through a plain-text copy, because the reviewer's tool cannot open `.app` files; the copy was refreshed after the reviewer found it stale and the real file is digest-pinned). Evidence reviewed: `evidence/*-result.txt` and the scripts.

**Verdict: code approved (no Critical/High). Public host: approved conditionally for a time-limited demo.** Conditions: (1) random demo password ≥16 characters, rotated/removed after judging; (2) IPv4-only publishing kept, and a two-real-address test before enabling IPv6; (3) only 22 (restricted), 80, 443 answer from outside, 8080/11434 closed; (4) real certificate (ACME, port 80) and HSTS checked on the public name, `deploy/.env` mode 600 and untracked; (5) pinned images/module rebuilt deliberately. Without 1-4 the reviewer would not approve exposure.

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| R3-1 | Medium | Per-address limits bypassable with many addresses (IPv6 /64, botnet) | **Mitigated**: IPv4-only ports, strong-password condition in the runbook. Accepted. |
| R3-2 | Medium | Client address behind the Docker port proxy / any front layer | **Mitigated, not verified on a public host**; local second-address test showed addresses are told apart. Deploy condition. |
| R3-3 | Medium | Unpinned module and base images | **Fixed**: `caddy-ratelimit@v0.1.0`, all four base images by digest, Ollama by digest. |
| R3-4/R3-5 | Low | Ollama root/writable fs; no pids/cpu limits | **Mitigated**: `pids_limit` on all services, memory limits; root/writable fs and no CPU limit accepted. |
| R3-6 | Low | Hash in a container environment variable | Accepted for the demo (documented). |
| R3-7 | Low | Password file for evidence scripts could be committed | **Fixed**: `.gitignore` patterns, scripts read `DEMO_PASSWORD` from the environment; `deploy/.env` is git-ignored (checked). |
| R3-8 | Low | `UserMenu` swallows a failed sign-out; session stays valid until idle timeout | **Open, accepted (Low).** |
| R3-9 | Low | Empty allow-list behaviour unproven | **Fixed**: empty value fails startup (unit test), README corrected. |
| R3-10/R3-11 | Low/Info | Broad `internal-proxies` private ranges; sticky one-year HSTS | Accepted. |
| R3-12 | Info | Edge path normalisation untested | **Fixed**: probes F (404/401 as expected), `--path-as-is`. |
| R3-13 | Info | Weak healthcheck match | **Fixed**: matches `"status":"UP"` (and a DOWN pattern was shown not to match). |

Evidence limits stated by the reviewer: one local client address; Caddy's local CA with `curl -k`; restart policy shown by in-container exit only (not OOM kill/reboot); browser run does not capture console CSP violations; no absolute session lifetime (S-4, accepted).
