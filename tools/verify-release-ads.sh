#!/usr/bin/env bash
#
# Read the ad configuration back out of a BUILT release artifact.
#
# The build file says a release never carries a Google test ad id. This checks
# the thing that actually ships, because "the build file says so" and "the
# bundle contains so" are different claims and only the second one is submitted
# to Google.
#
# Usage:
#   tools/verify-release-ads.sh [path-to-.aab-or-.apk]
#
# Exit codes: 0 clean, 1 a test id is present, 2 could not inspect the file.
#
# NOTE ON METHOD. The first version of this script grepped the archive for
# "ca-app-pub-3940256099942544" and reported a clean bill of health on a bundle
# whose manifest demonstrably contained exactly that string. An .aab stores its
# manifest as deflated protobuf and its code as dex, so a raw byte scan over the
# zip sees neither. It was a check that could not fail, which is worse than no
# check because it reads as proof. Everything below decodes the artifact first.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$ROOT/app/build/outputs/bundle/release/app-release.aab}"

TEST_PUBLISHER="3940256099942544"
UNCONFIGURED="ca-app-pub-0000000000000000~0000000000"

if [ ! -f "$TARGET" ]; then
    echo "No artifact at: $TARGET"
    echo "Build one first:  ./gradlew bundleRelease"
    exit 2
fi

AAPT2="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$AAPT2" ]; then
    echo "Could not find aapt2 under the Android SDK build-tools."
    echo "Set ANDROID_HOME, or install build-tools via sdkmanager."
    exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Inspecting: $TARGET"

# --- 1. the application id, out of the real manifest -------------------------
#
# An APK's manifest is binary XML, which aapt2 decodes. An .aab's is protobuf
# and aapt2 refuses the container outright ("could not identify format of APK"),
# so it is extracted and read as text -- protobuf stores string fields
# uncompressed, so once the zip entry is inflated the id is plainly there.
case "$TARGET" in
    *.aab)
        unzip -qo "$TARGET" -d "$WORK" 'base/manifest/AndroidManifest.xml' 2>/dev/null || true
        APP_ID="$(strings -a "$WORK/base/manifest/AndroidManifest.xml" 2>/dev/null \
            | grep -o 'ca-app-pub-[0-9]*~[0-9]*' | head -1 || true)"
        ;;
    *)
        APP_ID="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$TARGET" 2>/dev/null \
            | grep -A 1 'com.google.android.gms.ads.APPLICATION_ID' \
            | grep -o 'ca-app-pub-[0-9]*~[0-9]*' \
            | head -1 || true)"
        ;;
esac

# --- 2. the ad unit ids, out of the dex --------------------------------------
unzip -qo "$TARGET" -d "$WORK" 'classes*.dex' 'base/dex/*.dex' 2>/dev/null || true
UNIT_IDS="$(find "$WORK" -name '*.dex' -exec strings -a {} + 2>/dev/null \
    | grep -o 'ca-app-pub-[0-9]*/[0-9]*' | sort -u || true)"

echo
echo "  application id : ${APP_ID:-(none in manifest)}"
if [ -n "$UNIT_IDS" ]; then
    echo "  ad unit ids    :"
    echo "$UNIT_IDS" | sed 's/^/    /'
else
    echo "  ad unit ids    : (none)"
fi
echo

# --- 3. the verdict ----------------------------------------------------------
FAILED=0

if printf '%s\n%s\n' "$APP_ID" "$UNIT_IDS" | grep -q "$TEST_PUBLISHER"; then
    echo "FAIL: this artifact contains a Google TEST ad id (publisher ${TEST_PUBLISHER})."
    echo
    echo "A release must never carry one: test creatives earn nothing and"
    echo "serving them to real users breaks AdMob policy."
    echo
    echo "Most likely cause: the release was built without"
    echo "  -Pcyops.admob.appId / -Pcyops.admob.interstitialId / -Pcyops.admob.rewardedId"
    echo "See ADMOB_SETUP.md."
    FAILED=1
fi

if [ "$FAILED" -eq 0 ]; then
    if [ "$APP_ID" = "$UNCONFIGURED" ]; then
        echo "OK, with a warning: no AdMob ids were supplied to this build."
        echo
        echo "The application id is the explicit placeholder, so nothing can"
        echo "serve an ad. This artifact is publishable and shows no ads and no"
        echo "ad revive. That is a valid release; it is just not a monetised one."
    elif [ -n "$APP_ID" ]; then
        echo "OK: production AdMob ids present, no Google test ids."
    else
        echo "OK, with a warning: no AdMob application id in the manifest at all."
    fi
fi

exit "$FAILED"
