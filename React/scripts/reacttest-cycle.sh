#!/usr/bin/env bash
# reacttest-cycle.sh [platforms...]   (default: purpur spigot paper)
# Cycles a single 'reacttest' multiplexor instance across platforms at MC 26.1.2,
# runs /react test run + /react test loadtest, collects the JSON reports, then
# deletes and rebuilds the instance for the next platform. File-based verification
# only (never scrapes the console). Env overrides: PLAYERS, DURATION, RESULTS_DIR, BOT_SMOKE.
set -uo pipefail

MPX="/Users/brianfopiano/Developer/RemoteGit/[Minecraft Server]"
REACT_JAR="/Users/brianfopiano/Developer/RemoteGit/VolmitSoftware/React/React/build/libs/React-2.0.3-26.2.jar"
SMOKE="/Users/brianfopiano/Developer/react-sim/smoke-react.sh"
INSTANCE="reacttest"
MC="26.2"
PORT="25599"
PLAYERS="${PLAYERS:-1000}"
DURATION="${DURATION:-120}"
RESULTS_DIR="${RESULTS_DIR:-/tmp/reacttest-results}"
BOT_SMOKE="${BOT_SMOKE:-1}"
RUNTIME_LOG_DIR="$MPX/consumers/plugin-consumers/state/runtime"

