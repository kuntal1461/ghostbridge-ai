# Tasks — GB-AI-001 (AI Framework Setup)

Kiro spec task list: [.kiro/specs/ghostbridge-ai/tasks.md](../../../.kiro/specs/ghostbridge-ai/tasks.md) (same IDs). Gate: a task is checked only after its acceptance criteria are verified. `→` marks a dependency.

## M1 — Discovery and planning
- [x] T1.1 Inspect existing modules, POMs, shared infrastructure, `.kiro/`, templates
- [x] T1.2 Create task folder and all mandatory documents from `templates/tasks/`
- [x] T1.3 Create Kiro spec (requirements, design, tasks)
- [ ] T1.4 User review of plan; resolve open questions (D5, Bedrock access, default model) — gates M2
- [ ] T1.5 Create `.kiro/steering/` (rules, structure, AI security, Sonar, task-first) → T1.4
- [ ] T1.6 Create `.kiro/hooks/` (see Hook plan) → T1.5
- [ ] T1.7 Extend reviewer prompts with AI-specific checks and steering resources → T1.5
- [ ] T1.8 Update `.kiro/README.md` evidence index and `PROJECT_STRUCTURE.md` (ai-core/ai-web package lists) → T1.4

## M2 — AI Core foundation → T1.4
- [ ] T2.1 POM changes: springboot (AWS BOM, json-schema version, surefire group exclusion), ai-core (micrometer, json-schema, test deps)
- [ ] T2.2 Shared `ErrorCode`/`statusFor` additive change + tests (only if D5 approved) 
- [ ] T2.3 `AiProperties` + validation tests → T2.1
- [ ] T2.4 DTO records (`AiRequest`, `AiResponse`, `AiUsage`, `AiGenerationOptions`, `AiRequestMetadata`)
- [ ] T2.5 Typed exceptions → T2.2
- [ ] T2.6 `AiClient`, `AiProvider`, `AiCoordinator` interfaces → T2.4
- [ ] T2.7 Prompt service + `prompts/` resources + render tests → T2.4, T2.5
- [ ] T2.8 Structured output service + schemas + tests → T2.5
- [ ] T2.9 Limiter, retry executor, token budget + tests → T2.3, T2.5
- [ ] T2.10 `MockAiProvider` + tests → T2.6
- [ ] T2.11 `DefaultAiCoordinator`, registry, metrics recorder + tests → T2.7–T2.10
- [ ] T2.12 `AiAutoConfiguration`, `AiProductionValidator`, `AutoConfiguration.imports` + context tests → T2.11
- [ ] T2.13 Run `mvn verify` for ai-core and springboot; record in `implementation.md`/`milestone.md`

## M3 — Real provider → T2.13
- [ ] T3.1 Bedrock dependency wiring (sync client only), `dependency:tree` check
- [ ] T3.2 `BedrockAiProvider` (Converse) + error mapping + readiness
- [ ] T3.3 Mocked-SDK tests (I-04), credentials-missing and invalid-credentials tests
- [ ] T3.4 Tagged live-provider test (disabled by default)

## M4 — AI Web → T2.13
- [ ] T4.1 ai-web POM test deps
- [ ] T4.2 DTOs + mapper + bean validation
- [ ] T4.3 `AiRestController` with `@PreAuthorize`
- [ ] T4.4 `AiExceptionHandler` (`@Order(HIGHEST_PRECEDENCE)`, AI exceptions only)
- [ ] T4.5 `AiWebAutoConfiguration` + `AutoConfiguration.imports`
- [ ] T4.6 Web tests I-05…I-13 (test application class under test sources only)

## M5 — AI Frontend → T4.3
- [ ] T5.1 Toolchain (Vite, Vitest, ESLint, react-dom) and scripts `build`, `lint`, `test`
- [ ] T5.2 API client + types + error mapping
- [ ] T5.3 Overview, Playground, Structured Response Viewer, Request History views
- [ ] T5.4 Frontend tests F-01…F-08

## M6 — Review and hardening → M3, M4, M5
- [ ] T6.1 Run `ghostbridge-security-reviewer` (separate agent) → `security-review.md`
- [ ] T6.2 Run `ghostbridge-code-quality-reviewer` (separate agent) → `code-quality-review.md`
- [ ] T6.3 Fix findings; independent re-review of each fix
- [ ] T6.4 `mvn clean verify` and `mvn dependency:tree` from root; `npm run build/lint/test`
- [ ] T6.5 Sonar: run if configured, otherwise record "not verified"

