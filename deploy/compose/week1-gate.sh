#!/usr/bin/env sh
# Week 1 gate (+ I3), on the running compose stack:
#   login (JWT) → nurse places an order (REST) → pharmacist validates in the UI → OrderValidated on Artemis
#   → dispatch assigns a robot → robot-sim moves. One traceId is sent on every call and must show in every log.
#
#   docker compose -f deploy/compose/docker-compose.yml up -d --build --wait
#   sh deploy/compose/week1-gate.sh
#
# Needs: curl, docker compose. Exit code 0 = gate passed.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
COMPOSE="docker compose -f $HERE/docker-compose.yml"
TRACE="w1-gate-$(date +%s)"
PASSWORD="${DOSERUNNER_DEMO_PASSWORD:-DoseRunner-2026}"
fail=0

ok()   { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
ko()   { printf '  \033[31mFAIL\033[0m %s\n' "$*"; fail=1; }
step() { printf '\n==> %s\n' "$*"; }
json() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/"; }   # tiny JSON string extractor (no jq needed)

step "traceId for this run: $TRACE"

step "1. Login on identity-service (RS256 JWT)"
token() {
  curl -s -X POST localhost:8081/auth/login -H 'Content-Type: application/json' -H "x-request-id: $TRACE" \
       -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}" | json accessToken
}
NURSE=$(token nurse)
[ "${#NURSE}" -gt 100 ] && ok "nurse got a JWT (${#NURSE} chars)" || ko "nurse login failed"

step "2. Nurse places an order (POST /orders with the token)"
body=$(curl -s -w '\n%{http_code}' -X POST localhost:8082/orders -H "Authorization: Bearer $NURSE" \
       -H 'Content-Type: application/json' -H "x-request-id: $TRACE" \
       -d '{"patientRef":"P-GATE","drugCode":"PARA-500","qty":1,"ward":"CARDIO-2","room":"214"}')
status=$(printf '%s' "$body" | tail -n1)
ORDER=$(printf '%s' "$body" | head -n1 | json id)
[ "$status" = 201 ] && ok "201, order $ORDER (status PLACED)" || ko "expected 201, got $status"

step "3. Pharmacist validates it in the UI (POST /ui/pharmacy/orders/{id}/validate)"
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "localhost:8082/ui/pharmacy/orders/$ORDER/validate" \
         -H "x-request-id: $TRACE")
[ "$status" = 302 ] && ok "validated (302 back to the queue)" || ko "expected 302, got $status"

step "4. Event flow: order → Artemis → dispatch → robot-sim (waiting up to 20 s)"
i=0
while [ $i -lt 20 ]; do
  $COMPOSE logs --no-log-prefix robot-sim 2>/dev/null | grep -q "$TRACE" && break
  sleep 1; i=$((i + 1))
done
for svc in identity-service order-service dispatch-service robot-sim; do
  n=$($COMPOSE logs --no-log-prefix "$svc" 2>/dev/null | grep -c "$TRACE" || true)
  [ "$n" -gt 0 ] && ok "$svc: $n log line(s) with traceId $TRACE" || ko "$svc: no log line with $TRACE"
done
$COMPOSE logs --no-log-prefix dispatch-service 2>/dev/null | grep "$TRACE" | grep -q "OrderValidated" \
  && ok "dispatch received OrderValidated with the same traceId" || ko "dispatch: no OrderValidated with $TRACE"
ROBOT=$($COMPOSE logs --no-log-prefix robot-sim 2>/dev/null | grep "$TRACE" | grep -o 'robot=[A-Z0-9-]*' | head -n1 | cut -d= -f2)
[ -n "$ROBOT" ] && ok "robot-sim accepted the mission for $ROBOT" || ko "robot-sim: no mission accepted"

step "5. robot-sim moves (dispatch receives telemetry with a changing position)"
sleep 4
moves=$($COMPOSE logs --no-log-prefix --since 30s dispatch-service 2>/dev/null \
        | grep "Updated telemetry for ${ROBOT:-R-} at" | grep -o 'at ([0-9]*, [0-9]*)' | sort -u | wc -l)
[ "$moves" -gt 1 ] && ok "${ROBOT:-robot} reported $moves different positions in the last 30 s" \
                   || ko "no movement seen in telemetry"

if [ $fail -eq 0 ]; then printf '\n\033[32mWeek 1 gate PASSED\033[0m (traceId %s)\n' "$TRACE"
else printf '\n\033[31mWeek 1 gate FAILED\033[0m (traceId %s)\n' "$TRACE"; fi
exit $fail
