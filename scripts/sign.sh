#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MILESTONE="${1:-}"
EXPECTED_M5_1_UNSIGNED_SHA256="31f01bb898d497db9c138183ddbb40de4f8b1b2a0a15e8c5a1d9e70942e5ea03"
EXPECTED_M5_2_UNSIGNED_SHA256="01c97c0abdc0dfe300435ad639e603310a19dc7f4e6e52989460682fb0bd25ff"

if [ -z "$MILESTONE" ]; then
    printf 'Usage: %s <milestone> (for example: M1, M5.1, or M5.2)\n' "$0" >&2
    exit 2
fi

if ! [[ "$MILESTONE" =~ ^(M[0-9]+|M5\.1|M5\.2|M5\.5)$ ]]; then
    printf 'Invalid milestone: %s\n' "$MILESTONE" >&2
    printf 'Expected a value such as M0, M1, M5.1, or M5.2.\n' >&2
    exit 2
fi

UNSIGNED="$ROOT/build/PhotoCraft-$MILESTONE-unsigned.apk"
SIGNED="$ROOT/build/PhotoCraft-$MILESTONE.apk"
SIGNED_IDSIG="$SIGNED.idsig"
KEYSTORE="$ROOT/keystore/photocraft-debug.keystore"

for tool in apksigner zipalign mktemp mv; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Missing required tool: %s\n' "$tool" >&2
        exit 1
    fi
done

if [ ! -f "$UNSIGNED" ]; then
    printf 'Missing unsigned APK: %s\n' "$UNSIGNED" >&2
    exit 1
fi

if [ "$MILESTONE" = "M5.1" ]; then
    if ! command -v sha256sum >/dev/null 2>&1; then
        printf 'Missing required tool for M5.1: sha256sum\n' >&2
        exit 1
    fi
    actual_unsigned_sha256="$(sha256sum "$UNSIGNED")"
    actual_unsigned_sha256="${actual_unsigned_sha256%% *}"
    if [ "$actual_unsigned_sha256" != "$EXPECTED_M5_1_UNSIGNED_SHA256" ]; then
        printf 'Unsigned APK SHA-256 mismatch: %s\n' "$UNSIGNED" >&2
        printf 'expected: %s\n' "$EXPECTED_M5_1_UNSIGNED_SHA256" >&2
        printf 'actual:   %s\n' "$actual_unsigned_sha256" >&2
        exit 1
    fi
fi

if [ "$MILESTONE" = "M5.2" ]; then
    if ! command -v sha256sum >/dev/null 2>&1; then
        printf 'Missing required tool for M5.2: sha256sum\n' >&2
        exit 1
    fi
    actual_unsigned_sha256="$(sha256sum "$UNSIGNED")"
    actual_unsigned_sha256="${actual_unsigned_sha256%% *}"
    if [ "$actual_unsigned_sha256" != "$EXPECTED_M5_2_UNSIGNED_SHA256" ]; then
        printf 'Unsigned APK SHA-256 mismatch: %s\n' "$UNSIGNED" >&2
        printf 'expected: %s\n' "$EXPECTED_M5_2_UNSIGNED_SHA256" >&2
        printf 'actual:   %s\n' "$actual_unsigned_sha256" >&2
        exit 1
    fi
fi

if [ ! -f "$KEYSTORE" ]; then
    printf 'Missing keystore: %s\n' "$KEYSTORE" >&2
    printf 'Run scripts/init-keystore.sh first.\n' >&2
    exit 1
fi

if [ -e "$SIGNED" ] || [ -e "$SIGNED_IDSIG" ]; then
    printf 'Refusing to overwrite existing signed artifact: %s\n' "$SIGNED" >&2
    exit 1
fi

printf 'Verifying unsigned APK alignment...\n'
if ! zipalign -c -v 4 "$UNSIGNED" >/dev/null; then
    printf 'Unsigned APK is not 4-byte aligned: %s\n' "$UNSIGNED" >&2
    exit 1
fi

mkdir -p "$ROOT/build"
WORK="$(mktemp -d "$ROOT/build/sign-$MILESTONE.XXXXXXXX")"
TEMP_SIGNED="$WORK/PhotoCraft-$MILESTONE.apk"
TEMP_IDSIG="$TEMP_SIGNED.idsig"
printf 'Signing in isolated work directory: %s\n' "$WORK"

if ! apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias photocraft \
    --in "$UNSIGNED" \
    --out "$TEMP_SIGNED"; then
    printf 'Signing failed; no signed artifact was published.\n' >&2
    exit 1
fi

if ! apksigner verify --verbose "$TEMP_SIGNED"; then
    printf 'Signature verification failed; no signed artifact was published.\n' >&2
    exit 1
fi

printf 'Verifying signed APK alignment...\n'
if ! zipalign -c -v 4 "$TEMP_SIGNED" >/dev/null; then
    printf 'Signed APK is not 4-byte aligned: %s\n' "$TEMP_SIGNED" >&2
    exit 1
fi

if [ -e "$TEMP_IDSIG" ]; then
    mv "$TEMP_IDSIG" "$SIGNED_IDSIG"
fi
mv "$TEMP_SIGNED" "$SIGNED"

printf 'Signed APK: %s\n' "$SIGNED"
