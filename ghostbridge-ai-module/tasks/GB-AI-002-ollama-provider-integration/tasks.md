# Tasks — GB-AI-002 (Ollama Cloud Provider Integration)

A task is ticked only after its acceptance criteria are verified. `→` marks dependencies.

## A — Docs and secret hygiene
- [x] T1 Inspect repository, rules, M2 reviews; credential/secret checks (tree, history, tracked files, `.gitignore`, scanner availability)
- [x] T2 Create task folder and documents
- [x] T3 Update `.gitignore` (secret patterns) and add `.env.example` (empty placeholders) → T2
- [x] T4 Verify ignore rules with `git check-ignore` (G-02)
- [x] T5 Update Kiro spec (Ollama replaces Bedrock as the real provider) and note the M3 provider change in GB-AI-001 docs → T2

## B — Configuration and credential type → T2
- [x] T6 `AiSecret` (masked toString/JSON) + tests
- [x] T7 Literals for fixed Ollama protocol constants
- [x] T8 `AiProperties.Ollama` (mode, base-url, allowed-hosts, api-key, connect-timeout, byte limits) + provider `ollama` validation (model id required) + tests
- [x] T9 `AiAutoConfiguration`: Ollama provider bean, key resolution (property → `OLLAMA_API_KEY`), safe failure, readiness → T6, T8

## C — Provider → T9
- [x] T10 Wire VOs (`OllamaChatRequestVO`, `OllamaChatMessageVO`, `OllamaChatResponseVO`)
- [x] T11 `OllamaAiProvider` (HttpClient, no redirects, request/response byte limits, status/timeout/JSON mapping)
- [x] T12 Stub-server tests P-01…P-22

## D — Integration and redaction → T12
- [x] T13 Coordinator integration + mock regression (R-01…R-03)
- [x] T14 Redaction tests S-01…S-07
- [x] T15 Optional live test (disabled by default; L-01 stays "Not run")
- [x] T16 `mvn clean verify`, `mvn dependency:tree`

## E — Reviews and closure → T16
- [x] T17 Secret scans G-01: regex fallback run on tree, staged files, tracked files and history (scanner not installed); no credential found
- [x] T18 `ghostbridge-security-reviewer` → `security-review.md` (independent; round 1 + round 2; 0 Critical/High)
- [x] T19 `ghostbridge-code-quality-reviewer` → `code-quality-review.md` (independent; round 1 + round 2; 0 Critical/High)
- [x] T20 Fix findings (all Medium fixed; cheap Lows fixed), update docs and Kiro spec, completion report (see `milestone.md`)

## Gates carried over
- [x] G-M3-1 provider byte limits — implemented and verified by P-17…P-20 (request/response bytes, `max_tokens`, overall deadline); independently reviewed (rounds 1-2)
- [ ] G-M4-1 per-requester limits from trusted identity — **still mandatory before any AI Web endpoint; not part of this task**
- [ ] G-M4-2 `Retry-After`, DTO bounds, AiRequest error mapping — not part of this task

## Approvals
Commit and push require explicit user approval. Nothing is committed or pushed by this task unprompted.

## Open follow-ups (not blockers for this task)
- [ ] CI / server-side secret scan (e.g. Gitleaks in CI or GitHub push protection) — the local hook is advisory
- [ ] Install Gitleaks locally (not installed; hook falls back to regex)
- [ ] Revoke the previously shared (compromised) Ollama key at the provider — owner action
- [ ] Optional manual live check (`OllamaLiveProviderTest`) with a replacement key, with the user's explicit approval
- [ ] M4: Actuator value hiding in the consuming app; G-M4-1, G-M4-2
