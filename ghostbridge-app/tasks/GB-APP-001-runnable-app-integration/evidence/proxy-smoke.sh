#!/bin/bash
# Controlled local proxy verification against https://localhost (self-signed local CA). Never prints credentials.
B=https://localhost; PW=${DEMO_PASSWORD:?set DEMO_PASSWORD}; TMPDIR=${TMPDIR:-/tmp}; J=$(mktemp); trap 'rm -f $J' EXIT
c() { curl -sk -m 20 "$@"; }  # a later -m overrides
echo "1 SPA:"; c -o /dev/null -w "  / -> %{http_code} %{content_type}\n" $B/; c -o /dev/null -w "  /playground (deep link) -> %{http_code} %{content_type}\n" $B/playground
echo "2 headers on SPA:"; c -D - -o /dev/null $B/ | grep -iE "^(strict|x-content|x-frame|referrer|content-security|permissions|server)" | tr -d '\r'
echo "3 anonymous API:"; c -o /dev/null -w "  /api/v1/ai/usage -> %{http_code}\n" $B/api/v1/ai/usage; c -o /dev/null -w "  /api/v1/auth/me -> %{http_code}\n" $B/api/v1/auth/me
echo "4 actuator:"; c -o /dev/null -w "  health -> %{http_code}\n" $B/actuator/health; c -o /dev/null -w "  env -> %{http_code}\n" $B/actuator/env; c -o /dev/null -w "  metrics -> %{http_code}\n" $B/actuator/metrics
echo "5 csrf + cookie flags + API headers:"; c -c $J -D - $B/api/v1/auth/csrf -o $TMPDIR/csrf.json | grep -iE "^(HTTP|set-cookie|cache-control|content-security|strict|x-content|x-frame)" | tr -d '\r' | sed 's/JSESSIONID=[^;]*/JSESSIONID=<redacted>/'
H=$(python3 -c "import json;print(json.load(open('$TMPDIR/csrf.json'))['headerName'])"); T=$(python3 -c "import json;print(json.load(open('$TMPDIR/csrf.json'))['token'])")
echo "6 login without CSRF:"; c -b $J -o /dev/null -w "  -> %{http_code}\n" -X POST $B/api/v1/auth/login -d "username=demo&password=x"
echo "7 login ok:"; c -b $J -c $J -H "$H: $T" -o $TMPDIR/login.json -w "  -> %{http_code}\n" -X POST $B/api/v1/auth/login --data-urlencode "username=demo" --data-urlencode "password=$PW"; python3 -c "import json;d=json.load(open('$TMPDIR/login.json'));print('  user=',d.get('username'),'roles=',d.get('roles'),'keys=',sorted(d))"
c -b $J -c $J $B/api/v1/auth/csrf -o $TMPDIR/csrf.json; H=$(python3 -c "import json;print(json.load(open('$TMPDIR/csrf.json'))['headerName'])"); T=$(python3 -c "import json;print(json.load(open('$TMPDIR/csrf.json'))['token'])")
echo "8 signed-in:"; c -b $J -o /dev/null -w "  me -> %{http_code}\n" $B/api/v1/auth/me; c -b $J -w "  status -> %{http_code}\n" $B/api/v1/ai/provider/status | sed 's/$//' | head -3
echo "9 AI POST without CSRF:"; c -b $J -o /dev/null -w "  -> %{http_code}\n" -X POST $B/api/v1/ai/generate -H 'Content-Type: application/json' -d '{"promptName":"generic-structured","variables":{"text":"hi"}}'
echo "10 real local inference through proxy (structured):"; c -b $J -H "$H: $T" -H "Content-Type: application/json" -m 200 -w "\n  -> %{http_code} in %{time_total}s\n" -X POST $B/api/v1/ai/generate/structured -d '{"schemaName":"generic-summary","variables":{"text":"GhostBridge is a self-hosted AI app. It keeps all inference on a local model."}}' | cut -c1-500
echo "11 usage:"; c -b $J $B/api/v1/ai/usage | cut -c1-400; echo
echo "12 logout then protected:"; c -b $J -c $J -H "$H: $T" -o /dev/null -w "  logout -> %{http_code}\n" -X POST $B/api/v1/auth/logout; c -b $J -o /dev/null -w "  me after logout -> %{http_code}\n" $B/api/v1/auth/me; c -b $J -o /dev/null -w "  usage after logout -> %{http_code}\n" $B/api/v1/ai/usage
