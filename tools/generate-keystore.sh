#!/usr/bin/env bash
#
# Generate a local self-signed keystore so that a signed, installable release
# APK can be produced without any network access or external key material.
#
# This key is for LOCAL BUILDS AND TESTING ONLY. It is not a distribution key.
# Replace it with your own upload key before publishing anywhere.
#
# The keystore/ directory is git-ignored; the key never enters the repository.
#
# NOTE: do not regenerate a key that has already signed a build someone
# installed. Android refuses to upgrade an app whose signing key changed, so a
# new key turns every future build into a manual-uninstall-first situation.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_DIR="$ROOT/keystore"
KEYSTORE="$KEYSTORE_DIR/cyopstd.jks"

# These match the signingConfig in app/build.gradle.kts.
STORE_PASS="cyopstd"
KEY_PASS="cyopstd"
ALIAS="cyopstd"

if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists: $KEYSTORE"
    echo "Delete it first if you want to regenerate."
    exit 0
fi

mkdir -p "$KEYSTORE_DIR"

keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=CyOps TD, OU=Development, O=CyOps TD, L=, ST=, C=US"

echo
echo "Created $KEYSTORE"
echo "You can now run: ./gradlew assembleRelease"
