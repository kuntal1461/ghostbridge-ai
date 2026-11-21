# GhostBridge AI — Development Rules

These rules are mandatory for every module. Layout and module roles: [PROJECT_STRUCTURE.md](../doc-architecture/PROJECT_STRUCTURE.md).

## 1. Layer rules

| Kind of code | Goes in |
|---|---|
| Business logic, entities, repositories, services, implementations | `*-core` |
| REST controllers, exception advice, request/response mapping | `*-web` |
| Anything UI (React, TypeScript, CSS, assets) | `*-frontend` |

- `*-web` depends on `*-core`. `*-core` must never depend on `*-web`.
- Controllers contain no business logic: validate, delegate to a core service, map the result.
- `*-frontend` is a React application, not a Maven module. It talks to the backend only through REST.
- Other projects reuse `*-core` or `*-web` through Maven dependencies, never by copying code.

## 2. Sonar compliance

- No new Sonar blocker, critical or major issues; the quality gate must pass.
- No code smells left behind: no unused imports, variables, private methods or commented-out code.
- Cognitive complexity per method: 15 or less. Keep methods short and single-purpose.
- No duplicated blocks; extract shared code.
- No hard-coded credentials, secrets, URLs or ports; use configuration.
- Handle or rethrow exceptions; never swallow them silently. Do not catch `Exception`/`Throwable` without reason.
- No `System.out` / `printStackTrace`; use SLF4J logging.
- New code has unit tests; target 80% coverage on new code.

## 3. Java compliance

- Java 21, Spring Boot 3.5.x. Run `mvn clean install` from the repository root before every commit.
- Constructor injection only. No field injection with `@Autowired`.
- Use `record` for immutable DTOs and properties.
- Entities are never returned from controllers; use DTOs.
- Prefer `Optional` over `null` returns. Do not pass or return `null` collections; return empty ones.
- Use specific exceptions extending `GhostBridgeException`. Errors are returned as `ApiError` by the exception advice in `*-web`.
- Classes: `PascalCase`. Methods and fields: `camelCase`. Constants: `UPPER_SNAKE_CASE`. Packages: lowercase.
- One public top-level type per file. Keep files small and focused.

## 4. Project structure and folder naming compliance

- `ghostbridge-springboot/pom.xml` is the only place that declares Spring Boot, dependency and plugin versions. The root `pom.xml` is an aggregator only.
- Maven modules and folders are lowercase, hyphen-separated: `ghostbridge-<project>-<layer>` (for example `ghostbridge-springboot-core`).
- Layers are named exactly `core`, `web`, `frontend`. No other layer names.
- **`com.ghostbridge` is the only Java base namespace in every project and module.** Never add project or layer levels: no `.springboot`, `.ai`, `.app`, `.core`, `.web` (so not `com.ghostbridge.springboot.config` and not `com.ghostbridge.ai.web.controller`).
- Organize classes by function directly under it: `com.ghostbridge.config`, `entity`, `repository`, `service`, `service.impl`, `controller`, `restcontroller`, `advice`, `filter`, `security`, `dto`, `exception`, `util`.
- Core modules hold `config`, `entity`, `repository`, `service`, `service.impl`, `dto`, `util`, `exception`. Web modules hold `controller`, `restcontroller`, `advice`, `filter`, `security`, `dto`, `config`. Layer separation is enforced by the Maven module, not by the package name.
- Several modules may contribute to the same functional package (for example `com.ghostbridge.service`), so **simple class names must be unique across the whole repository**. Prefix with the domain when needed (`AiPromptService`, `SpringBootCorsConfig`), so there is never a duplicate fully qualified name.
- Spring scanning (component, entity, repository) starts at `com.ghostbridge`. The application class lives directly in `com.ghostbridge` and uses the default scan; do not set narrower `scanBasePackages`, `@EntityScan` or `@EnableJpaRepositories` packages.
- The module names (`ghostbridge-<project>-<layer>`) and artifact IDs are not affected by this rule.
- Avoid unnecessary nested packages and duplicate directory structures.
- Do not create a module, folder or package that is not described in [PROJECT_STRUCTURE.md](../doc-architecture/PROJECT_STRUCTURE.md). Update that file in the same change.
- Dependencies flow one way: springboot → ai → app. Never upward.
- `ghostbridge-ai-module` is isolated: other projects use it only through its published core/web modules.
- `ghostbridge-springboot` core and web must stay reusable by other applications: no app-specific logic in them.
- Follow clean code and SOLID principles.
- `rules/` holds the rules and Claude instructions. Documentation lives in `doc-architecture/`; hackathon material in `hackathon-requirements/`.

