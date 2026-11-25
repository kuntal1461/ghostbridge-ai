# Code Quality Review — GB-APP-001 (Milestone 6)

Reviewer: `ghostbridge-code-quality-reviewer` (independent, read-only; ran no commands). Date: 2026-11-24. Round 1.
Gate: pending the four Medium findings (CQ-01…CQ-04) and an explanation of CQ-05. No Critical/High. Sonar/coverage not configured, so not measured. No method estimated above 15 (highest ≈14, `AiUsageSummarizer.summarize`).

Passed: layering (app-core has no servlet/web/frontend deps), single `@SpringBootApplication`, single config set, no HTTP hop to AI Web, constructor injection, no wildcard/static literal imports, no new versions in poms.

## Findings and disposition

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| CQ-01 | Medium | Structured-output approval/check and provider-status logic duplicated between `DefaultAppAiService` and `DefaultAiApiService`; two allow-list properties | **Open — follow-up task.** AI Web's service is switched off in the App, so no behaviour divergence today; extract one AI Core class when AI Web is next touched. |
| CQ-02 | Medium | Per-requester limit lives in `AppAiController`, not in the service | **Open — follow-up.** Only the controller calls `AppAiService`; move the permit into a service decorator. |
| CQ-03 | Medium | Usage/option mapping and `noStore`/ceil-seconds helpers duplicated; `AppAiProviderStatus` mirrors the AI Web VO | **Open — follow-up** with CQ-01. |
| CQ-04 | Medium | PROJECT_STRUCTURE.md package table incomplete; stale "returns 401" text | **Fixed.** Table lists `config`, `constants`, `security`, `mapper`, `responsevo`; status text updated. |
| CQ-05 | Medium | One unexplained `AppRealHttpTest` failure | **Not reproduced** (green on every later run including a clean build on 2026-11-24). Cause undetermined; stale `target/classes` is the leading suspicion. Kept open. |
| CQ-06 | Low | `AuthRestController` has no security-enabled gate | Open (latent; default enabled). |
| CQ-07 | Low | Hard-coded `JSESSIONID`, key prefixes, `"no-store"` | **Partly fixed:** `SESSION_COOKIE_NAME` constant. Prefix/no-store constants open. |
| CQ-08 | Low | Complexity target ≤10 (`summarize` ≈14, `User` ctor ≈9–10) | Open (under the hard limit of 15). |
| CQ-09 | Low | Redundant same-package imports, FQNs in tests | Open. |
| CQ-10 | Low | Validation split between annotations and constructors | Open. |
| CQ-11 | Low | Empty `structured-schemas` silently defaulted | Open. |
| CQ-12 | Low | Test isolation (shared users/limiter state) | Open. |
| CQ-13 | Low | Stale `auth.ts` header and docs | **Fixed** (`auth.ts` header, PROJECT_STRUCTURE). |
| CQ-14 | Low–Med | Post-login refresh failure reported as sign-in failure | **Fixed** (non-fatal). Logout rejection / strategy recreation open. |
| CQ-15 | Low | Login form a11y (aria-describedby etc.) | Open. |
| CQ-16 | Low | `isRecord`/`noStore` duplicated | Open. |
| CQ-17 | Low | Unused public exports of the AI frontend | Open. |
| CQ-18 | Low | Cross-module script path; `internal: true` blocks `ollama pull`; deploy unverified | Open for the deploy run. |
| CQ-19 | Low | Double `@ConditionalOnAiRestApi` undocumented | Open (needed because the component scan also finds the controller). |
| CQ-20 | Low | Global limiter lock; implicit `Clock` coupling; per-user lock | Lock part addressed by S-2; rest open. |

## Limits
No tests or builds run by the reviewer; complexity by hand; `deploy/` and browser UI unexercised.

---

# Round 2 (2026-11-24) — final changed code

Reviewer: independent, read-only (no commands run; found no `.kiro/agents/prompts` file, used the agent JSON and rules). **Verdict: pass with conditions, no Critical/High.**

