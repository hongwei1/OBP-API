#!/bin/bash
# Poll OBP-API's JSON introspection endpoints into a CSV time series.
#
# OBP-API ships no Prometheus/JMX/Micrometer exporter, so resource observation
# during a load scenario has to come from these HTTP endpoints plus jcmd
# (see jvm-snapshot.sh). This script is the data source every Tier-B scenario
# reads from.
#
# Usage:
#   ./poll-endpoints.sh [-o out.csv] [-i interval_sec] [-d duration_sec]
#
# Env overrides:
#   OBP_BASE      default http://127.0.0.1:8080
#   OBP_USERNAME / OBP_PASSWORD / OBP_CONSUMER_KEY   DirectLogin credentials

set -uo pipefail

OBP_BASE="${OBP_BASE:-http://127.0.0.1:8080}"
OBP_USERNAME="${OBP_USERNAME:-}"
OBP_PASSWORD="${OBP_PASSWORD:-}"
OBP_CONSUMER_KEY="${OBP_CONSUMER_KEY:-}"

OUT="poll-$(date +%Y%m%d-%H%M%S).csv"
INTERVAL=1
DURATION=60

while getopts "o:i:d:" opt; do
  case "$opt" in
    o) OUT="$OPTARG" ;;
    i) INTERVAL="$OPTARG" ;;
    d) DURATION="$OPTARG" ;;
    *) echo "usage: $0 [-o out.csv] [-i interval_sec] [-d duration_sec]" >&2; exit 2 ;;
  esac
done

if [[ -z "$OBP_USERNAME" || -z "$OBP_PASSWORD" || -z "$OBP_CONSUMER_KEY" ]]; then
  echo "ERROR: set OBP_USERNAME, OBP_PASSWORD and OBP_CONSUMER_KEY (DirectLogin credentials)." >&2
  exit 2
fi

# ---- 1. DirectLogin ---------------------------------------------------------
DL_HEADER="DirectLogin username=\"${OBP_USERNAME}\",password=\"${OBP_PASSWORD}\",consumer_key=\"${OBP_CONSUMER_KEY}\""
TOKEN_BODY=$(curl -sS -m 10 -X POST "${OBP_BASE}/my/logins/direct" \
  -H "Content-Type: application/json" -H "DirectLogin: ${DL_HEADER}" 2>/dev/null)
TOKEN=$(printf '%s' "$TOKEN_BODY" | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("token",""))
except Exception: print("")' 2>/dev/null)

if [[ -z "$TOKEN" ]]; then
  echo "ERROR: DirectLogin failed. Response was:" >&2
  echo "$TOKEN_BODY" >&2
  exit 1
fi
AUTH="Authorization: DirectLogin token=\"${TOKEN}\""

# ---- 2. Preflight -----------------------------------------------------------
# The pool endpoint's handler only calls withUser, but ResourceDocMiddleware
# enforces the ResourceDoc's declared roles (Http4s600.scala: canGetDatabasePoolInfo),
# so an authenticated user without that entitlement still gets 403. Fail loudly
# here rather than silently recording -1 for the whole run.
POOL_URL="${OBP_BASE}/obp/v6.0.0/system/database/pool"
PRE_CODE=$(curl -sS -m 10 -o /tmp/poll-preflight.json -w "%{http_code}" -H "$AUTH" "$POOL_URL" 2>/dev/null)
if [[ "$PRE_CODE" == "403" ]]; then
  echo "ERROR: 403 on ${POOL_URL}." >&2
  echo "  User '${OBP_USERNAME}' lacks the CanGetDatabasePoolInfo entitlement." >&2
  echo "  Grant it, then re-run. Response:" >&2
  cat /tmp/poll-preflight.json >&2
  exit 1
elif [[ "$PRE_CODE" != "200" ]]; then
  echo "ERROR: preflight on ${POOL_URL} returned HTTP ${PRE_CODE}:" >&2
  cat /tmp/poll-preflight.json >&2
  exit 1
fi
echo "preflight OK — pool endpoint readable, writing to ${OUT}"

# ---- 3. Poll loop -----------------------------------------------------------
echo "ts,elapsed_s,pool_active,pool_idle,pool_total,pool_waiting,pool_max,status_code,status_db,status_redis,cache_total_keys,cache_redis_available" > "$OUT"

START=$(date +%s)
END=$((START + DURATION))
while [[ $(date +%s) -lt $END ]]; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START))

  POOL_JSON=$(curl -sS -m 5 -H "$AUTH" "$POOL_URL" 2>/dev/null)
  STATUS_CODE=$(curl -sS -m 5 -o /tmp/poll-status.json -w "%{http_code}" "${OBP_BASE}/status" 2>/dev/null)
  STATUS_JSON=$(cat /tmp/poll-status.json 2>/dev/null)
  CACHE_JSON=$(curl -sS -m 5 -H "$AUTH" "${OBP_BASE}/obp/v6.0.0/system/cache/info" 2>/dev/null)

  ROW=$(POOL="$POOL_JSON" STATUS="$STATUS_JSON" CACHE="$CACHE_JSON" python3 - <<'PY'
import json, os

def load(name):
    try:
        return json.loads(os.environ.get(name, "") or "{}")
    except Exception:
        return {}

pool, status, cache = load("POOL"), load("STATUS"), load("CACHE")

def g(d, k, default=""):
    v = d.get(k, default)
    return "" if v is None else v

# /status nests its per-dependency verdicts; tolerate either flat or nested shapes.
checks = status.get("checks", status)
print(",".join(str(x) for x in [
    g(pool, "active_connections", -1),
    g(pool, "idle_connections", -1),
    g(pool, "total_connections", -1),
    g(pool, "threads_awaiting_connection", -1),
    g(pool, "maximum_pool_size", -1),
    g(checks, "database", "?"),
    g(checks, "redis", "?"),
    g(cache, "total_keys", -1),
    g(cache, "redis_available", "?"),
]))
PY
)
  # Splice the HTTP status of /status in between the readiness verdicts.
  IFS=',' read -r pa pi pt pw pm sdb sredis ck cra <<< "$ROW"
  echo "$(date -Iseconds),${ELAPSED},${pa},${pi},${pt},${pw},${pm},${STATUS_CODE},${sdb},${sredis},${ck},${cra}" >> "$OUT"

  sleep "$INTERVAL"
done

echo "done — ${OUT} ($(wc -l < "$OUT") lines incl. header)"
