# Implementation — GB-AI-005

Record of what was actually done (Claude Code, not Kiro).

## Milestone 2-3: properties, validation, provider, auto-configuration
- **Configuration contract** (`ghostbridge.ai.*`, validated at startup, read once, restart required to change):
```yaml
ghostbridge:
  ai:
    provider: ollama            # unchanged provider switch
    ollama:
      local-enabled: true       # REQUIRED (no default): true = self-hosted Ollama, false = Ollama Cloud (paid)
      local:
        base-url: http://127.0.0.1:11434
        model: qwen2.5-coder:3b
        allowed-hosts: []       # optional container/VPN host names approved by the operator
      cloud:
        base-url: https://ollama.com   # optional (default)
        model: deepseek-v4.1-flash
        api-key: ${OLLAMA_API_KEY:}    # optional: the backend also reads OLLAMA_API_KEY from the OS environment when blank
```
  No `application.yml` was added: no runnable application exists in the repository (`ghostbridge-app-web` has no main class), and library modules must not carry duplicate environment configuration (rules §6). The contract above is for the executable application, and `.env.example` documents the environment variable forms.
- **Preserved / migrated:** `provider=ollama`, `model-id`, `ollama.base-url`, `ollama.api-key`, `ollama.allowed-hosts`, `ollama.mode` keep working as aliases (cloud). Both forms set to different values fail startup. `model-id` that differs from the selected `local.model`/`cloud.model` fails startup. `connect-timeout`, `max-request-bytes`, `max-response-bytes` unchanged.
- **Classes changed:** `AiProperties` (`Ollama` extended with `localEnabled`, `Local`, `Cloud`, alias resolution, `credentialRequired()`, `selectedModel()`, `chatCompletionsUri()` by mode; model-id resolution/validation), new package-private `OllamaEndpointValidator`, `OllamaAiProvider` (credential checked/sent only in cloud mode; per-mode status text), `AiAutoConfiguration` (env key fallback only when the credential is required; one INFO line with mode and model), `GhostBridgeAiLiterals` (local mode, http scheme, cloud domain, model regex).
- **Provider selection logic:** selection happens once, when `AiAutoConfiguration` builds the single `OllamaAiProvider` from `AiProperties.Ollama`. `AiProperties.modelId()` carries the selected model, so `DefaultAiCoordinator`, the approved-model check, `/provider/status` and every service use it without code changes. Controllers, the `AiProvider` interface and AI Core public contracts are untouched. The mock provider path is untouched.
- **Local mode:** no `Authorization` header, no key required, `OLLAMA_API_KEY` ignored even if present; endpoint is http(s) plain origin on loopback, private IPv4/IPv6 literal (10/8, 172.16/12, 192.168/16, 169.254/16, 127/8, ::1, fc00::/7, fe80::/10) or an allow-listed name; no DNS lookups during validation; `ollama.com` (and subdomains) rejected; redirects still never followed; failures are reported, never retried elsewhere.
- **Cloud mode:** unchanged rules (https, allow-listed host, no custom port, TLS defaults); credential required with a startup failure naming only the settings.
- **Decisions (final):** the mode has no default. See "Safe provider selection" below. Blank legacy `base-url` is still rejected; other blank placeholders count as unset; the startup log shows mode and model only (never URL or key)..
- **Local Ollama setup requirements:** install and run Ollama on the same host or a private network, bind it to loopback or a private interface (`OLLAMA_HOST=127.0.0.1:11434`, the default), never publish port 11434 to the internet or put it behind an unauthenticated public proxy, pull the model (`ollama pull qwen2.5-coder:3b`), set `ghostbridge.ai.timeout` generously for slow local hardware, and restart GhostBridge after changing the flag (no refresh scope).
- **Tests added:** `AiOllamaLocalModeTest` (selection, URL and model matrix, aliases, conflicts, property-source override, mock unaffected), `OllamaLocalProviderTest` (no Authorization header even with a cloud key configured, no cross-endpoint traffic, unreachable local server, cloud still needs a key), plus four context tests in `AiOllamaAutoConfigurationTest`. Existing tests updated only for renamed message text.
- **Not done:** live local Ollama inference was not run (no Ollama server was installed or started for this task); no paid or cloud call was made.

