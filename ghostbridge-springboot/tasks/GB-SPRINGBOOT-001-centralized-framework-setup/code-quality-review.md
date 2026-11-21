# Code Quality Review — GB-SPRINGBOOT-001

**Reviewer workflow:** `ghostbridge-code-quality-reviewer` (definition: `.kiro/agents/ghostbridge-code-quality-reviewer.json`)
**Date:** 2026-11-21  **Rounds:** 2

> **Limitation — independence not achieved.** The Kiro agent was created but **not executed**. The review was performed by the same Claude Code session that wrote the code, using the agent's checklist. Run `ghostbridge-code-quality-reviewer` in Kiro for an independent result.

## Sonar status
**SonarQube/SonarCloud is not configured in this repository, so no Sonar analysis was run.** Full Sonar compliance is **not verified**. No quality-gate result is claimed. Substitute checks actually performed:
- Compiler with `-Xlint:all`: no warnings.
- Scripted unused-import scan over all main and test sources: none found.
- `grep` for `System.out`, `printStackTrace`, TLS-disabling code, credential literals: none.
- Duplicate simple class names across all modules: none.
- `mvn dependency:analyze`: reports only transitive-via-starter "used undeclared" entries (normal for Spring starters) and `spring-boot-starter` as redundant in core (see Q-4).
- Complexity: **manual inspection only** (no tool measured it). Highest estimated cognitive complexity: `GlobalExceptionHandler.handleExceptionInternal` (about 6) and `GhostBridgeSecurityAutoConfiguration.ghostBridgeSecurityFilterChain` (about 4, mostly lambdas). All are below the limit of 15.

## Scope and checks performed
Java 21 conventions, SOLID, dead code, exception handling, DI (constructor only), thread safety (filters stateless, MDC cleared in `finally`), resource management (try-with-resources in `SecureXmlParser`), null safety, Core/Web separation, Maven boundaries (web → core only; no cycles), test quality, naming, logging, package rules in `rules/rules.md`.

## Findings
| ID | Severity | File:line | Rule / principle | Issue | Recommendation | Status |
|---|---|---|---|---|---|---|
| Q-1 | Low | `GhostBridgeProperties.java:41` | Null safety / immutability | Compact constructors threw NPE on null lists when built programmatically | Defensive `List.copyOf` via shared helper; test added | **Fixed (round 2)** |
| Q-2 | Low | `GlobalExceptionHandler.java:56-65` vs `security/Problem*Handler.java` | DRY | 401/403 bodies are produced in two places (MVC advice and servlet-level handlers) | Both go through `ProblemDetailFactory`, so contract cannot diverge; accept | Accepted |
| Q-3 | Low | `rules/rules.md` | Convention | New functional packages `filter`, `security` were not in the allowed package list | Add to rules and structure docs | **Fixed in M6** |
| Q-4 | Low | `ghostbridge-springboot-core/pom.xml:14` | Unused dependency | `spring-boot-starter` is already pulled in by `spring-boot-starter-validation` | Keep explicit (clarity) or remove; no runtime effect | Accepted |
| Q-5 | Low | `src/test/...` | Test cost | Several `@SpringBootTest` contexts (one per property combination) | Acceptable at current size; consolidate if build time grows | Accepted |
| Q-6 | Medium | repository | Sonar | Sonar not configured; compliance unverified | Configure SonarCloud/SonarQube and the Maven scanner in CI | Open |
| Q-7 | Info | `GhostBridgeSecurityAutoConfiguration.java` | Cohesion | One auto-configuration holds chain, user details and encoder | Fine at this size; split if it grows | Accepted |

## Test review
Core: 31 tests; web: 40 tests; all pass. Coverage is behaviour-driven (every `test-cases.md` row has a test) but **no coverage percentage was measured** (no JaCoCo configured).

## Limitations
Same-session review; no Sonar; no coverage tool; complexity estimated manually.