## M7 — App readiness → M6
- [ ] T7.1 Public contract document + integration examples (in `implementation.md`)
- [ ] T7.2 Test demonstrating a structured request through `AiClient`
- [ ] T7.3 Completion report; update `architecture.md` and Kiro evidence

## Hook plan (T1.6; implemented in `.kiro/hooks/`, no recursion, report-only)
| Trigger | Action |
|---|---|
| Save `ghostbridge-ai-core/**/*.java` | `mvn -q -pl ghostbridge-ai-module/ghostbridge-ai-core -am test` (focused, tests only) and report |
| Save `ghostbridge-ai-web/**/*.java` | same for ai-web |
| Save `ghostbridge-ai-frontend/src/**/*.ts(x)` | `npm run typecheck && npm run lint` in the frontend |
| Spec task marked complete | Ask the agent to compare against verification evidence and propose (not auto-write) `tasks.md`/`milestone.md` updates |
Hooks never edit source or the files that trigger them and never approve results.

## In progress
- [ ] T1.4 awaiting user review

## Test and review gates
- [ ] Tests U/I/S/F pass (see `test-cases.md`)
- [ ] `security-review.md` complete, no unresolved Critical/High
- [ ] `code-quality-review.md` complete, complexity ≤ 15
- [ ] `mvn clean verify` green from repository root

## M2 execution order (approved 2026-11-21; Bedrock adapter and AWS SDK deferred to M3)
T2.2 ErrorCode+handler+tests → T2.1 POM (json-schema-validator version in springboot POM; micrometer-core + test deps in ai-core) → T2.4/T2.6 DTOs+interfaces → T2.3 properties → T2.10 mock → T2.7–T2.9,T2.11 prompt/structured/limiter/retry/budget/coordinator → T2.5 exceptions → T2.12 auto-config + prod validator → unit tests → T6.1/T6.2-style reviews for M2 → fixes → docs.

## M2 follow-up: literals class (user request)
- [x] T2.14 (verified 2026-11-21: `mvn -o clean verify` BUILD SUCCESS; ai-core 90 tests, web 53, springboot-core 31) Create `GhostBridgeAiLiterals`, replace hard-coded literals/patterns in ai-core main + tests, add `constants` to PROJECT_STRUCTURE.md, `mvn clean verify` green, no behavior change

## M2 remediation (approved 2026-11-21) — status tracked here
- [x] T2.15 Revalidate key findings with failing adversarial tests (F-01, F-02, F-03, CQ-01, CQ-02) (verified 2026-11-21: BUILD SUCCESS; tests 31/55/173)
- [x] T2.16 Shared: `ErrorCode.PAYLOAD_TOO_LARGE`/`QUOTA_EXCEEDED`, explicit `statusFor`, regression table (verified 2026-11-21: BUILD SUCCESS; tests 31/55/173)
- [x] T2.17 AI core fixes per disposition table in `implementation-plan.md` (verified 2026-11-21: BUILD SUCCESS; tests 31/55/173)
- [x] T2.18 New/updated unit tests; `mvn -o clean verify` green (verified 2026-11-21: BUILD SUCCESS; tests 31/55/173)
- [x] T2.19 (rounds 1-3 complete 2026-11-21) Independent re-review (security + quality) of current code; reconcile findings; update review reports, `implementation.md`, `test-cases.md`, `changelog.md`
- [x] T2.20 (done 2026-11-21 with caveats in `milestone.md`) Mark M2 complete only if all 9 Medium findings resolved, no Critical/High, deferred gates recorded

## Mandatory deferred gates (not part of M2)
- [ ] **G-M3-1** Provider byte limits enforced before any real provider is enabled (blocks M3 completion)
- [ ] **G-M4-1** Per-requester rate/concurrency/budget from trusted authentication identity before exposing any AI Web endpoint (blocks M4 completion; global limits stay as secondary safeguard)
- [ ] **G-M4-2** (blocks M4 completion) `Retry-After`, DTO size bounds, `reason` exposure decision, and mapping `AiRequest` construction errors (`INVALID_INPUT` 400 / `INPUT_TOO_LARGE` 413) through Jackson/DTO conversion so they are not wrapped as 400-malformed or 500
