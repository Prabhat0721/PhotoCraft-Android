#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_DIR="$ROOT/keystore"
KEYSTORE="$KEYSTORE_DIR/photocraft-debug.keystore"

if ! command -v keytool >/dev/null 2>&1; then
    printf 'Missing required tool: keytool\n' >&2
    exit 1
fi

if [ -e "$KEYSTORE" ]; then
    printf 'Refusing to overwrite: %s\n' "$KEYSTORE" >&2
    exit 1
fi

umask 077
mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"

keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias photocraft \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=PhotoCraft, OU=Mobile, O=PhotoCraft, L=Local, ST=Local, C=US"

chmod 600 "$KEYSTORE"
printf 'Keystore created: %s\n' "$KEYSTORE"