PLATFORMS=("$@")
[ ${#PLATFORMS[@]} -eq 0 ] && PLATFORMS=(purpur spigot paper)

mkdir -p "$RESULTS_DIR"
LOG="$RESULTS_DIR/cycle.log"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }
mpx() { ( cd "$MPX" && ./start.sh "$@" ); }

poll_grep() { # <file> <ERE-pattern> <timeout_s>
  local f="$1" p="$2" t="$3" i=0
  while [ "$i" -lt "$t" ]; do
    [ -e "$f" ] && grep -Eq "$p" "$f" 2>/dev/null && return 0
    sleep 2; i=$((i + 2))
  done
  return 1
}
poll_glob() { # <dir> <glob> <timeout_s>
  local d="$1" g="$2" t="$3" i=0
  while [ "$i" -lt "$t" ]; do
    ls "$d"/$g >/dev/null 2>&1 && return 0
    sleep 2; i=$((i + 2))
  done
  return 1
}
wait_ready() { # <rlog> <react_dir> <timeout_s>  echoes signal on stdout
  local rlog="$1" rdir="$2" t="$3" i=0
  while [ "$i" -lt "$t" ]; do
    if [ -e "$rlog" ] && grep -Eq 'For help, type|Done \(' "$rlog" 2>/dev/null; then echo log; return 0; fi
    if [ "$i" -ge 90 ] && [ -d "$rdir" ]; then echo react-folder; return 0; fi
    sleep 2; i=$((i + 2))
  done
  return 1
}

for PLATFORM in "${PLATFORMS[@]}"; do
  log "================= PLATFORM: $PLATFORM ================="
  mpx consumer use plugin >>"$LOG" 2>&1
  mpx instance delete "$INSTANCE" >>"$LOG" 2>&1 || true
  if ! mpx server create "$INSTANCE" --type "$PLATFORM" --mc "$MC" --isolated >>"$LOG" 2>&1; then
    log "create failed for $PLATFORM; skipping"; continue
  fi

  IPATH="$(mpx instance path "$INSTANCE" 2>/dev/null | tail -1 | tr -d '\r')"
  if [ -z "$IPATH" ] || [ ! -d "$IPATH" ]; then
    log "bad instance path '$IPATH'; skipping $PLATFORM"; continue
  fi
  log "instance path: $IPATH"

  mkdir -p "$IPATH/plugins"
  cp "$REACT_JAR" "$IPATH/plugins/React.jar"
  printf 'eula=true\n' >"$IPATH/eula.txt"
  SP="$IPATH/server.properties"
  touch "$SP"
  if grep -q '^online-mode=' "$SP" 2>/dev/null; then
    sed -i '' -E "s/^online-mode=.*/online-mode=false/" "$SP" 2>/dev/null || true
  else
    printf 'online-mode=false\n' >>"$SP"
  fi

  RLOG="$RUNTIME_LOG_DIR/$INSTANCE.log"
  rm -f "$RLOG" 2>/dev/null || true
  mpx runtime start "$INSTANCE" --no-console >>"$LOG" 2>&1

  log "waiting for server ready..."
  READY_SIG="$(wait_ready "$RLOG" "$IPATH/plugins/React" 300)"
  if [ -z "$READY_SIG" ]; then
    log "server $PLATFORM did not become ready in 300s; tail:"; tail -25 "$RLOG" >>"$LOG" 2>&1
    mpx runtime stop "$INSTANCE" >>"$LOG" 2>&1 || true
    mpx instance delete "$INSTANCE" >>"$LOG" 2>&1 || true
    continue
  fi
  if [ "$READY_SIG" = "react-folder" ]; then
    log "ready via React data folder (console log frozen - likely Spigot); settling 30s"
    sleep 30
  fi

  SESS="$(tmux ls 2>/dev/null | grep -i "$INSTANCE" | head -1 | cut -d: -f1)"
  if [ -z "$SESS" ]; then
    log "no tmux session found for $INSTANCE; skipping $PLATFORM"
    mpx runtime stop "$INSTANCE" >>"$LOG" 2>&1 || true
    mpx instance delete "$INSTANCE" >>"$LOG" 2>&1 || true
    continue
  fi
  log "ready. tmux session: $SESS"
  sleep 5
  ACTUAL_PORT="$(mpx instance port "$INSTANCE" 2>/dev/null | grep -oE '[0-9]{4,5}' | tail -1)"
  [ -z "$ACTUAL_PORT" ] && ACTUAL_PORT="$PORT"
  log "server port: $ACTUAL_PORT"

  REPDIR="$IPATH/plugins/React/test-reports"
  mkdir -p "$REPDIR"

  log "/react test run ..."
  rm -f "$REPDIR"/*selftest.json 2>/dev/null || true
  tmux send-keys -t "$SESS" 'react test run full=true json=true' Enter
  poll_glob "$REPDIR" '*selftest.json' 200 && log "selftest report OK" || log "selftest report TIMEOUT"

  log "/react test loadtest players=$PLAYERS duration=$DURATION ..."
  rm -f "$REPDIR"/*loadtest.json 2>/dev/null || true
  tmux send-keys -t "$SESS" "react test loadtest true players=$PLAYERS duration=$DURATION" Enter
  LT_TIMEOUT=$((DURATION * 2 + 300))
  poll_glob "$REPDIR" '*loadtest.json' "$LT_TIMEOUT" && log "loadtest report OK" || log "loadtest report TIMEOUT"

  if [ "$BOT_SMOKE" = "1" ]; then
    log "bot smoke ..."
    "$SMOKE" 127.0.0.1 "$ACTUAL_PORT" 20 30 1.21.11 >"$RESULTS_DIR/$PLATFORM-botsmoke.json" 2>>"$LOG" || true
    log "bot smoke: $(cat "$RESULTS_DIR/$PLATFORM-botsmoke.json" 2>/dev/null)"
  fi

  mkdir -p "$RESULTS_DIR/$PLATFORM"
  cp "$REPDIR"/*.json "$RESULTS_DIR/$PLATFORM/" 2>/dev/null || true
  log "collected $(ls "$RESULTS_DIR/$PLATFORM"/*.json 2>/dev/null | wc -l | tr -d ' ') report(s) for $PLATFORM"

  mpx runtime stop "$INSTANCE" >>"$LOG" 2>&1 || true
  sleep 6
  mpx instance delete "$INSTANCE" >>"$LOG" 2>&1 || true
  log "================= $PLATFORM complete ================="
done

log "CYCLE COMPLETE. Results in $RESULTS_DIR"
