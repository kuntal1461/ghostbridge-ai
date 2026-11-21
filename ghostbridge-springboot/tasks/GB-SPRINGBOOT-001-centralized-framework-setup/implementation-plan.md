# Implementation Plan — GB-SPRINGBOOT-001

## Phases
| Phase | Goal | Deliverables |
|---|---|---|
| M1 | Templates, task docs, rules/CLAUDE.md workflow gate | `templates/tasks/`, this folder, rules updates |
| M2 | Core: properties, validation, exceptions, utilities, defaults | `GhostBridgeProperties`, `GhostBridgeProductionValidator`, exception types, `SensitiveDataMasker`, `SecureXmlParser`, `UploadValidator`, auto-config, defaults file |
| M3 | Web: correlation filter, exception advice, CORS, security chain, headers, RestClient | Web auto-configurations and classes |
| M4 | Tests | JUnit 5 / Spring Boot Test / MockMvc suites in core and web |
| M5 | Review agents (`.kiro/agents`) and reviews | Agents, `security-review.md`, `code-quality-review.md`, fixes, re-review |
| M6 | Docs and final verification | `rules/rules.md`, architecture/structure docs, final report |

## Affected modules and files
| Module | Files (planned) | Change |
|---|---|---|
| `ghostbridge-springboot-core` | `pom.xml`; `com.ghostbridge.config.*` (properties, validator, auto-config); `com.ghostbridge.exception.*`; `com.ghostbridge.util.*`; `META-INF/spring/...AutoConfiguration.imports`; `ghostbridge-defaults.properties` | New |
| `ghostbridge-springboot-web` | `pom.xml`; `com.ghostbridge.config.*` (security, CORS, headers, rest-client auto-configs); `com.ghostbridge.advice.*`; `com.ghostbridge.filter.*`; imports file | New |
| Root/rules/docs | `rules/rules.md`, `rules/claude/CLAUDE.md`, `doc-architecture/*`, `templates/tasks/*`, `.kiro/agents/*` | Update/new |
| AI / App modules | none | Untouched |

`filter` and `security` are new functional packages under `com.ghostbridge` (web); added to `rules/rules.md`. (Updated during implementation; see implementation.md for deviations.)

## Technical decisions
| Decision | Alternatives | Reason |
|---|---|---|
| Records for properties with `@Validated` | Mutable beans | Immutable, matches rules (records for DTOs/properties) |
| `@AutoConfiguration` + `AutoConfiguration.imports` | Component scanning | Consumers must not scan library packages; satisfies override requirement |
| Security chain backs off if app defines a `SecurityFilterChain` | Always-on | Avoids duplicate chains |
| Stateless default, CSRF disabled only in stateless mode | Always CSRF-on / always off | No browser session in token model; documented per SEC-8 |
| No authentication provider; empty `UserDetailsService` bean when none exists | Spring default generated user | Prevents a random-password default account; apps supply authentication |
| `ProblemDetail` via `ResponseEntityExceptionHandler` | Custom envelope | Spring-native, project-approved equivalent |
| `@PropertySource` defaults file for non-secret defaults | `EnvironmentPostProcessor` | Simple; lowest precedence so apps override |
| JDK `HttpClient` for `RestClient` | Apache HttpClient | No new dependency |
| Skip springdoc, DB, cache, async | Add now | Not justified by current requirements |

## Dependencies (Maven)
- core: `spring-boot-starter-validation`, `spring-boot-starter-test` (test), `spring-boot-configuration-processor` (optional).
- web: `spring-boot-starter-security`, `spring-boot-starter-actuator`, `spring-boot-starter-test` and `spring-security-test` (test). Versions managed by the Spring Boot BOM.

## Rollback considerations
Everything is additive in two library modules with no consumers running yet. Rollback = revert the files and POM dependency additions of the affected milestone; consumers can also disable features at runtime via `ghostbridge.security.enabled=false` and per-feature flags.
