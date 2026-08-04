#!/bin/bash
# Periodic JVM snapshots of the running OBP-API process.
#
# Complements poll-endpoints.sh: the HTTP endpoints show pool saturation, this
# shows *why* — how many threads are parked in a blocking Await (the
# Connector.scala Await.result on the shared fork-join EC is the suspected
# amplifier), plus heap/Metaspace drift over a soak.
#
# Usage:
#   ./jvm-snapshot.sh [-o outdir] [-i interval_sec] [-d duration_sec] [-p pid]

set -uo pipefail

OUTDIR="jvm-snapshots-$(date +%Y%m%d-%H%M%S)"
INTERVAL=10
DURATION=60
PID=""

while getopts "o:i:d:p:" opt; do
  case "$opt" in
    o) OUTDIR="$OPTARG" ;;
    i) INTERVAL="$OPTARG" ;;
    d) DURATION="$OPTARG" ;;
    p) PID="$OPTARG" ;;
    *) echo "usage: $0 [-o outdir] [-i interval_sec] [-d duration_sec] [-p pid]" >&2; exit 2 ;;
  esac
done

if [[ -z "$PID" ]]; then
  PID=$(pgrep -f "bootstrap.http4s.Http4sServer" | head -1)
fi
if [[ -z "$PID" ]]; then
  echo "ERROR: no OBP-API process found (looked for bootstrap.http4s.Http4sServer). Pass -p <pid>." >&2
  exit 1
fi
if ! command -v jcmd >/dev/null 2>&1; then
  echo "ERROR: jcmd not on PATH — need a JDK, not just a JRE." >&2
  exit 1
fi

mkdir -p "$OUTDIR"
SUMMARY="${OUTDIR}/summary.csv"
echo "ts,elapsed_s,total_threads,threads_in_await,threads_blocked,heap_used_mb" > "$SUMMARY"

echo "snapshotting pid ${PID} every ${INTERVAL}s for ${DURATION}s -> ${OUTDIR}/"

START=$(date +%s)
END=$((START + DURATION))
N=0
while [[ $(date +%s) -lt $END ]]; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START))
  N=$((N + 1))
  STAMP=$(printf "%03d" "$N")

  TD="${OUTDIR}/threads-${STAMP}.txt"
  HEAP="${OUTDIR}/heap-${STAMP}.txt"
  jcmd "$PID" Thread.print > "$TD" 2>&1
  jcmd "$PID" GC.heap_info > "$HEAP" 2>&1

  TOTAL=$(grep -c '^"' "$TD" 2>/dev/null || echo 0)
  # Threads parked inside scala.concurrent Await / ThreadPoolImpl blocking.
  AWAIT=$(grep -cE 'scala\.concurrent\.(Await|impl\.Promise.*(tryAwait|ready|result))|CompletableFuture\.waitingGet' "$TD" 2>/dev/null || echo 0)
  BLOCKED=$(grep -c 'java.lang.Thread.State: BLOCKED' "$TD" 2>/dev/null || echo 0)
  HEAP_MB=$(grep -oE 'used [0-9]+K' "$HEAP" 2>/dev/null | head -1 | grep -oE '[0-9]+' | awk '{printf "%.1f", $1/1024}')
  HEAP_MB="${HEAP_MB:--1}"

  echo "$(date -Iseconds),${ELAPSED},${TOTAL},${AWAIT},${BLOCKED},${HEAP_MB}" >> "$SUMMARY"
  echo "  t=${ELAPSED}s threads=${TOTAL} await=${AWAIT} blocked=${BLOCKED} heap=${HEAP_MB}MB"

  sleep "$INTERVAL"
done

echo "done — ${SUMMARY}"
