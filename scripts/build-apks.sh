#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  echo "Set ANDROID_SDK_ROOT to an installed Android SDK." >&2
  exit 1
fi
API="${ANDROID_API:-36}"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-36.0.0}"
ANDROID_JAR="$SDK_ROOT/platforms/android-$API/android.jar"
TOOLS="$SDK_ROOT/build-tools/$BUILD_TOOLS"
if [[ ! -f "$ANDROID_JAR" || ! -x "$TOOLS/aapt2" ]]; then
  echo "Install Android platform $API and build-tools $BUILD_TOOLS in $SDK_ROOT." >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build-local"
mkdir -p "$OUT/keystore" "$ROOT/artifacts"
KEYSTORE="$OUT/keystore/debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

build_module() {
  local module="$1" package="$2" version_code="$3" version_name="$4"
  local work="$OUT/$module" src="$ROOT/$module/src/main"
  rm -rf "$work"
  mkdir -p "$work/generated" "$work/classes" "$work/dex"
  "$TOOLS/aapt2" compile --dir "$src/res" -o "$work/resources.zip"
  "$TOOLS/aapt2" link -o "$work/resources.ap_" \
    --manifest "$src/AndroidManifest.xml" \
    --java "$work/generated" -I "$ANDROID_JAR" \
    --rename-manifest-package "$package" \
    --min-sdk-version 21 --target-sdk-version 36 \
    --version-code "$version_code" --version-name "$version_name" \
    --auto-add-overlay -R "$work/resources.zip"
  mapfile -d '' sources < <(find "$src/java" "$work/generated" -name '*.java' -print0)
  javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" \
    -d "$work/classes" "${sources[@]}"
  mapfile -d '' classes < <(find "$work/classes" -name '*.class' -print0)
  "$TOOLS/d8" --min-api 21 --lib "$ANDROID_JAR" --output "$work/dex" "${classes[@]}"
  cp "$work/resources.ap_" "$work/unsigned.apk"
  (cd "$work/dex" && zip -q "$work/unsigned.apk" classes.dex)
  "$TOOLS/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"
  "$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out "$ROOT/artifacts/${module}-debug.apk" "$work/aligned.apk"
  "$TOOLS/apksigner" verify --verbose "$ROOT/artifacts/${module}-debug.apk"
}

build_module Seismo com.ariwilson.seismo 12 2.0
build_module SeismoWallpaper com.ariwilson.seismowallpaper 4 2.0
echo "Built APKs in $ROOT/artifacts/"
