# Security Review — GB-AI-005

> Provenance: executed by a Claude Code subagent following the Kiro role prompt in `.kiro/agents/prompts/`; not run by the Kiro agent runtime. Read-only run; the report text was saved here by the implementer.

**Reviewer:** ghostbridge-security-reviewer (independent, read-only)  **Date:** 2026-11-24  **Round:** 1

## Scope and checks performed
Read the role prompt, requirements/plan/implementation docs, `OllamaEndpointValidator`, `AiProperties`, `AiSecret`, `OllamaAiProvider`, `AiAutoConfiguration`, `GhostBridgeAiLiterals`, `.env.example`, the generated configuration metadata, the ai-web status VO and service. Compiled the real validator into a scratch directory and ran about 60 hostile endpoint strings against `validateLocal` and 7 against `validateCloud` (IPv4/IPv6 literals, IPv4-mapped and -compatible IPv6, decimal/octal/hex/short IPv4, userinfo tricks, trailing dots, uppercase, percent-encoded, unicode/full-width, backslashes, tabs, empty and zero ports, `file://`). `InetAddress.getByName` was used for literals only (no DNS, no network).
Not done: no Spring context or Maven run, no live Ollama, no scanner, no network, no API key; tests judged from names and signatures.

## Findings
| ID | Severity | File:line | Issue | Impact | Remediation | Status |
|---|---|---|---|---|---|---|
| SEC-R1 | Medium | `AiProperties.java:204,220`; `AiAutoConfiguration.java:68` | `local-enabled` defaults to false (cloud) and Spring ignores unknown keys, so a typo such as `local-enable=true`, or a blank `GHOSTBRIDGE_AI_OLLAMA_LOCAL_ENABLED=`, leaves cloud mode active without error. With `OLLAMA_API_KEY` set, prompts would go to Ollama Cloud although local-only was intended. Only the INFO line shows the mode. A malformed value such as `maybe` fails at bind. | Silent data egress to a third party when local was intended. | Log cloud selection prominently, warn when local settings exist but cloud is selected, document the typo risk; optionally require an explicit flag. | See disposition |
| SEC-R2 | Low | `OllamaEndpointValidator.java:113-114,124` | Link-local `169.254.0.0/16` and `fe80::/10` accepted, including the cloud metadata address (`http://169.254.169.254` accepted). Path is fixed and method POST, so exploitation is narrow. | Blind SSRF toward link-local services if an operator points the endpoint there. | Drop link-local from the default accept set; require allow-listing. | See disposition |
| SEC-R3 | Low | `OllamaEndpointValidator.java:55-61`; `AiProperties.java:223,321-323` | `local.allowed-hosts` entries not validated; `ollama.com.` (trailing dot) passes the cloud-host guard when allow-listed, so local mode could send cleartext prompts to the hosted service. Allow-listed names are resolved by DNS at request time. | Operator-controlled misconfiguration; rebinding/poisoned names reach arbitrary hosts. | Normalize and reject trailing dots, wildcards, blanks; strip the trailing dot in the cloud check; document DNS trust. | See disposition |
| SEC-R4 | Low | `OllamaAiProvider.java:105-109` | `HttpClient` uses the JDK default `ProxySelector`; with JVM proxy properties set, local requests to private/allow-listed hosts may go through a proxy (documented JDK behaviour, not run). Also affects the existing cloud path. | Local prompts through an unintended proxy. | Disable proxies for local mode; document. | See disposition |
| SEC-R5 | Low | `OllamaEndpointValidator.java:52,19,89-92` | `http://127.0.0.1:` (empty port) accepted; leading-zero octets accepted and parsed as decimal on JDK 21 (`010.0.0.1` → 10.0.0.1). No mismatch seen on this JDK, but another resolver could read octal. | Robustness only. | Reject empty ports and leading-zero octets. | See disposition |
| SEC-R6 | Info | `AiAutoConfiguration.java:68`; status VO | Startup log prints only mode and model (model passes the safe regex). REST status exposes provider and model only; no endpoint, key or mode. Frontend has no reference to Ollama settings. | None. | None. | Closed (verified) |

Held up under probing: userinfo/host confusion, `127.1`, decimal/hex IPv4, `localhost.`, `[::ffff:8.8.8.8]`, `[::]`, `0.0.0.0`, `100.64/10`, uppercase, unicode and percent-encoded hosts, cloud-host guard (`ollama.com`, `OLLAMA.COM`, `api.ollama.com`), key never sent or read in local mode, no mode-to-mode fallback, redirects NEVER, model regex excludes whitespace/quotes/control characters, conflicting legacy/new keys and `model-id` vs selected model fail startup.

## Missing tests (as reported)
Trailing-dot hosts (with and without allow-listing), uppercase, IPv4-mapped IPv6, `169.254.169.254`, leading-zero octets, empty port, unicode hosts; allow-list hygiene; flag typo/malformed value behaviour; env key present with local mode end to end; 3xx not followed; proxy bypass.

