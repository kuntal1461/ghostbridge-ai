# Test Cases — GB-SPRINGBOOT-001

Status values: Not run / Pass / Fail. Updated after each execution.

| ID | Type | Scenario | Expected | Test class | Status |
|---|---|---|---|---|---|
| TC-1 | positive | CORS request from allowed origin | `Access-Control-Allow-Origin` echoes origin | `CorsSecurityIntegrationTest` | Pass |
| TC-2 | negative | CORS request from disallowed origin | 403, no allow-origin header | `CorsSecurityIntegrationTest` | Pass |
| TC-3 | positive | CORS preflight from allowed origin | 200 with allow-methods / max-age | `CorsSecurityIntegrationTest` | Pass |
| TC-4 | security | Unauthenticated request to protected path | 401 problem+json | `SecurityIntegrationTest` | Pass |
| TC-5 | security | Authenticated user without role | 403 problem+json | `SecurityIntegrationTest` | Pass |
| TC-6 | positive | Public endpoint without auth | 200 | `SecurityIntegrationTest` | Pass |
| TC-7 | security | CSRF behaviour: stateless → POST with auth needs no token; stateful mode → POST without token rejected | as described | `SecurityIntegrationTest` | Pass |
| TC-8 | negative | Invalid request body | 400 problem+json with field errors | `GlobalExceptionHandlerIntegrationTest` | Pass |
| TC-9 | integration | Business / not-found / unexpected exceptions | 422/404/500, stable code, no stack trace or message leak | `GlobalExceptionHandlerIntegrationTest` | Pass |
| TC-10 | security | Response headers | CSP, nosniff, frame deny, referrer policy present | `SecurityHeadersIntegrationTest` | Pass |
| TC-11 | security | Actuator: health public; `/actuator/env` denied | 200 / 401 | `ActuatorSecurityIntegrationTest` | Pass |
| TC-12 | negative | Invalid configuration (wildcard + credentials; prod unsafe) | context start fails | `GhostBridgePropertiesTest`, `GhostBridgeProductionValidatorTest` | Pass |
| TC-13 | security | Sensitive-data masking | password/token/Authorization values masked | `SensitiveDataMaskerTest` | Pass |
| TC-14 | security | Secure XML parser rejects DOCTYPE/XXE, oversize input | exception | `SecureXmlParserTest` | Pass |
| TC-15 | integration | Auto-configuration loads from imports file | beans present without scanning | `AutoConfigurationLoadingTest` | Pass |
| TC-16 | integration | User-defined beans override / `SecurityFilterChain` backs off | user bean wins, one chain | `AutoConfigurationOverrideTest` | Pass |
| TC-17 | positive | Correlation ID generated / echoed / invalid value replaced | header present, valid | `CorrelationIdFilterTest` | Pass |
| TC-19 | security | Request body over `max-request-size` | 413 problem+json, within limit accepted | `RequestSizeLimitFilterIntegrationTest` | Pass |
| TC-20 | integration | Outbound RestClient propagates correlation ID; redirects not followed | header sent; 302 returned unfollowed | `RestClientConventionsTest` | Pass |
| TC-18 | negative | Upload validator: too large, bad type, bad filename | rejected | `UploadValidatorTest` | Pass |

## Execution log
| Date | Command | Result |
|---|---|---|
| 2026-11-21 | `mvn clean verify` (repository root) | BUILD SUCCESS; core 31 tests, web 40 tests, 0 failures |
| 2026-11-21 | `mvn dependency:tree` | BUILD SUCCESS |

Notes: an earlier run failed (tests picked up by scanning, handler NPE on empty ProblemDetail properties, CORS bean shadowed by Spring MVC introspector, advice back-off); all fixed, see implementation.md. Not covered: prod-profile full-stack run, chunked oversized bodies, customizer-added authentication (see security-review.md).
