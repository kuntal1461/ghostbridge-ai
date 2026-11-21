# Implementation — GB-SPRINGBOOT-001

## M1: Documentation and workflow gate
- Created `templates/tasks/` (no templates existed) and this task folder from them.
- Added the task gate to `rules/rules.md` (section 5) and `rules/claude/CLAUDE.md`.

## M2: Core (`ghostbridge-springboot-core`, package `com.ghostbridge.*`)
- `config/GhostBridgeProperties` (validated records: cors, security, httpClient, upload), `GhostBridgeProductionValidator` (fails `prod` startup on unsafe settings), `GhostBridgeCoreAutoConfiguration` (+ `AutoConfiguration.imports`, `ghostbridge-defaults.properties` as lowest-precedence defaults).
- `exception/`: `ErrorCode`, `GhostBridgeException`, `BusinessException`, `ResourceNotFoundException`, `InvalidInputException`.
- `util/`: `SensitiveDataMasker`, `SecureXmlParser` (DOCTYPE disabled, size cap), `UploadValidator`.
- POM: + `spring-boot-starter-validation`, `spring-boot-configuration-processor` (optional), `spring-boot-starter-test` (test).

## M3: Web (`ghostbridge-springboot-web`)
- `filter/CorrelationIdFilter`, `filter/RequestSizeLimitFilter`; `advice/GlobalExceptionHandler` (ProblemDetail); `util/ProblemDetailFactory`, `ProblemResponseWriter`; `security/ProblemAuthenticationEntryPoint`, `ProblemAccessDeniedHandler`, `GhostBridgeHttpSecurityCustomizer`.
- `config/GhostBridgeWebAutoConfiguration` (filters, advice, CORS), `GhostBridgeSecurityAutoConfiguration`, `GhostBridgeRestClientAutoConfiguration`; registered in `AutoConfiguration.imports`.
- POM: + `spring-boot-starter-security`, `spring-boot-starter-actuator`, test: `spring-boot-starter-test`, `spring-security-test`.

## Decisions and deviations from the plan
- **Added packages `filter` and `security`** (web) and `util` use in web; recorded in `rules/rules.md` and PROJECT_STRUCTURE.md.
- **Exception handler back-off replaced by ordering.** Planned: `@ConditionalOnMissingBean`. Found by test: the application scans `com.ghostbridge`, so the `@RestControllerAdvice` class is registered by scanning regardless of conditions. Final: `@Order(LOWEST_PRECEDENCE)` on the advice, auto-config bean only if not already scanned, applications override with a higher-precedence advice.
- **CORS bean** is registered by name (`corsConfigurationSource`) because Spring MVC's introspector is also a `CorsConfigurationSource` and silently shadowed a type-based condition.
- **Request-size filter added** after the first security review (S-1); `ghostbridge.upload.max-request-size` added.
- **No authentication provider introduced.** Non-public endpoints return 401 until an application supplies authentication through `GhostBridgeHttpSecurityCustomizer` (or its own chain, in which case the shared chain backs off). An empty `UserDetailsService` prevents Spring Boot's generated default user.
- **Deferred:** OpenAPI/Swagger, DB pooling/migrations, caching, async/scheduling, pagination, trace export (no current need).
- **Kiro agents:** `.kiro/agents/ghostbridge-security-reviewer.json` and `ghostbridge-code-quality-reviewer.json` with prompts in `.kiro/agents/prompts/`. Schema taken from Kiro's custom-agent configuration reference (docs have no read-only example; the permissions rules were composed from the documented fields and are unverified in a live Kiro run).
- **Reviews were run by the implementing session**, not by Kiro agents; documented in both reports.

## Docs updated
`rules/rules.md` (sections 5-9), `rules/claude/CLAUDE.md`, `doc-architecture/architecture.md`, `doc-architecture/PROJECT_STRUCTURE.md`.
