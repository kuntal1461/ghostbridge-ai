# Impact Analysis — GB-AI-002 (Ollama Cloud Provider Integration)

## Dependencies on Spring Boot infrastructure
No change to `ghostbridge-springboot`. Security chain, CORS, exception handling, correlation id and `ErrorCode` mapping are reused as-is (all provider failures already map through `AiErrorReason`). No second security/CORS/exception configuration is added.

## Changes affecting other projects
| Area | Impact |
|---|---|
| `ghostbridge-ai-web` | None in this task (no controllers). It will later consume `AiClient` only. |
| GhostBridge App | None. When integrated it supplies `ghostbridge.ai.provider=ollama`, `ghostbridge.ai.model-id`, optional `ghostbridge.ai.ollama.*` and the runtime `OLLAMA_API_KEY`. |
| Frontend | None, and it must stay that way: no key, no `VITE_` variable, no direct provider call. |
| Maven | No new module or dependency planned. |

## API / contract compatibility
Public contracts (`AiClient`, `AiRequest`, `AiResponse`, `AiProvider`) are unchanged. New configuration keys are additive with restrictive defaults. `AiProperties` gains a nested `Ollama` record (constructor signature change is internal to the module; tests are updated).

## Security implications
- **New outbound path** to a third-party processor over HTTPS with a bearer credential. Mitigations: allow-listed host, no redirects, bounded bodies, secret type, no logging of requests/headers.
- **Credential lifecycle:** the previously shared key is compromised and must be revoked at the provider by the owner; a replacement is injected only through the runtime environment. Rotation = restart with a new env value; no code or config change.
- **Cost/abuse:** global rate/concurrency/budget stay active. **G-M4-1 (per-requester limits from trusted identity) remains mandatory before any AI Web endpoint**; nothing here exposes model invocation to unauthenticated callers.
- **Data exposure:** prompts leave the system to Ollama Cloud; only sanitized, delimited, size-limited content is sent. Operational documentation must state this.
- **Supply chain:** no new dependency, so no new vulnerable library; JDK TLS defaults.

## Operational implications
- Startup fails fast (without printing any value) if `provider=ollama` and no key/model/valid base URL.
- Provider readiness is visible through `AiProvider.status()` (safe text only).
- Secret handling procedure: key only in the runtime environment/secrets manager; `.env` (if used locally) is git-ignored; `.env.example` has empty placeholders only.
- Secret scanning before every commit/push; a hit blocks the **commit** locally through `.githooks/pre-commit` (advisory: `git commit --no-verify` skips it, and it is only active where `git config core.hooksPath .githooks` was run) and triggers revocation + a safe, user-approved history-remediation procedure (no automatic rewrite). **A CI or server-side secret scan (for example Gitleaks in CI or GitHub push protection) is still required for real push-time protection and is not part of this task.**

## Affected files (planned)
New/changed under `ghostbridge-ai-module/ghostbridge-ai-core/**`, `.gitignore`, `.env.example`, `.kiro/specs/ghostbridge-ai/*`, `doc-architecture/PROJECT_STRUCTURE.md` (if class notes change), this task folder. Not touched: `ghostbridge-springboot/**`, `ghostbridge-app/**`, `ghostbridge-ai-web/**`, frontend, root `pom.xml`.

## Residual operational risks (from the independent reviews)
- JVM debug switches can print request headers: keep `jdk.httpclient.HttpClient.log` (headers/content/trace/all), `javax.net.debug` (plaintext/all) unset wherever the credential is configured. The provider refuses launch-time settings of these properties but cannot see `$JAVA_HOME/conf/net.properties` and the JDK reads the settings once at class initialization (best effort).
- Retried generation requests can be billed more than once; retries are bounded by `retry.max-attempts`.
- The JDK `HttpClient` honors JVM/system proxy settings; TLS remains end to end but a proxy sees the destination host.
- `ghostbridge.ai.ollama.api-key` can be supplied from any Spring property source (YAML, command line, `-D`); only the *fallback* is limited to the OS environment. Operators must not put it in committed YAML. A consuming app's Actuator `env`/`configprops` exposure (`show-values=ALWAYS`) would reveal it: keep Actuator values hidden (M4/app responsibility).
