# Existing Implementation — GB-AI-001 (AI Framework Setup)

Reviewed: 2026-11-21, branch `main`, commit `6d07945`, working tree clean.

## Scope analysed
- `ghostbridge-ai-module/` (parent POM, `ghostbridge-ai-core`, `ghostbridge-ai-web`, `ghostbridge-ai-frontend`)
- `ghostbridge-springboot/` (parent POM, `-core`, `-web`) — the shared infrastructure the AI module must reuse
- Root `pom.xml`, `rules/rules.md`, `doc-architecture/*`, `templates/tasks/`, `.kiro/`

## Current AI modules and classes
| Module | State |
|---|---|
| `ghostbridge-ai-core` | **Structure only.** `pom.xml`, `src/main/java/com/ghostbridge/.gitkeep`, `src/main/resources/.gitkeep`, `src/test/java/.gitkeep`. Zero Java classes. |
| `ghostbridge-ai-web` | **Structure only.** `pom.xml`, `src/main/java/com/ghostbridge/restcontroller/.gitkeep`, `src/test/java/.gitkeep`. Zero Java classes. No `src/main/resources`. |
| `ghostbridge-ai-frontend` | **Structure only.** `package.json` (only script: `typecheck`), `tsconfig.json`, empty `src/api`, `src/components`, `src/views` (`.gitkeep`). No `index.ts` although `package.json` declares `"main": "src/index.ts"`. No React DOM, bundler, linter or test runner installed. |
| `ghostbridge-ai-module/pom.xml` | Aggregator `pom`, parent `ghostbridge-springboot`, modules `ghostbridge-ai-core`, `ghostbridge-ai-web`. |

## Current Maven dependencies
- `ghostbridge-ai-core`: `ghostbridge-springboot-core`, `spring-boot-starter-json`. No test dependency declared (springboot-core's `spring-boot-starter-test` is `test` scope and does not transitively apply).
- `ghostbridge-ai-web`: `ghostbridge-ai-core` (`${project.version}`), `ghostbridge-springboot-web`, `spring-boot-starter-validation`. No test dependency declared.
- Versions: only `ghostbridge-springboot/pom.xml` (Spring Boot parent 3.5.7, `java.version=21`). Its `dependencyManagement` already lists `ghostbridge-ai-core` and `ghostbridge-ai-web`.
- No AI provider SDK, JSON-schema library or Micrometer dependency is declared anywhere in the repository.
- Local build toolchain: JDK 25 (Temurin), Maven via SDKMAN, Node v24.19.0. Build target remains Java 21 (`java.version`).

## Existing AI providers or configurations
None. No `ghostbridge.ai.*` properties, no provider adapters, no prompts, no credentials handling.
`~/.m2` already contains `software.amazon.awssdk:bedrockruntime` (2.31.65, 2.41.22, 2.46.19) and `com.networknt:json-schema-validator` (1.5.7, 2.0.0, 3.0.1) from other work; this is not a repository dependency and only indicates offline availability.

## Existing shared infrastructure to reuse (ghostbridge-springboot)
| Capability | Class / location | Relevance to AI |
|---|---|---|
| Config contract | `GhostBridgeProperties` record, prefix `ghostbridge`, `@Validated`, `@DefaultValue`, restrictive defaults | Pattern for `ghostbridge.ai.*` record. AI must **not** edit it. |
| Prod startup validation | `GhostBridgeProductionValidator` | Pattern for AI prod validation (no mock in `prod`). |
| Auto-configuration | `GhostBridgeCoreAutoConfiguration`, `…WebAutoConfiguration`, `…SecurityAutoConfiguration`, registered via `AutoConfiguration.imports` | AI must register through its own `AutoConfiguration.imports` (rules §6), `@ConditionalOnMissingBean`. |
| Exceptions | `GhostBridgeException(ErrorCode, message[, cause])`; `ErrorCode` is a **closed enum**: `VALIDATION_ERROR, INVALID_INPUT, MALFORMED_REQUEST, AUTHENTICATION_REQUIRED, ACCESS_DENIED, RESOURCE_NOT_FOUND, BUSINESS_RULE_VIOLATION, INTERNAL_ERROR` | AI errors extend `GhostBridgeException`. **Gap:** no codes for 429 / upstream 502-504. |
| Error mapping | `GlobalExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`), `statusFor(ErrorCode)` private switch, default → 400 | Any new `ErrorCode` falls to **400** unless the switch is extended or an AI advice with higher precedence is added. |
| Problem format | `ProblemDetailFactory.create/enrich` — takes `ErrorCode`; adds `code`, `correlationId`, `timestamp` | Reuse for AI error bodies. |
| Correlation | `CorrelationIdFilter` (`X-Correlation-Id`, MDC key `CorrelationIdFilter.MDC_KEY`) | AI request tracing reuses it. |
| Request size | `RequestSizeLimitFilter`, `Upload` properties | Baseline HTTP body limit; AI adds its own input-length limit. |
| Security | `GhostBridgeSecurityAutoConfiguration` (deny by default, stateless, method security, `GhostBridgeHttpSecurityCustomizer` hook, `publicPaths`) | AI endpoints are protected by default; **no second `SecurityFilterChain`**. |
| CORS | single policy in web `config` | AI must not add CORS config. |
| Masking | `SensitiveDataMasker` | Reuse for any text that could reach logs. |
| Outbound HTTP | `GhostBridgeRestClientAutoConfiguration` (timeouts, no redirects) | Bedrock uses the AWS SDK (own HTTP client) — see impact analysis; conventions (timeouts, no redirects, TLS verification) must be matched, not bypassed. |
| Actuator | `spring-boot-starter-actuator` in springboot-web | Micrometer `MeterRegistry` is available in web contexts; core must treat it as optional. |
| Test pattern | `GhostBridgeWebTestApplication`, `*IntegrationTest` under springboot-web tests | Pattern for AI web tests. |

