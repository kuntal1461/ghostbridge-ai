# GhostBridge AI — Architecture (HLD)

> Resurrect Legacy Systems. Bridge the Future.

GhostBridge AI converts legacy SOAP APIs into modern REST services using AI-assisted WSDL analysis, code generation and compatibility testing.

- **Hackathon category:** Resurrection (Kiroween)
- **Backend:** Spring Boot 3.5 / Java 21 / Maven multi-module
- **Frontend:** React + TypeScript (optional, added later as a separate module)

## 1. Project Structure

The module layout, responsibilities and dependency rules are in [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md).

In short: a root aggregator pom plus three Maven projects: `ghostbridge-springboot`, `ghostbridge-ai-module`, `ghostbridge-app`. Dependencies flow springboot → ai → app.

## 2. High-Level Flow

```
React UI (optional) ──REST──► ghostbridge-app
                                 │  1. upload WSDL / SOAP definition
                                 │  2. parse operations and types
                                 ├──► ghostbridge-ai-module ──► LLM provider
                                 │      3. propose REST resource model, generate code
                                 │  4. generate REST controllers + DTOs
                                 │  5. run compatibility tests (SOAP response vs REST response)
                                 ▼
                         REST service + test report
```

## 3. Cross-Cutting Concerns (head)

Owned centrally by `ghostbridge-springboot` (task GB-SPRINGBOOT-001), reused by every other project:

| Capability | Where | Notes |
|---|---|---|
| `ghostbridge.*` validated configuration, `prod` startup validation, safe defaults file | core `config` | Applications supply environment values; no secrets in defaults |
| CORS | web `config` | Single policy, empty allowlist by default, applied by Spring Security when enabled |
| Spring Security chain | web `config`, `security` | Deny by default, JSON 401/403, stateless by default, method security, `GhostBridgeHttpSecurityCustomizer` for authentication |
| HTTP security headers | web `config` (Spring Security) | CSP, nosniff, frame deny, referrer policy, HSTS |
| Validation, exceptions | web `advice`, core `exception` | `ProblemDetail` with `code`, `correlationId`, `timestamp` |
| Correlation ID, request-size limit | web `filter` | `X-Correlation-Id` in MDC and response |
| Secure XML, upload validation, masking | core `util` | XXE-safe parser, size caps, log masking |
| Outbound HTTP | web `config` | Timeouts, no redirects, correlation propagation |
| Actuator | web `config` | Only health/probes public |

Who implements which protection:

| Protection | Spring Boot (shared lib) | Spring Security | Frontend | Infrastructure |
|---|---|---|---|---|
| CORS | policy and properties | applies it before authentication | sends allowed origin | proxy may add rules |
| CSRF | n/a | on for session mode, off for stateless token | include token in session mode | n/a |
| XSS | JSON-only API, input validation | CSP and nosniff headers | output encoding, no unsafe HTML | WAF (optional) |
| Clickjacking | n/a | `X-Frame-Options: DENY`, `frame-ancestors 'none'` | n/a | n/a |
| Transport security | secure cookie defaults | HSTS header | https only | TLS termination, certificates |
| XXE / uploads | `SecureXmlParser`, `UploadValidator`, size limits | n/a | n/a | proxy body limits |
| Secrets | no secrets in code or defaults | n/a | n/a | secret store, environment |

Deferred (no current need, conventions only): OpenAPI/Swagger, database pooling and migrations, caching, async/scheduling, pagination, distributed tracing export.

## 4. Build and Run

```bash
mvn clean install
# The shared Spring Boot modules are libraries; no executable application exists yet.
```

## 5. Kiro Credits

Credits received for the hackathon are recorded here.

| Date | Source | Amount | Used for |
|---|---|---|---|
| _TBD_ | _TBD_ | _TBD_ | _TBD_ |
