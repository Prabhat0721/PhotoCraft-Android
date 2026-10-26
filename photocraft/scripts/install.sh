#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MILESTONE="${1:-}"

if [ -z "$MILESTONE" ]; then
    printf 'Usage: %s <milestone> (for example: M1)\n' "$0" >&2
    exit 2
fi

if ! [[ "$MILESTONE" =~ ^M[0-9]+$ ]]; then
    printf 'Invalid milestone: %s\n' "$MILESTONE" >&2
    printf 'Expected a value such as M0 or M1.\n' >&2
    exit 2
fi

APK="$ROOT/build/PhotoCraft-$MILESTONE.apk"

if ! command -v termux-open >/dev/null 2>&1; then
    printf 'Missing required tool: termux-open\n' >&2
    exit 1
fi

if [ ! -f "$APK" ]; then
    printf 'Missing signed APK: %s\n' "$APK" >&2
    exit 1
fi

termux-open "$APK"
