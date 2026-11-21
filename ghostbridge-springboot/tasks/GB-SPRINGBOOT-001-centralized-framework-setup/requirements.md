# Requirements — GB-SPRINGBOOT-001

## Objective
Make `ghostbridge-springboot` the single, reusable owner of shared Spring Boot infrastructure (configuration, CORS, security, HTTP hardening, validation, error handling, logging, outbound HTTP) with security-first defaults, consumable by other applications through Maven.

## Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | Type-safe, validated `ghostbridge.*` configuration properties with safe defaults. |
| FR-2 | Profiles `local`, `dev`, `qa`, `uat`, `prod` supported; production startup validation fails fast on unsafe settings. |
| FR-3 | Central CORS: configurable origins, methods, headers, exposed headers, max-age, credentials; restrictive defaults. |
| FR-4 | Central Spring Security filter chain: deny-by-default, explicit public allowlist, 401/403 JSON responses, method security, stateless-by-default, CSRF per auth model. |
| FR-5 | HTTP security headers: CSP, `X-Content-Type-Options`, frame protection, Referrer-Policy, HSTS. |
| FR-6 | Standard validation error responses; upload size limits; secure XML parsing helper (XXE/expansion safe) with size limit. |
| FR-7 | Global exception handling returning `ProblemDetail` with status, stable `code`, message, `correlationId`, `timestamp`. |
| FR-8 | Request correlation ID filter (MDC + response header); sensitive-data masking utility; health/readiness probes. |
| FR-9 | Shared outbound `RestClient` conventions: timeouts, no redirects by default, correlation propagation, TLS validation untouched. |
| FR-10 | Reusable auto-configuration registered via `AutoConfiguration.imports`; every bean overridable (`@ConditionalOnMissingBean`). |

## Non-functional requirements
| ID | Requirement |
|---|---|
| NFR-1 | Java 21, Spring Boot 3.5.7; Core and Web stay ordinary library JARs. |
| NFR-2 | Only justified dependencies added (validation, security, actuator, test). |
| NFR-3 | Cognitive complexity ≤ 15 per method; classes follow `rules/rules.md`; package base `com.ghostbridge` only; unique class names repo-wide. |
| NFR-4 | No circular module dependencies; no duplicate security chains / competing advices / conflicting beans. |
| NFR-5 | No secrets in source or default properties. |

## Security requirements
| ID | Requirement |
|---|---|
| SEC-1 | No wildcard origin when credentials are allowed; no wildcard origin in `prod`. |
| SEC-2 | Non-public endpoints return 401 when unauthenticated, 403 when unauthorised. |
| SEC-3 | Actuator: only `health` (and probes) public; everything else requires a role; exposure limited by default. |
| SEC-4 | Error responses never contain stack traces, SQL, credentials or infrastructure detail. |
| SEC-5 | Logs never contain passwords, tokens, Authorization headers, API keys. |
| SEC-6 | XML parsing disables DOCTYPE/external entities and enforces a size limit. |
| SEC-7 | TLS verification is never disabled; retries are not applied automatically. |
| SEC-8 | CSRF is disabled only for the stateless token model and is documented; enabled otherwise. |

## Out of scope (deferred, no current need)
OpenAPI/Swagger dependency, database pooling/migrations, caching, async/scheduling, pagination, distributed tracing export, a concrete authentication provider/login. Conventions for these are documented only.

## Acceptance criteria
- [ ] Task documents and templates exist and are current.
- [ ] Allowed/disallowed origin and preflight tests pass.
- [ ] 401, 403, public endpoint, restricted actuator tests pass.
- [ ] Validation, global exception, headers, masking, secure XML, invalid-config startup, auto-config loading and override tests pass.
- [ ] `mvn clean verify` passes from the repository root.
- [ ] Security and code-quality review reports exist; no unresolved Critical/High.
- [ ] `rules/rules.md` and architecture docs updated.