| ID | Round-2 result | Evidence / disposition |
|---|---|---|
| CQ-01 | **Resolved** | Both services call `AiStructuredOutputs`, `AiProviderReadiness.overview`; no copy left |
| CQ-02 | **Resolved** | Permit taken in `RequesterLimitedAppAiService` (`@Primary`), released on exception (new test) |
| CQ-03 | **Resolved** | One `toUsageResponse`/`toProviderStatus`/`toOptions` in `AiApiMapper`; `AiDurations.ceilSeconds` shared; `AppAiProviderStatus` removed |
| CQ-04 | **Resolved** (regressed once) | The reviewer found `service.impl` missing from the app-web row (CQ-21) → added with the reason |
| CQ-05 | **Open, not reproduced** | Investigation recorded in test-cases.md; root cause unconfirmed |

New/residual findings and dispositions:
- CQ-21 (Medium, package table) — **fixed**.
- CQ-24 (decorator test weak: vacuous `verify`, no release-on-failure, no pass-through) — **fixed** (3 tests now).
- CQ-25 (duration test misplaced, no direct `requireStructured` test) — **fixed** (`AiDurationsTest`, direct test).
- CQ-22/23 (controller still reads limiter/properties to render usage; awkward comment/Javadoc) — open, Low.
- CQ-26 (`Math.max(1, ceilSeconds(..))` twice; AI-named util used by login filter) — open, Low.
- CQ-27 (add a security-chain precondition to `AppRealHttpTest`) — open, Low-Med.
- CQ-28 (two structured-schema allow-lists): **accepted with conditions** — different entry points, AI Web's is inert in the App; logic now shared. Follow-up: document which key applies where; optionally extract validation into AI Core.
- CQ-09 (import order/FQNs), CQ-06..08, 10..20 from round 1: open, Low, not re-reviewed.
- The two client messages now live in `GhostBridgeAiLiterals` (they were already client-facing literals in AI Web's service); the reviewer left this as a note.

Limits: nothing built or run by the reviewer; Sonar and coverage not measured.

---

# Round 3 (2026-11-25) — final code and deployment

Reviewer: independent, read-only. **Verdict: pass with one condition (documentation sync, now done); no Critical/High, no open Medium.**

| ID | Result |
|---|---|
| CQ-06, 07, 11, 14, 15, 19, 21, 23, 24, 25, 26, 27 | Verified in code |
| CQ-29 (docs contradicted the evidence) | **Fixed**: `test-cases.md` deployment/proxy/final-run sections and counts, `implementation.md`, `changelog.md`, `milestone.md`, `impact-analysis.md`, `PROJECT_STRUCTURE.md`, README status |
| CQ-30 (README allow-list wrong/untested) | **Fixed**: README rewritten (AI Web property is bound/validated but unused here; maps merge by key; empty value fails startup); two new unit tests |
| CQ-31 (password file not ignored) | **Fixed** |
| CQ-32 (unpinned module/images) | **Fixed** |
| CQ-33 (model-pull command) | **Fixed**: replaced by `docker run` + `docker exec`, tested with a 45 MB model into a scratch volume |
| CQ-34, 35, 37, 38 | **Fixed** (comment; `retryAfterSeconds` test and fixed clock; evidence scripts env-configured with exit code; stricter healthcheck) |
| CQ-36, 39, 40 | Deferred/accepted: literal duplication of the default schema names, a stale structure-table remark, hash in container environment |
| CQ-08, 09, 10, 16, 17, 20, 22 | Open, Low, accepted (no behavioural risk); CQ-12 improved (clock flake removed) |
| CQ-05 | Open, not reproducible; **accepted as a documented low residual risk**; the new `@BeforeEach` preconditions make a recurrence diagnosable |
| CQ-28 | Accepted with the runbook condition now met |

Limits: nothing built or run by the reviewer; Sonar and coverage not measured; complexity by hand.