## Functionality that must be preserved
- Maven hierarchy: root aggregator → `ghostbridge-springboot` → `ghostbridge-ai-module` → `-core`/`-web`; versions only in `ghostbridge-springboot/pom.xml`.
- All existing springboot-core/web behaviour and tests (no regressions).
- Single security chain, single CORS policy, single global exception handler.
- `ghostbridge-ai-web` stays a library (no executable application); `ghostbridge-app-web` remains the future executable.
- Existing review agents `.kiro/agents/*.json` and their prompts (extended, not duplicated).
- Existing `templates/tasks/` (used as-is; nothing recreated).

## Gaps and risks found
1. No AI code, tests, config, prompts or UI exist — everything is new.
2. `ghostbridge-ai-core` / `-web` declare no test dependency; tests need `spring-boot-starter-test` (and `spring-security-test` for web) added to module POMs (versions already managed by the Spring Boot parent).
3. `ErrorCode` is a closed enum without rate-limit / upstream-failure codes, and `GlobalExceptionHandler.statusFor` maps unknown codes to 400. Decision needed (see implementation-plan D5).
4. Bedrock SDK and JSON-schema library are not declared; per rules §4 their versions must be added **only** in `ghostbridge-springboot/pom.xml` (cross-project change, documented in impact-analysis).
5. Frontend has no bundler, React DOM, linter or test runner; `npm run build` and `npm run lint` scripts do not exist (task prompt: create only where justified and document).
6. `.kiro/` has `README.md` and `agents/` only: **no `specs/`, `steering/`, `hooks/`, `settings/`** exist yet. The task prompt refers to existing steering; it does not exist and must be created (not duplicated).
7. The task prompt mentions `ghostbridge-ai/templates/tasks/`; the actual location is `templates/tasks/` at the repository root and already contains all templates. No templates are created.
8. Reviewer agents are scoped generically; their AI-specific checks (OWASP LLM Top 10, prompt injection, output handling) need prompt extensions.
9. `PROJECT_STRUCTURE.md` says `ai-core` packages "none created yet"; it must be updated in the same change that creates packages.
10. Bedrock credentials/access are not available in this environment (unverified); real-provider tests must be opt-in.
11. Local JDK is 25 while the project targets 21; build uses `--release 21` via Spring Boot parent (`java.version`) — confirm during verification.

## Evidence
- `git ls-files` (commit `6d07945`), `cat` of all POMs, `package.json`, `tsconfig.json`, `rules/rules.md`, `PROJECT_STRUCTURE.md`, `architecture.md`, `.kiro/agents/*`, `GhostBridgeProperties`, `ErrorCode`, `GhostBridgeException`, `GlobalExceptionHandler`, `ProblemDetailFactory`, `GhostBridgeHttpSecurityCustomizer`, `ghostbridge-defaults.properties`.
- `java -version`, `mvn -v`, `node -v`, listing of `~/.m2/repository/software/amazon/awssdk` and `com/networknt`.
