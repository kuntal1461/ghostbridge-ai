# Deploying GhostBridge on one VPS

Status: **built and run locally with Docker Desktop (2026-11-24)** against `SITE_ADDRESS=localhost` and a real local model
(`phi3:3.8b`, CPU): images build, the stack becomes healthy, the SPA and API work through Caddy, and Ollama is unreachable from
outside. **Not yet verified on a public host:** a real certificate (ACME), the public firewall, and client addresses seen from
the internet (only a local second client address was tested). See the Milestone 6 `test-cases.md` for evidence.

```
Internet --443--> proxy (Caddy, TLS, static SPA) --/api--> app (Spring Boot, :8080 internal) --http--> ollama (:11434 internal)
```

- The only published ports are 80 and 443 (the proxy). Networks: `edge` (proxy, internet), `web` (proxy and app, no route out), `models`
  (app and ollama, no route out), so the proxy cannot reach the model server and the model server cannot call out. Ollama also runs with
  `OLLAMA_NO_CLOUD=1`.
- Caddy is built with the `caddy-ratelimit` module: 30 requests/min per client address on `/api/v1/auth/*` and 60/min on `/api/v1/ai/*`,
  on top of the application's login lockout and per-user limit. Caddy is the only thing facing the internet and hands the application
  one `X-Forwarded-For` value it builds itself; the application (profile `prod`) trusts it only from private proxy addresses
  (`server.tomcat.remoteip.internal-proxies`).
- The SPA and the API share one origin, so no CORS is needed and the session cookie stays `SameSite=Strict`.
- One executable jar (`ghostbridge-app-web`); AI Web's own controller is switched off in this application.

## Steps
1. **Host:** a Linux VPS with Docker and the Compose plugin. Firewall: allow only 22 (from your address), 80 and 443. Never open 11434 or 8080.
2. **Build:** from the repository root, `mvn -B clean package -DskipTests`, then `cd deploy && docker compose build`.
3. **Secrets:** `cp .env.example .env`, edit it, `chmod 600 .env`. Create the demo user's hash on your own machine
   (`htpasswd -nbBC 10 x 'password' | cut -d: -f2`, prefix `{bcrypt}`); put the password in the submission notes, the hash in `.env`, wrapped in single quotes (Compose would otherwise expand the `$` characters and corrupt it).
4. **Start:** `docker compose up -d`. The proxy starts only after the application reports ready.
5. **Provision the model** (once; internet needed temporarily because the stack's networks have no route out). Pull it into the stack's volume with a
   throw-away container on the default bridge (tested with a small model; use the same digest as `compose.yml`):
   ```
   docker volume create deploy_ollama_models      # compose names it <project>_ollama_models; "deploy" when run from deploy/
   docker run -d --rm --name ollama-pull -v deploy_ollama_models:/root/.ollama -e OLLAMA_NO_CLOUD=1 ollama/ollama@sha256:4793b080b3a70fbcca3a79672bd208ff7b3c51692a9ba9b3a319bf1e3915c0a9
   docker exec ollama-pull ollama pull <model>    # then: docker rm -f ollama-pull
   ```
   Alternatively copy `~/.ollama/models` (manifest and blobs of that model only) into the volume. Set `OLLAMA_MODEL` to that exact name.
6. **Check:** `https://<SITE_ADDRESS>/actuator/health` returns UP; sign in; run a structured request; confirm `curl http://<server-ip>:11434` from outside fails.

## Conditions before exposing a public host (from the security review)
1. A random demo password of at least 16 characters; rotate or remove it after judging (login limits are per client address, so a client with many addresses, such as an IPv6 /64, is limited only by bcrypt and password strength).
2. Ports are published on IPv4 only (`0.0.0.0:80/443` in `compose.yml`) because behind the Docker port proxy IPv6 clients can appear with one shared source address, which would collapse the per-client limits. If you enable IPv6, test lockout and edge limits from two real remote addresses first.
3. From outside the host, only 22 (restricted), 80 and 443 answer; 8080 and 11434 do not. Real certificate (ACME needs port 80) and HSTS checked on the public name.
4. `deploy/.env` is mode 600 and never committed; `deploy/.env` holds only a bcrypt hash (it is visible through `docker inspect`; accepted for a demo).
5. Images are pinned by digest and the Caddy rate-limit module by version; rebuild deliberately.

## Sizing
A model that fits RAM is mandatory: a 3-4B class model needs roughly 3-6 GB. Without a GPU expect several seconds to tens of
seconds per request; the application caps concurrency (`ghostbridge.ai.rate-limit`, per-user limit) and uses a 120 s timeout.
Measured on a developer laptop only: `phi3:3.8b` answered a small structured request in about 4-7 s. Re-measure on the VPS.

## Operations
Restart to change any setting (read once at startup). Logs contain request ids, models, token counts and durations, never prompts,
passwords or hashes. Upgrade: rebuild, `docker compose up -d --build`. Rollback: keep the previous image tag.

## Structured-output schema allow-lists (two settings, two enforcement points)
A structured request names a schema (for example `generic-summary`); an operator-controlled allow-list maps it to the registered prompt
that produces it, and clients can only pick a name from that list. The rule itself (`AiStructuredOutputs.approvedPrompt`) is shared; there are two lists because there are two entry points.

| Property | Enforced in | Role in this application |
|---|---|---|
| `ghostbridge.app.ai.structured-schemas` (map: schema name → prompt name; default `generic-summary` → `generic-structured`) | `DefaultAppAiService` (the App service) for `/api/v1/ai/generate/structured` | **The effective list.** Change this one. |
| `ghostbridge.ai.web.structured-schemas` (same shape and default) | `DefaultAiApiService` (AI Web's own service), only when AI Web's controller is on | **Inactive here.** `ghostbridge.ai.web.rest-enabled=false`, so its service is not created. The property is still bound and format-validated at startup, but nothing uses it. |

Notes: `application.yml` sets `generic-summary` explicitly and Spring merges maps by key, so another configuration source can add or re-point entries but cannot remove that one. An empty
value (`structured-schemas:` with nothing) is rejected at startup rather than silently replaced by the default (unit-tested for the App property); to approve a different set, list the
entries you want and re-point or accept `generic-summary`. (The App record treats a programmatically created empty map as "approve nothing"; AI Web's treats it as the default; configuration cannot produce either.) The schema choice shown in the browser comes from front end configuration and is only a convenience: the server list decides.
