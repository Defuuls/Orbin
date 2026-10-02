#!/usr/bin/env bash
# Drive the real Orbin Android app headlessly (Robolectric + Roborazzi), from the repo root.
#   .claude/skills/run-orbin/driver.sh 'wait:/an/;shot:boards;tree'   (steps: see SKILL.md)
# Env: OUT=<dir> (default build/orbin-drive), DARK=true for night mode.
set -euo pipefail
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SKILL=.claude/skills/run-orbin
STEPS="${1:-idle:2000;shot:launch;tree}"
OUT="${OUT:-$PWD/build/orbin-drive}"
mkdir -p "$OUT"
# Robolectric fetches its Android runtime jar itself (not via Gradle) and gives up on Maven
# Central's 429s. Seed ~/.m2, which it checks first; curl retries the 429s.
RV=15-robolectric-13954326-i7   # android-all for sdk 35 under robolectric 4.17
RD=~/.m2/repository/org/robolectric/android-all-instrumented/$RV
mkdir -p "$RD"
for f in jar jar.sha512 pom pom.sha512; do
  dest="$RD/android-all-instrumented-$RV.$f"
  [ -s "$dest" ] && continue
  echo "[driver] seeding Robolectric runtime: $(basename "$dest")"
  curl -fsS --retry 20 --retry-all-errors --retry-delay 10 --retry-max-time 900 -o "$dest.part" \
    "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/$RV/android-all-instrumented-$RV.$f" \
    && mv "$dest.part" "$dest" \
    || { echo "[driver] could not download $(basename "$dest") (Maven Central 429s?) - rerun in a minute" >&2; exit 1; }
done
RESULT=app/build/test-results/testDebugUnitTest/TEST-com.orbin.app.drive.DriveOrbin.xml
for attempt in 1 2 3 4; do
  rm -f "$RESULT" "$OUT"/*.png
  if ./gradlew --max-workers=2 -q -I "$SKILL/drive.init.gradle" \
      :app:testDebugUnitTest --tests 'com.orbin.app.drive.DriveOrbin' \
      -Porbin.steps="$STEPS" -Porbin.out="$OUT" -Porbin.dark="${DARK:-false}" \
      > "$OUT/drive.log" 2>&1; then status=0; else status=$?; fi
  [ -f "$RESULT" ] && cat "$RESULT" >> "$OUT/drive.log"
  # Steps, the semantics tree, and the first failure lines (the whole log is in $OUT/drive.log).
  grep -E '^\[drive\]|^ *\|?-?Node #|Text = |ContentDescription = |Actions = ' "$OUT/drive.log" | grep -v '^\s*at ' || true
  grep -E '^e: |Exception|Caused by|What went wrong' "$OUT/drive.log" | grep -v '^\s*at ' | head -8 || true
  # Maven Central rate-limits cold dependency downloads (HTTP 429); those are worth a retry.
  if [ "$status" -eq 0 ] || ! grep -qE 'code:? 429' "$OUT/drive.log"; then break; fi
  echo "[driver] 429 from Maven Central, retrying in 30s ($attempt)"; sleep 30
done
ls -1 "$OUT"/*.png 2>/dev/null || true
exit "$status"