## 5. Task documentation workflow (mandatory)

- No source, configuration or POM change without a task folder `<project>/tasks/<TASK-ID>-<slug>/` created from `templates/tasks/`.
- Required before any change: `existing-implementation.md`, `requirements.md`, `implementation-plan.md`, `impact-analysis.md`, `test-cases.md`, `milestone.md`, `tasks.md`. `implementation.md` and `changelog.md` are maintained during the work.
- If the task folder does not exist, nothing is changed until it is created.
- A milestone is complete only after its acceptance criteria are verified, never merely because code was generated.

## 6. Centralized Spring Boot infrastructure (mandatory)

- `ghostbridge-springboot` owns shared infrastructure: configuration contracts, CORS, Spring Security, HTTP security headers, validation, exception handling, logging/correlation, outbound HTTP conventions. Other projects reuse it through Maven and **must not re-implement or duplicate it** (no second CORS config, no second global exception handler without explicit `@Order`, no second default `SecurityFilterChain`).
- Shared capabilities are registered through `@AutoConfiguration` and `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, never by forcing consumers to scan extra packages. Every shared bean uses `@ConditionalOnMissingBean` (or an explicit order) so applications can override it.
- Configuration lives under `ghostbridge.*` as validated `@ConfigurationProperties` records with restrictive defaults. The framework defines contracts and defaults; the executable application supplies environment values.
- Profiles are `local`, `dev`, `qa`, `uat`, `prod`. `prod` must pass startup validation (security enabled, HSTS on, https-only CORS origins, no wildcard actuator exposure).
- No passwords, tokens, API keys or database credentials in source, defaults or committed properties; inject them from the environment or a secret store.
- Libraries stay plain JARs: only `ghostbridge-app-web` (or another application module) is executable.

## 7. Security-first rules (mandatory)

- Deny by default: every endpoint requires authentication unless listed in `ghostbridge.security.public-paths`. Never use `/**` as a public path.
- CORS: no wildcard origin in `prod`, never together with credentials. CORS is not a substitute for authentication, authorization, CSRF protection or output encoding.
- CSRF is disabled only in the stateless token model (`ghostbridge.security.stateless=true`, credentials sent in the `Authorization` header). Anything using cookie/session authentication must set `stateless=false` and keep CSRF on.
- Error responses are `ProblemDetail` with a stable `code` and `correlationId`. `GhostBridgeException` messages are returned to callers and must be client-safe: no stack traces, SQL, credentials or infrastructure details.
- Never log passwords, tokens, `Authorization` headers, API keys or credentials; use `SensitiveDataMasker` for untrusted text.
- Untrusted XML (WSDL/SOAP) is parsed only with `SecureXmlParser`; uploads go through `UploadValidator`; request bodies are size-limited.
- Outbound HTTP uses the shared `RestClient` conventions. Never disable TLS verification; never follow redirects blindly; never retry non-idempotent calls automatically.
- Actuator: only health and probes are public; everything else needs the configured role and is not exposed by default.

## 8. Code quality and Sonar (mandatory)

- Code must satisfy Sonar Java rules; cognitive complexity at most 15 per method (target 10 or less for controllers and services); prefer guard clauses and small helpers.
- Sonar and coverage results are reported as measured. If Sonar is not configured, say "not verified"; never invent results.

## 9. Mandatory reviews and acceptance gates

- Before a task is closed, run `ghostbridge-security-reviewer` and `ghostbridge-code-quality-reviewer` (`.kiro/agents/`). Their reports are written into the task folder (`security-review.md`, `code-quality-review.md`).
- Reviewers do not approve their own findings: each fix is re-reviewed against the code.
- **Gate:** no unresolved Critical or High security finding (or it is listed as an explicit blocker), tests pass, `mvn clean verify` passes from the repository root.
- If the review agents cannot be run independently, the limitation is stated in the report and the gate is "pending independent review".
