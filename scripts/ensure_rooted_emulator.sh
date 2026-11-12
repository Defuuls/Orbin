#!/usr/bin/env bash
# adbd can close its transport while switching to root. Proceed only after observing UID 0.
set -u

limit="${1:-60}"
if [ "$#" -gt 1 ] || ! [[ "$limit" =~ ^[1-9][0-9]*$ ]]; then
  echo 'Usage: ensure_rooted_emulator.sh [positive integer timeout seconds]' >&2
  exit 2
fi

# Own the worker session explicitly: timeout utilities differ in descendant cleanup, and a
# surviving adb process can hold the caller's output pipes after its immediate shell has died.
exec python3 - "$limit" <<'PY'
import os
import signal
import subprocess
import sys

body = r'''
set +m
adb root || :
adb wait-for-device >/dev/null 2>&1 || :
while true; do
  if uid="$(adb shell id -u 2>/dev/null)"; then
    uid="${uid//$'\r'/}"
    if [ "$uid" = 0 ]; then
      echo "Confirmed rooted emulator UID 0."
      exit 0
    fi
  fi
  sleep 0.1
done
'''

limit = int(sys.argv[1])
worker = subprocess.Popen(["bash", "-c", body], start_new_session=True)
try:
    try:
        status = worker.wait(timeout=limit)
    except subprocess.TimeoutExpired:
        status = 1
finally:
    # Kill the entire owned group even after an early exit: orphaned descendants must not
    # retain captured stdout/stderr. The caller, Python supervisor and adb server are outside it.
    try:
        os.killpg(worker.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    worker.wait()

if status != 0:
    print(f"Rooted emulator UID 0 was not confirmed within {limit}s; refusing to continue.", file=sys.stderr)
sys.exit(0 if status == 0 else 1)
PY
