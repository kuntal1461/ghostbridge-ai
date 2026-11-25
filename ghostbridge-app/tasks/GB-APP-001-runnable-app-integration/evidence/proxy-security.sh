#!/bin/bash
B=https://localhost; J=$(mktemp); trap 'rm -f $J' EXIT
c() { curl -sk -m 20 "$@"; }
tok() { c -b $J -c $J $B/api/v1/auth/csrf -o /tmp/t.json; python3 -c "import json;d=json.load(open('/tmp/t.json'));print(d['headerName'],d['token'])"; }
echo "A lockout cannot be evaded with forged X-Forwarded-For (7 failed logins, a different forged address each):"
read H T < <(tok)
for i in 1 2 3 4 5 6 7; do
  code=$(c -b $J -c $J -H "$H: $T" -H "X-Forwarded-For: 203.0.113.$i" -H "X-Real-IP: 198.51.100.$i" -o /dev/null -w "%{http_code}" -X POST $B/api/v1/auth/login --data-urlencode "username=locktest" --data-urlencode "password=wrong-$i")
  echo "  attempt $i -> $code"
done
echo "B the same user name from the (single) real address with the right header set stays locked:"
c -b $J -H "$H: $T" -o /dev/null -w "  -> %{http_code}\n" -X POST $B/api/v1/auth/login --data-urlencode "username=locktest" --data-urlencode "password=x"
echo "C edge rate limit on /api/v1/auth/* (40 quick requests, forged XFF each):"
for i in $(seq 1 40); do c -o /dev/null -w "%{http_code}\n" -H "X-Forwarded-For: 192.0.2.$i" $B/api/v1/auth/me; done | sort | uniq -c
echo "  429 headers:"; c -D - -o /dev/null $B/api/v1/auth/me | grep -iE "^(HTTP|retry-after)" | tr -d '\r'
echo "D what the Docker host publishes (compose project in ../../../../deploy):"
( cd "$(dirname "$0")/../../../../deploy" && docker compose ps --format '  {{.Service}}: ports=[{{.Ports}}]' && echo "  ollama 11434 mapping: [$(docker compose port ollama 11434 2>&1)]" && echo "  app 8080 mapping: [$(docker compose port app 8080 2>&1)]" )
echo "  (a host process listening on 127.0.0.1:8080 would be unrelated to this stack; it publishes no 8080)"
echo "E http -> https redirect:"; curl -s -m 5 -o /dev/null -w "  http://localhost/ -> %{http_code} %{redirect_url}\n" http://localhost/
echo "F path-normalisation probes through the edge (none may expose anything operational or skip a control):"
for path in /Actuator/env /actuator/health/../env //actuator/env /api//v1/ai/usage /API/V1/AI/usage "/api/v1/auth;x=1/me" /actuator/health/../metrics; do
  printf "  %-34s -> %s\n" "$path" "$(curl -sk -m 10 --path-as-is -o /dev/null -w '%{http_code}' "$B$path")"
done
echo "G request body over the edge cap (300 KB) is refused:"
head -c 307200 /dev/zero | tr '\0' 'a' > "${TMPDIR:-/tmp}/big.txt"; curl -sk -m 20 -o /dev/null -w "  -> %{http_code}\n" -X POST "$B/api/v1/auth/login" -H 'Content-Type: application/x-www-form-urlencoded' --data-binary @"${TMPDIR:-/tmp}/big.txt"; rm -f "${TMPDIR:-/tmp}/big.txt"