## Limitations and remaining risks
No live local Ollama, Spring context, Maven build or scanner was run by the reviewer; validator probing only; behaviour depends on JDK 21 literal parsing. By design: text-only host judgement, allow-listed names trusted and resolved at request time, http to allow-listed hosts is cleartext, Ollama port 11434 must stay on loopback/private networks (not enforceable by the app). Not a statement that the application is free of vulnerabilities.

## Gate
Critical/High unresolved: 0. Medium: 1 (SEC-R1). Result: pass, with SEC-R1 to be addressed before relying on local mode for privacy-sensitive data (dispositions below).

## Disposition by the implementer (2026-11-24, not independently re-reviewed)
| ID | Disposition |
|---|---|
| SEC-R1 (Medium) | **Mitigated, residual accepted:** the flag still defaults to cloud (backward compatibility; requiring it would break every existing deployment). A WARN is now logged when local settings are present but cloud is selected (the exact signature of a misspelled flag), `Ollama.hasUnusedLocalSettings()` is tested, a malformed value fails at bind (tested), and `.env.example`/docs call out the risk. Operators who need a hard guarantee of "no cloud egress" should unset `OLLAMA_API_KEY` in local-only deployments: cloud mode then fails startup. A strict "require explicit flag" option was not added. |
| SEC-R2 | **Fixed:** `169.254.0.0/16` and `fe80::/10` are no longer accepted by default (including `169.254.169.254`); they need `local.allowed-hosts`. Tests added (rejected by default, accepted when listed). |
| SEC-R3 | **Fixed:** allow-list entries must be plain host names or IP literals (no wildcard, trailing dot, port, path, blank); the cloud-host guard strips a trailing dot, so an allow-listed `ollama.com.` is still refused. DNS trust of allow-listed names remains an operator responsibility (documented, accepted). |
| SEC-R4 | **Fixed for local mode:** the HTTP client bypasses JVM proxy settings when no credential is sent. The cloud path keeps the JDK default proxy selector (corporate proxies may be required to reach the cloud); not tested (no hermetic way to assert the proxy selector). |
| SEC-R5 | **Fixed:** empty port and leading-zero octets are rejected (tests added). |
| Missing tests | Added: link-local, trailing dots, uppercase, IPv4-mapped IPv6, leading zeros, empty/zero port, allow-list hygiene, malformed flag, unused-local-settings, redirect not followed. Not added: env-key + local end-to-end through a full context beyond `localModeIgnoresAnOperatingSystemCredential` (already present), proxy test. |

---
# Round 2 — 2026-11-24 (independent, read-only; saved by the implementer)
Scope: the tri-state provider selection, validator hygiene fixes, no-proxy local client, manual live test. The reviewer ran `mvn -o -B -pl ghostbridge-ai-module/ghostbridge-ai-core clean test` (373 tests, 0 failures, 2 skipped), probed the real binder with 19 selection scenarios and the compiled validator with about 40 hostile URLs (no network, no key, no live tests). One probe run used a stale class from the reviewer's own scratch directory and was redone against `target/classes`.

| ID | Severity | Issue | Disposition |
|---|---|---|---|
| SEC2-1 | Medium | Legacy cloud markers alone (`api-key`, `cloud.model`, typo'd local keys plus a cloud setting) still inferred the paid cloud silently. | **Fixed:** inference can now only select local; cloud requires explicit `local-enabled=false` or `mode=cloud`. Tests: `cloudIsNeverInferredFromCloudSettingsAlone`, `anEnvironmentOnlyCloudSetupWithoutTheFlagFailsStartupInsteadOfCallingThePaidCloud`. Breaking change documented with migration step. |
| SEC2-2 | Low | A blank `ollama.mode` failed startup for every provider, inconsistent with a blank flag. | **Fixed:** blank mode counts as unset; tests updated. |
| SEC2-3 | Low | Docs claimed a default-cloud flag, a WARN and `hasUnusedLocalSettings()` that no longer exist. | **Fixed:** requirements, implementation, impact analysis, test cases, changelog rewritten to the final design. |
| SEC2-4 | Info | Accepted boolean forms (`yes`, `on`, `1`, ` TRUE `) work; `ture`/`maybe` fail the bind. | Verified; tests assert the failures. |

Round-1 dispositions verified by the reviewer: SEC-R2, R3, R5 fixed (code and probes), R4 verified by code only (no hermetic test), R1 superseded by the explicit selection. Credential leakage in local mode: none found (key never read, never sent, key configured alongside is dropped); no secret in logs for the code read.
Missing tests listed: proxy-bypass test (no hermetic way), log-line assertion for the mode line. Added since: blank mode, env-only migration, relaxed env names.
Limitations: no live cloud or local call by the reviewer, no scanner; allow-listed names are DNS-trusted and http to them is cleartext; port 11434 must be kept private by the operator; JDK debug logging (`jdk.httpclient.HttpClient.log=content`) would print local prompts and the startup guard against it applies to cloud mode only.
Gate: Critical/High unresolved 0; Medium 0 after the fix above (fixes not independently re-reviewed). Result: pass.
