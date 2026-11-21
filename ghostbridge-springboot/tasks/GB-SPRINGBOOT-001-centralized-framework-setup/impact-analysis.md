# Impact Analysis — GB-SPRINGBOOT-001

## Affected projects and consumers
| Project / module | Impact |
|---|---|
| `ghostbridge-springboot-core` | New dependencies (validation), new auto-configuration. |
| `ghostbridge-springboot-web` | New dependencies (security, actuator); activates a security chain and advice in any app that includes it. |
| `ghostbridge-ai-web`, `ghostbridge-app-web` | Inherit these capabilities transitively; no code changes. No application class exists yet, so nothing runs today. |
| Frontends | Will be subject to CORS/CSP defaults once a backend runs; allowed origins must be set per environment. |

## Runtime behaviour changes (once an application is created)
- All endpoints except an explicit allowlist return 401 until the app supplies authentication.
- CORS denies all cross-origin requests until `ghostbridge.cors.allowed-origins` is set.
- Error bodies become `application/problem+json`.
- Only `/actuator/health` (+probes) is publicly reachable.

## Compatibility risks
- Spring Security on the classpath changes defaults of every web app; mitigated by `ghostbridge.security.enabled` and back-off on a user-provided `SecurityFilterChain`.
- Strict CSP (`default-src 'none'`) breaks Swagger UI/HTML pages; configurable via `ghostbridge.security.content-security-policy`.
- Split `com.ghostbridge.config` package across core and web: class names are unique, so no clash.

## Potential breaking changes
None for existing code (no code exists). Future applications must supply authentication and origins.

## Mitigations
Feature flags, `@ConditionalOnMissingBean` overrides, tests for override and back-off behaviour.
