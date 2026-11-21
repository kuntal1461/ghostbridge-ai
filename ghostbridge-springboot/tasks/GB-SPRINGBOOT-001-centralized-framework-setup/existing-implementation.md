# Existing Implementation — GB-SPRINGBOOT-001

## Scope analysed
`ghostbridge-springboot` (parent POM), `ghostbridge-springboot-core`, `ghostbridge-springboot-web`, `ghostbridge-springboot-frontend`, plus how `ghostbridge-ai-module` and `ghostbridge-app` consume them. Analysed 2026-11-21.

## Current behaviour
| Area | Current state |
|---|---|
| Maven | `ghostbridge-springboot/pom.xml` inherits `spring-boot-starter-parent` 3.5.7, Java 21, and manages versions of all internal modules. Root `pom.xml` is an aggregator only. |
| Core dependencies | `spring-boot-starter` only. |
| Web dependencies | `ghostbridge-springboot-core`, `spring-boot-starter-web`. |
| Java code | **None.** All Java sources were removed earlier in the project (structure-only instruction). Only empty, `.gitkeep`-marked package folders exist under `com.ghostbridge` (`config`, `dto`, `entity`, `exception`, `repository`, `service`, `service.impl`, `util` in core; `advice`, `config`, `controller`, `dto`, `restcontroller` in web). |
| Configuration | No `application*.yml/properties`, no `@ConfigurationProperties`, no profiles. |
| Security | No Spring Security dependency or configuration. |
| CORS | None. (An earlier prototype `CorsConfig` was deleted.) |
| Exception handling | None. (An earlier prototype `GlobalExceptionHandler` was deleted.) |
| Logging / observability | None. No actuator dependency. |
| Validation | None. No Bean Validation dependency. |
| Auto-configuration | None. No `AutoConfiguration.imports`. |
| Tests | None. |
| Executable apps | `ghostbridge-app-web` has no main class; repackage is skipped (`spring-boot.repackage.skip`). AI module has no code. |

## Functionality that must be preserved
- Module names, artifact IDs, folder layout and `com.ghostbridge` single-base-package convention (see `rules/rules.md`).
- Core and Web remain plain library JARs (no executable repackaging).
- `ghostbridge-springboot/pom.xml` remains the only place declaring versions and plugins.
- Dependency direction: web → core; core never depends on web.
- No business functionality exists yet, so no runtime behaviour has to be preserved.

## Gaps and risks found
- No shared infrastructure exists, so every future module would otherwise implement CORS, security, error handling itself.
- Downstream modules (`ghostbridge-ai-web`, `ghostbridge-app-web`) depend on `ghostbridge-springboot-web`, so anything added to it becomes active in them once an application class exists.

## Evidence
`find ghostbridge-springboot -name "*.java"` returned no files; POMs read directly.
