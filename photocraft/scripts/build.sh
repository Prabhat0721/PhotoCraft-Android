#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app/src/main"
BUILD="$ROOT/build"
ANDROID_JAR="$ROOT/tools/android.jar"
ANDROID_JAR_SHA256="d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a"
RES_DIR="$APP/res"
KOTLIN_DIR="$APP/kotlin"
MANIFEST="$APP/AndroidManifest.xml"
MILESTONE="${1:-}"

if [ -z "$MILESTONE" ]; then
    printf 'Usage: %s <milestone> (for example: M1 or M5.1)\n' "$0" >&2
    exit 2
fi

if ! [[ "$MILESTONE" =~ ^M[0-9]+(\.[0-9]+)?$ ]]; then
    printf 'Invalid milestone: %s\n' "$MILESTONE" >&2
    printf 'Expected a value such as M0, M1, or M5.1.\n' >&2
    exit 2
fi

UNSIGNED_APK="$BUILD/PhotoCraft-$MILESTONE-unsigned.apk"

for tool in aapt2 javac jar kotlinc d8 zip zipalign sha256sum grep mktemp mv; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Missing required tool: %s\n' "$tool" >&2
        exit 1
    fi
done

if [ ! -f "$ANDROID_JAR" ]; then
    printf 'Missing Android platform jar: %s\n' "$ANDROID_JAR" >&2
    exit 1
fi

actual_android_jar_sha256="$(sha256sum "$ANDROID_JAR")"
actual_android_jar_sha256="${actual_android_jar_sha256%% *}"
if [ "$actual_android_jar_sha256" != "$ANDROID_JAR_SHA256" ]; then
    printf 'Android platform jar checksum mismatch: %s\n' "$ANDROID_JAR" >&2
    printf 'expected: %s\n' "$ANDROID_JAR_SHA256" >&2
    printf 'actual:   %s\n' "$actual_android_jar_sha256" >&2
    exit 1
fi

if [ -e "$UNSIGNED_APK" ]; then
    printf 'Refusing to overwrite existing output: %s\n' "$UNSIGNED_APK" >&2
    printf 'Choose a different milestone or move the existing file intentionally.\n' >&2
    exit 1
fi

mkdir -p "$BUILD"
WORK="$(mktemp -d "$BUILD/work-$MILESTONE.XXXXXXXX")"
UNALIGNED_APK="$WORK/PhotoCraft-$MILESTONE-unaligned.apk"
mkdir -p "$WORK/gen" "$WORK/classes" "$WORK/dex"
printf 'Milestone: %s\n' "$MILESTONE"
printf 'Isolated work directory: %s\n' "$WORK"

printf 'Compiling resources...\n'
aapt2 compile --dir "$RES_DIR" -o "$WORK/res.zip"

printf 'Linking resources...\n'
aapt2 link \
    -o "$UNALIGNED_APK" \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    --java "$WORK/gen" \
    "$WORK/res.zip"

printf 'Compiling generated R.java...\n'
javac -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$WORK/classes" \
    "$WORK/gen/com/prabhat/photocraft/R.java"

printf 'Packaging R classes...\n'
jar cf "$WORK/r.jar" -C "$WORK/classes" .

printf 'Compiling Kotlin...\n'
JAVA_OPTS='-Xms64m -Xmx256m' kotlinc "$KOTLIN_DIR" \
    -classpath "$ANDROID_JAR:$WORK/classes" \
    -jvm-target 1.8 \
    -include-runtime \
    -no-reflect \
    -d "$WORK/PhotoCraft.jar"

printf 'Dexing...\n'
JAVA_TOOL_OPTIONS='-Xms64m -Xmx256m' d8 \
    --lib "$ANDROID_JAR" \
    --min-api 29 \
    --output "$WORK/dex" \
    "$WORK/PhotoCraft.jar" \
    "$WORK/r.jar"

printf 'Packaging DEX files...\n'
shopt -s nullglob
DEX_FILES=("$WORK/dex/"classes*.dex)
if [ "${#DEX_FILES[@]}" -eq 0 ]; then
    printf 'D8 produced no classes*.dex files in %s\n' "$WORK/dex" >&2
    exit 1
fi
(
    cd "$WORK/dex"
    zip -q "$UNALIGNED_APK" "${DEX_FILES[@]##*/}"
)
if ! apk_entries="$(jar tf "$UNALIGNED_APK")"; then
    printf 'Failed to list APK entries: %s\n' "$UNALIGNED_APK" >&2
    exit 1
fi
for dex in "${DEX_FILES[@]}"; do
    if ! grep -Fqx -- "${dex##*/}" <<<"$apk_entries"; then
        printf 'Missing DEX entry in APK: %s\n' "${dex##*/}" >&2
        exit 1
    fi
done

printf 'Aligning APK...\n'
ALIGNED_TMP="$WORK/aligned.apk"
if ! zipalign -f 4 "$UNALIGNED_APK" "$ALIGNED_TMP"; then
    printf 'APK alignment failed; no aligned APK was published.\n' >&2
    exit 1
fi

printf 'Verifying alignment...\n'
if ! zipalign -c -v 4 "$ALIGNED_TMP" >/dev/null; then
    printf 'APK alignment verification failed; no aligned APK was published.\n' >&2
    exit 1
fi

mv "$ALIGNED_TMP" "$UNSIGNED_APK"

printf 'Unsigned aligned APK: %s\n' "$UNSIGNED_APK"
printf 'Next: scripts/sign.sh %s\n' "$MILESTONE"
