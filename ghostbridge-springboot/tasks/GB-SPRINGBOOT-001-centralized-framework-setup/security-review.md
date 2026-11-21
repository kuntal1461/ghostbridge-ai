# Security Review — GB-SPRINGBOOT-001

**Reviewer workflow:** `ghostbridge-security-reviewer` (definition: `.kiro/agents/ghostbridge-security-reviewer.json`)
**Date:** 2026-11-21  **Rounds:** 2

> **Limitation — independence not achieved.** The Kiro custom agent was created but **was not executed** (this environment has no Kiro runtime). Both rounds below were performed by the same Claude Code session that wrote the implementation, following the agent's prompt and checklist. This is **not** an independent review. Run `ghostbridge-security-reviewer` in Kiro against this folder before relying on the gate result.

## Scope and checks performed
Reviewed `ghostbridge-springboot-core` and `ghostbridge-springboot-web` main sources, both POMs, `ghostbridge-defaults.properties`, auto-configuration imports and tests.

| Area | Check performed | Result |
|---|---|---|
| Authentication/authorization | Read security chain; tests TC-4/5/6 (401, 403, public, method security) | Deny-by-default confirmed |
| CORS | Read policy + validation; tests TC-1..3 incl. preflight and disallowed origin | Restrictive default (no origins) |
| CSRF | Read `GhostBridgeSecurityAutoConfiguration` (csrf disabled only when `stateless=true`); stateful tests | Documented in code; see S-8 |
| XSS / headers | Tests TC-10 for CSP, nosniff, frame deny, referrer, HSTS (HTTPS request) | Present on success and error responses |
| SQL injection | grep for JDBC/SQL | No SQL code exists |
| XML/XXE | `SecureXmlParser` read; XXE, expansion bomb, oversize, malformed tests | DOCTYPE disabled; size capped |
| Unsafe deserialization | grep for ObjectInputStream / default typing | None present |
| File upload | `UploadValidator` + 413 filter | Metadata validated; see S-6 |
| SSRF | Read RestClient auto-config | See S-5 |
| Sensitive-data exposure | Error-handler tests (no stack trace, SQL, credentials) | No leakage in responses |
| Hardcoded credentials | grep for password/api-key/token literals in main sources and properties | None found |
| Dependency vulnerabilities | `mvn dependency:tree` reviewed; **no scanner run** | Not verified; see S-4 |
| Unsafe logging | grep for System.out/printStackTrace; masker tests; 500 handler logs masked message only | OK |
| Spring Security misconfiguration | Single chain test, back-off test, actuator tests | OK |
| Untrusted AI-generated code execution | No code execution paths exist in these modules | N/A here; AI module is structure only |

## Findings
| ID | Severity | File:line | Issue | Impact | Remediation | Status |
|---|---|---|---|---|---|---|
| S-1 | Medium | `GhostBridgeWebAutoConfiguration.java` (before fix: no filter) | JSON request bodies had no size limit; only multipart was capped | Memory/DoS via huge bodies | Added `RequestSizeLimitFilter` (413 on declared Content-Length over `ghostbridge.upload.max-request-size`) + `RequestSizeLimitFilterIntegrationTest` | **Fixed (round 2)** |
| S-2 | Medium | `RequestSizeLimitFilter.java` | Chunked requests have no Content-Length and are not measured | Residual DoS vector | Apply container limits (Tomcat/proxy) or add a counting request wrapper | Open (residual risk) |
| S-3 | Medium | `GhostBridgeProperties.java:26` (Cors) | Wildcard origin accepted outside `prod` (without credentials) | Overly open CORS in non-prod | Prod validator rejects it (non-https); keep wildcard to `local` only | Accepted, documented |
| S-4 | Medium | (build) | No dependency vulnerability scanner configured or run | Known-CVE dependencies undetected | Add OWASP Dependency-Check or Dependabot/Renovate | Open |
| S-5 | Medium | `GhostBridgeRestClientAutoConfiguration.java:31` | Redirects are disabled, but there is no destination allowlist or private-address blocking | SSRF if a user-supplied URL is ever fetched | Add an allowlist helper before the first user-supplied-URL feature | Open (residual risk) |
| S-6 | Low | `UploadValidator.java` | Content type is the client-declared header; content is not sniffed | Mislabelled upload passes metadata check | `SecureXmlParser` validates real content for XML; add sniffing for other types if added | Accepted |
| S-7 | Low | `GlobalExceptionHandler.java:44` | `GhostBridgeException` messages are returned to callers | A developer could put internal detail in a message | Rule: messages must be client-safe; reviewed in code review | Accepted, documented in rules |
| S-8 | Low | `GhostBridgeSecurityAutoConfiguration.java:93` | CSRF is off whenever `stateless=true`; an application adding cookie-based authentication through a customizer would be unprotected | CSRF if misused | Documented in code; `rules.md` states cookie auth requires `stateless=false` | Accepted, documented |
| S-9 | Low | `SecureXmlParser.java:25` | Only size and DOCTYPE are restricted; element-depth/attribute limits rely on JDK secure-processing defaults | Pathologically nested XML within size limit | Add depth/count limits if profiling shows need | Accepted |
| S-10 | Low | `GlobalExceptionHandler.java:72` | 500s are logged without stack trace (masked message only) | Reduced diagnosability (deliberate trade-off) | Structured, masked logging in applications; stack trace at DEBUG | Accepted |
| S-11 | Info | defaults file | No `Permissions-Policy` / `Cross-Origin-*` headers | Defence-in-depth gap | Add when a browser-facing UI is served | Accepted |

## Missing tests
- No test runs the application under the `prod` profile through the full web stack (core tests cover the validator).
- No test for chunked/streamed oversized bodies (S-2).
- No test that a customizer-added authentication mechanism works with the shared chain.

## Limitations and remaining risks
- Same-session review (see top); no automated scanner (SAST/SCA/DAST) was run; no penetration test; no real authentication mechanism exists yet, so authorization was tested with mock users only.
- Protections split across layers: see `doc-architecture/architecture.md` (ownership table). Frontend output encoding and infrastructure TLS/WAF are outside these modules and unverified here.
- This report does **not** claim the platform is free of vulnerabilities.

## Gate
Round 1: Critical 0, High 0, Medium 5, Low 5. Round 2 (after S-1 fix): Critical 0, High 0. Open Medium items (S-2, S-4, S-5) are recorded as residual risks. **Result: no unresolved Critical/High findings — pending an independent Kiro run.**
