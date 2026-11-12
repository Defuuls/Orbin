#!/usr/bin/env bash
# adbd can close its transport while switching to root. Proceed only after observing UID 0.
set -u

limit="${1:-60}"
if [ "$#" -gt 1 ] || ! [[ "$limit" =~ ^[1-9][0-9]*$ ]]; then
  echo 'Usage: ensure_rooted_emulator.sh [positive integer timeout seconds]' >&2
  exit 2
fi

# One outer deadline covers every adb call, including a hung root/reconnect/UID command.
# Signal the whole timeout process group so no blocked adb child survives the deadline.
if timeout --signal=KILL "${limit}s" bash -c '
  adb root || :
  adb wait-for-device >/dev/null 2>&1 || :
  while true; do
    if uid="$(adb shell id -u 2>/dev/null)"; then
      uid="${uid//$'"'"'\r'"'"'/}"
      if [ "$uid" = 0 ]; then
        echo "Confirmed rooted emulator UID 0."
        exit 0
      fi
    fi
    sleep 0.1
  done
'; then
  exit 0
fi

echo "Rooted emulator UID 0 was not confirmed within ${limit}s; refusing to continue." >&2
exit 1
