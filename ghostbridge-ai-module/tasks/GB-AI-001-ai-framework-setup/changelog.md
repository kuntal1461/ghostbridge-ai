# Changelog — GB-AI-001 (AI Framework Setup)

| Date | Change | Files |
|---|---|---|
| 2026-11-21 | M1: task folder and planning documents created; no code changes | `ghostbridge-ai-module/tasks/GB-AI-001-ai-framework-setup/*` |
| 2026-11-21 | M1: Kiro spec created (requirements, design, tasks) | `.kiro/specs/ghostbridge-ai/*` |
| 2026-11-21 | M2: ai-core foundation (DTOs, properties, mock provider, coordinator, prompts, structured output, limiter/retry/budget); shared ErrorCode+handler additive change; Kiro steering/hooks | `ghostbridge-ai-module/ghostbridge-ai-core/**`, `ghostbridge-springboot/**`, `.kiro/**` |
| 2026-11-21 | M2: T2.14 literals moved to `GhostBridgeAiLiterals` (done, behavior unchanged, +3 tests) | `ghostbridge-ai-core/**/constants/` |
| 2026-11-21 | M2: literals import convention — static imports replaced by one normal class import (`GhostBridgeAiLiterals.X`), no wildcards; rule added to `rules/rules.md` §3 | `ghostbridge-ai-core/**`, `rules/rules.md` |
| 2026-11-21 | M2 remediation: strict structured output, delimited variables, Unicode-aware delimiter handling, typed null/runtime handling, config bounds + fail-fast tests, error contract (413/429/429-quota/502), mock-profile guard, idempotent permit, INTERRUPTED, exhaustive statusFor, duplicate checks, id validation, Javadoc; shared `ErrorCode.PAYLOAD_TOO_LARGE`/`QUOTA_EXCEEDED` | `ghostbridge-ai-core/**`, `ghostbridge-springboot-{core,web}` |
| 2026-11-21 | M2 round-2 fixes: linear delimiter regex (N-01), local-only schemas (N-02), eager mock guard under lazy init (N-03), null prompt name (N-04), non-fatal telemetry, more look-alike variants | `ghostbridge-ai-core/**` |
| 2026-11-21 | Docs: deferred gates G-M3-1, G-M4-1, G-M4-2 recorded; steering/rules updates (`constants` package, mock profile rule, naming) | `tasks.md`, `milestone.md`, `.kiro/steering/*`, `rules/rules.md` |
| 2026-11-21 | M2 final: NFKC expansion cap, `$id` rejected in schemas, stronger fixtures/tests, spec design.md corrected, G-M4-2 clarified; M2 marked complete with caveats | `ghostbridge-ai-core/**`, `.kiro/specs/**`, task docs |
