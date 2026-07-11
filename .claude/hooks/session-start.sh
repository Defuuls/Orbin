#!/bin/bash
# SessionStart hook: provision the Android build toolchain for Claude Code on the web.
#
# The web sandbox ships a JDK but not the Android SDK, and the Gradle distribution host
# (services.gradle.org -> GitHub releases) is blocked by the egress policy, so `./gradlew`
# cannot self-bootstrap. This script installs the Android SDK and seeds the Gradle wrapper
# cache from an allowed mirror so detekt/ktlint/tests/assemble all run locally.
#
# Idempotent: every step is skipped when its output already exists, so re-running (resume,
# clear, compact) is cheap. Remote-only: does nothing on a local machine.
set -euo pipefail

# Only provision the managed web sandbox; a developer's own machine is left untouched.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

log() { echo "[session-start] $*" >&2; }

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
GRADLE_MIRROR_BASE="https://mirrors.cloud.tencent.com/gradle"

# ---------------------------------------------------------------------------
# 1. Android SDK
# ---------------------------------------------------------------------------
# Match the SDK level the build compiles against so version bumps need no hook change.
COMPILE_SDK="$(grep -E '^compileSdk\s*=' "$PROJECT_DIR/gradle/libs.versions.toml" | head -1 | sed -E 's/.*"([0-9]+)".*/\1/')"
COMPILE_SDK="${COMPILE_SDK:-36}"
BUILD_TOOLS="${COMPILE_SDK}.0.0"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  log "installing Android command-line tools into $SDK_DIR"
  mkdir -p "$SDK_DIR/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL --retry 3 --max-time 300 -o "$tmp_zip" "$CMDLINE_TOOLS_URL"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  unzip -q "$tmp_zip" -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f "$tmp_zip"
fi

if [ ! -d "$SDK_DIR/platforms/android-$COMPILE_SDK" ]; then
  log "accepting licenses and installing platform-tools, android-$COMPILE_SDK, build-tools $BUILD_TOOLS"
  yes 2>/dev/null | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" "platform-tools" "platforms;android-$COMPILE_SDK" "build-tools;$BUILD_TOOLS" >/dev/null
else
  log "Android SDK already present ($SDK_DIR)"
fi

# Point both the build (local.properties, gitignored) and the session env at the SDK.
echo "sdk.dir=$SDK_DIR" > "$PROJECT_DIR/local.properties"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$SDK_DIR\""
    echo "export ANDROID_SDK_ROOT=\"$SDK_DIR\""
  } >> "$CLAUDE_ENV_FILE"
fi

# ---------------------------------------------------------------------------
# 2. Gradle distribution (seed the wrapper cache from a reachable mirror)
# ---------------------------------------------------------------------------
DIST_URL="$(grep '^distributionUrl=' "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.properties" | cut -d= -f2- | sed 's/\\:/:/g')"
GRADLE_ZIP="${DIST_URL##*/}"                 # e.g. gradle-9.4.1-bin.zip
DIST_NAME="${GRADLE_ZIP%.zip}"               # e.g. gradle-9.4.1-bin
GRADLE_DIR_NAME="${DIST_NAME%-bin}"          # e.g. gradle-9.4.1
GRADLE_DIR_NAME="${GRADLE_DIR_NAME%-all}"

# The wrapper stores each distribution under a dir named base36(md5(distributionUrl)).
HASH="$(python3 - "$DIST_URL" <<'PY'
import hashlib, sys
n = int.from_bytes(hashlib.md5(sys.argv[1].encode()).digest(), "big")
a = "0123456789abcdefghijklmnopqrstuvwxyz"
s = ""
while n:
    n, r = divmod(n, 36)
    s = a[r] + s
print(s or "0")
PY
)"

DEST="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/$DIST_NAME/$HASH"
if [ ! -x "$DEST/$GRADLE_DIR_NAME/bin/gradle" ]; then
  log "seeding Gradle wrapper cache ($DIST_NAME) from mirror"
  mkdir -p "$DEST"
  rm -f "$DEST/$GRADLE_ZIP" "$DEST/$GRADLE_ZIP.part" "$DEST/$GRADLE_ZIP.lck"
  curl -fsSL --retry 3 --max-time 300 -o "$DEST/$GRADLE_ZIP" "$GRADLE_MIRROR_BASE/$GRADLE_ZIP"
  (cd "$DEST" && unzip -q "$GRADLE_ZIP")
  touch "$DEST/$GRADLE_ZIP.ok"   # marker the wrapper checks to skip re-downloading
else
  log "Gradle distribution already cached ($DIST_NAME)"
fi

log "toolchain ready: Android SDK + Gradle $GRADLE_DIR_NAME"
