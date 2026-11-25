# Code Quality Review — GB-AI-005

> Provenance: executed by a Claude Code subagent following the Kiro role prompt in `.kiro/agents/prompts/`; not run by the Kiro agent runtime. Read-only run; the report text was saved here by the implementer (findings condensed from the reviewer's table, wording shortened, IDs and severities unchanged).

**Reviewer:** ghostbridge-code-quality-reviewer (independent, read-only)  **Date:** 2026-11-24  **Round:** 1

## Scope and checks performed
Read in full: `AiProperties`, `OllamaEndpointValidator`, `GhostBridgeAiLiterals`, `AiSecret`, the two new test classes; diffs of `AiAutoConfiguration`, `OllamaAiProvider`, `AiOllamaAutoConfigurationTest`, `AiOllamaPropertiesTest`; task docs, `rules.md`, role prompt. Checked literals/static/wildcard imports (none violating), constructor injection, no new modules or dependencies, unique class name, no web dependency from core. Ran `mvn -o -B -pl ghostbridge-ai-module/ghostbridge-ai-core clean test`: BUILD SUCCESS, 338 tests, 0 failures, 1 skipped (live test, not run). Did not run `clean verify`/`install`.

## Sonar status
Not configured; full Sonar compliance not verified. Complexity figures are manual estimates; rule IDs are the rules a scanner would likely apply, not scan results.

## Findings
No Critical or High findings. Hard rules met (cognitive complexity ≤ 15 per method, no static/wildcard literal imports, constructor injection).

| ID | Severity | File:line | Issue | Recommendation | Status |
|---|---|---|---|---|---|
| CQ-01 | Medium | `AiProperties.java:203-231,253,273` | State duplicated: flat `baseUrl`/`apiKey` mirrored into `cloud`; `mode` is a third form of `localEnabled`; selection asymmetric (flat vs `local.baseUrl()`); `Ollama` constructor grew to 10 components (source-incompatible for external callers). | Make `cloud` the single holder; flat keys input aliases only, or document flat accessors as derived. | Open |
| CQ-02 | Medium | `AiProperties.java:219-248` | `Ollama` canonical constructor does ~8 jobs, estimated cognitive complexity ~12 (target ≤ 10); timeout bound check duplicated. | Extract helpers (required-local, timeout bounds, byte limits). | Open |
| CQ-03 | Low | validator/`AiProperties` methods | Estimates: `literalAddress` ~9, `isPrivateIpv4` ~6, `validateProvider` ~10; none > 15. | Optional split. | Open |
| CQ-04 | Medium | `AiProperties.java:204,220-221,241,263-273` | Boxed `Boolean localEnabled` auto-unboxed in several places; accessor returns `Boolean`; reason for boxing not explained. | Use primitive if blank binding allows, else copy to a local boolean and document. | Open |
| CQ-05 | Medium | `AiProperties.java:92-105,335-341` | Legacy `model-id` now must match the model regex (deliberate, SEC-4) but docs present it as unchanged. | State as a behavioural change. | Open |
| CQ-06 | Low | `AiProperties.java:101,337` | Model regex matched in two places with different messages. | Reuse `validModel`. | Open |
| CQ-07 | Low | `AiProperties.java:298-315` | `pick`/`pickKey` share a pattern. | Optional generic helper. | Open (accepted) |
| CQ-08 | Medium | `AiProperties.java:237-240` | Cloud destination validated even in local mode (an explicit empty `allowed-hosts` fails startup naming `cloud.base-url`); an invalid `local.*` URL fails startup while local is disabled; undocumented and untested. | Document and test (fail-fast on both blocks) or skip validation of the unselected mode. | Open |
| CQ-09 | Low | `OllamaEndpointValidator.java:19-24,91-125` | Network constants partly private, partly in literals class. | Name RFC ranges; align constants. | Open |
| CQ-10 | Low | `AiProperties` prefix/URL strings; `OllamaAiProvider` | `"ghostbridge.ai.ollama."` repeated; hard-coded default cloud URL duplicates cloud-domain literal. | Derive from `PROPERTY_PREFIX`; build default from constants. | Open |
| CQ-11 | Low | `OllamaEndpointValidator.java:48,55` | `String.valueOf(null)` trick; two null paths. | Explicit null guard. | Open |
| CQ-12 | Low | validator vs `requirements.md` SEC-1 | fe80::/10 accepted but not in the requirement; untested. | Align requirement/implementation, add test. | Open |
| CQ-13 | Low | `AiProperties.java:295,323` | `lowerCase` NPE on null list element. | Reject/filter null and blank entries. | Open |
| CQ-14 | Low | `GhostBridgeAiLiterals.java:25-34` | Section comment ("no hosts or models") and "only supported mode" Javadoc outdated. | Update. | Open |
| CQ-15 | Low | `OllamaAiProvider.java:72`; `AiAutoConfiguration.java:48-50`; `AiProperties.java:24-26` | Javadocs still cloud-only wording / "credential check". | Reword. | Open |
| CQ-16 | Low | `OllamaAiProvider.java:64-91` | `sendCredential` derived twice; key forced empty in local mode (overlapping state). | Optional simplification. | Open |
| CQ-17 | Low | new tests | Fully qualified names inline. | Import them. | Open |
| CQ-18 | Low | `AiOllamaLocalModeTest` | Brittle message-substring assertions; `assertRejected` checks only `BindException`. Strength: all negative cases also assert no URL/secret echo. | Prefer property name + stable fragment. | Open |
| CQ-19 | Medium | `AiOllamaLocalModeTest.java:88-105` | Missing cases: `[fe80::1]`, `[::]`, `[::ffff:127.0.0.1]`, `[::ffff:8.8.8.8]`, leading-zero octets, uppercase `LOCALHOST`, port 0, trailing-dot hosts, whitespace-only values, `local-enabled=true` with empty `allowed-hosts`, bad `local.*` URL while disabled, log assertion for the INFO line (TC-16). | Add. | Open |
| CQ-20 | Low | `AiOllamaLocalModeTest.java:175-187` | Test named "profile" uses two map sources, not profile mechanics. | Rename. | Open |
| CQ-21 | Low | task docs | TC statuses "Planned", execution log empty, tasks unchecked. | Update after re-run. | Open |
| CQ-22 | Info | `OllamaEndpointValidator.java` | Justified abstraction; only tested through binding. | None. | Closed |

## Limitations
Sonar not run; complexity are manual estimates. Only the core module's offline tests were run; no `clean verify`/`install`, other modules, frontend, or live test. Edge-case `InetAddress` behaviour from reading code, no scratch tests. Security not reviewed here. Coverage not measured.

## Disposition by the implementer (2026-11-24, not independently re-reviewed)
| ID | Disposition |
|---|---|
| CQ-01 | Partly addressed: the Javadoc now states the flat `baseUrl`/`apiKey` are derived and `mode` mirrors `localEnabled`. The duplicated-state record shape and the 10-component constructor (source-incompatible for external callers; none exist in the repository) are **accepted** to keep the legacy flat keys bindable without a custom binder. |
| CQ-02 | Fixed in part: validation moved into `validateDestinations` and a shared `requireTimeout`, removing the duplicated timeout check. Constructor still performs several steps (estimated lower than before; not measured). |
| CQ-04 | Documented: the boxed `Boolean` is required because a blank placeholder cannot bind to a primitive (verified by test); normalised in the constructor. |
| CQ-05 | Documented below as a behavioural change. |
| CQ-06, 08, 11, 12, 13, 14, 15, 17, 20, 21 | Fixed (model validation reused; both destinations validated and tested; explicit null guard; fe80 removed and requirement aligned; allow-list entries validated; Javadocs; imports; test renamed; docs updated). |
| CQ-09, 10 | Partly fixed: property prefix and default URL derive from literals; RFC range numbers and `PORT_MAX`/`OCTET_MAX` stay as private constants in the validator (protocol facts). |
| CQ-03, 07, 16, 18 | Accepted Low (optional splits; `pick`/`pickKey` kept separate; provider field redundancy; some message-substring assertions remain, all negative cases also assert no URL/secret echo). |
| CQ-19 | Fixed: added the listed cases (fe80, `::`, mapped IPv6, leading zeros, uppercase, port 0, trailing dot, blank, bad local URL while disabled). The log-content assertion for the INFO/WARN line was **not** added. |
| CQ-22 | No action. |
Behavioural changes to note: `ghostbridge.ai.model-id` must now match `[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}` when `provider=ollama`; blank flat `ollama.base-url` is still rejected; link-local addresses need allow-listing; a bad `local.*` or cloud value fails startup even if that mode is not selected.

---
# Round 2 — 2026-11-24 (independent, read-only; saved by the implementer)
The reviewer ran `mvn -o -B -pl ghostbridge-ai-module/ghostbridge-ai-core clean test` (373 tests, 0 failures, 2 skipped); no live tests, no Sonar (not configured), complexity by manual estimate (largest: `Ollama` constructor about 12, `validateProvider` about 10, `literalAddress` about 9; all ≤ 15). Null-safety of the tri-state `Boolean` and every branch of `resolveSelection` were checked and found covered.

| ID | Severity | Issue | Disposition |
|---|---|---|---|
| CQ2-01 | High (docs/compat) | Docs claimed default-cloud and unchanged compatibility; code failed a previously valid env-only cloud config. | **Fixed:** docs rewritten; the breaking change and migration are recorded (requirements FR-9, impact analysis, implementation). |
| CQ2-02, CQ2-03 | Medium (docs) | Stale WARN/`hasUnusedLocalSettings` text; execution log said 366 tests. | **Fixed.** Log updated to 376 tests, 2 skipped (live tests). TC-11 still uses two map sources, not profile mechanics (described as precedence). |
| CQ2-04, CQ2-05 | Low | `cloudConfigured` boolean readability; constructor density. | **Fixed:** `cloudSettingsPresent`, `isPresent`, `requireByteLimits` extracted. |
| CQ2-06 | Low | Blank `mode` inconsistent with blank flag. | **Fixed** (blank = unset). |
| CQ2-07 | Low | Property-name strings in `Local`/`Cloud`/selection message. | **Fixed** (shared prefix). Remaining message text stays inline. |
| CQ2-08, 09, 10, 16 | Low | 10-component record, provider constructor arity, overlapping `sendCredential`/`apiKey`, inline RFC ranges. | Accepted (unchanged from round 1). |
| CQ2-11, CQ2-12 | Low | Javadoc "Credentials are never properties"; `chatCompletionsUri` implicit unboxing. | **Fixed.** |
| CQ2-13, 14, 15 | Low | `catchBind` wrapper; weak malformed-flag assertion; contrived `withApiKey` in local test. | 14 **fixed** (asserts `local-enabled` in the message); 13 and 15 accepted. |
| CQ2-17 | Info | Branch coverage of `resolveSelection` complete. | None. |
Gate: no Critical/High on code. Fixes not independently re-reviewed.