## Review fixes (2026-11-24)
- Link-local ranges (`169.254/16`, `fe80::/10`) are **not** accepted by default (supersedes the list above); allow-list them explicitly. Allow-list entries are validated; the cloud-host guard ignores a trailing dot; empty ports and leading-zero IPv4 octets are rejected.
- Local mode builds its HTTP client without a proxy selector; cloud keeps the JDK default.
- Startup logs `ai.ollama.selected mode=… model=…`. (The WARN for unused local settings from round 1 was removed when the selection became explicit.)
- Behavioural changes: `model-id` must match the model pattern for `provider=ollama`; both the cloud and the local destination are validated at startup whenever present (an explicit empty `ollama.allowed-hosts` therefore fails even in local mode); blank placeholders (`local-enabled=`) count as unset.
- Restart/reload: configuration is bound once at startup; changing the flag, endpoint or model requires a restart.
- Remaining risks: allow-listed names are trusted and DNS-resolved at request time; http to such hosts is cleartext on the network; Ollama's port must be kept off public interfaces by the operator; live local inference unverified.

## Safe provider selection (final design, 2026-11-24)
- `Ollama.localEnabled` is now a nullable `Boolean`: `true` local, `false` cloud, `null` undetermined. Resolution (`resolveSelection`): explicit `local-enabled` wins; else the legacy `mode` (`local`/`cloud`, blank = unset; a flag/mode mismatch fails); else **local** is inferred only when `local.*` settings exist and no cloud setting (`cloud.*`, `api-key`, `base-url`) does; otherwise undetermined. `AiProperties` rejects an undetermined mode whenever `provider=ollama`: "ghostbridge.ai.ollama.local-enabled must be set explicitly: true for a local Ollama, false for Ollama Cloud (paid)". Other providers (mock, bedrock, none) are unaffected.
- Consequences: a missing, blank, malformed (`maybe`, `ture`, `2` fail at bind) or misspelled flag cannot reach the paid cloud. A misspelled flag with only `local.*` settings runs local; with cloud settings too, startup fails. `OLLAMA_API_KEY` in the environment never selects cloud. Local-to-cloud fallback does not exist.
- **Breaking change (deliberate, per the brief "require explicit Cloud selection"):** configurations that were implicitly cloud (provider=ollama + model + env key, or only `api-key`/`cloud.model`/`base-url`) now fail startup; migration is `ghostbridge.ai.ollama.local-enabled=false`. Legacy `mode=cloud` still selects cloud explicitly.
- Structural notes after the round-2 review: `cloudSettingsPresent`, `requireByteLimits`, `modeName` helpers extracted; `Local`/`Cloud` use the shared property prefix.

## Local verification (2026-11-24)
- Service: Ollama (macOS app, `ollama` CLI not on PATH) answering on `127.0.0.1:11434`, listening on loopback only (`lsof`), HTTP 200.
- Installed models: `qwen2.5-coder:32b` (19.9 GB), `llama3.1:8b` (4.9 GB), `mistral:latest` (4.4 GB), `phi3:3.8b` (2.2 GB), `qwen2.5-coder:1.5b-base` (base model), `nomic-embed-text` (embeddings only). No download was needed. Selected the smallest chat model, **`phi3:3.8b`**.
- Test: new manual `OllamaLocalLiveProviderTest` (tag `live-provider`, runs only with `-Dlive.local.provider=true` and `GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL`), which builds `AiProperties` with `local-enabled=true`, `local.base-url=http://127.0.0.1:11434`, `local.model=phi3:3.8b`, constructs the real `OllamaAiProvider` from the validated settings and sends one request ("Reply with Hello GhostBridge AI", max 32 tokens, temperature 0). `OLLAMA_API_KEY` was not set; no Ollama Cloud request was made.
- Result (two runs, the second on the final code): **real inference succeeded**; response mapped to `AiProviderResult` (65 characters), duration **4729 ms** (first run, includes model load) and **3673 ms** (second run), token usage reported by Ollama: **31 input / 20 output**. The provider does not expose the HTTP status; success means a 2xx was received and parsed.
- Not verified: structured (JSON-mode) output against the local model, the coordinator path against the local server, and behaviour of the other installed models.
