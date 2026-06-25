#!/usr/bin/env bash
# MnnLlmChat Build Script
# Usage: ./build.sh [debug|release] [standard|googleplay] [--skip-native]
#   (default: release standard, native built first if missing)
#
# Examples:
#   ./build.sh                          # Build release, standard flavor
#   ./build.sh debug                    # Build debug, standard flavor
#   ./build.sh release googleplay       # Build release, googleplay flavor
#   ./build.sh debug standard --skip-native  # Skip MNN native build, only build APK

set -e

# Re-enable MSYS auto path conversion (/d/... → D:\...) for native Windows
# tools (java/apksigner/zipalign). Some environments (e.g. WorkBuddy's bash)
# inject MSYS_NO_PATHCONV=1 / MSYS2_ARG_CONV_EXCL=* which disable conversion,
# causing apksigner to receive MSYS-style paths and throw IOException.
unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL

BUILD_TYPE="${1:-release}"
FLAVOR="${2:-standard}"
SKIP_NATIVE=false

# Parse optional flags
for arg in "$@"; do
    case "$arg" in
        --skip-native) SKIP_NATIVE=true ;;
    esac
done

# Validate BUILD_TYPE
case "$BUILD_TYPE" in
    debug|release) ;;
    *) echo "Usage: $0 [debug|release] [standard|googleplay] [--skip-native]"; exit 1 ;;
esac

# Validate FLAVOR
case "$FLAVOR" in
    standard|googleplay) ;;
    *) echo "Usage: $0 [debug|release] [standard|googleplay] [--skip-native]"; exit 1 ;;
esac

# Paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$SCRIPT_DIR/app"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
NATIVE_BUILD_DIR="$REPO_ROOT/project/android/build_64"
BUILD_TOOLS="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?ERROR: set ANDROID_HOME or ANDROID_SDK_ROOT}}/build-tools/36.0.0"
KEYSTORE="${KEY_STORE_LOCATION:-}"
KEYSTORE_PASS="${KEY_STORE_PASSWORD:-}"
KEY_ALIAS="${KEY_ALIAS:-}"

# Normalize all paths to Windows-style (D:/...) via cygpath -m when available.
# This avoids MSYS-style /d/... paths being passed to java.exe/apksigner,
# which throws IOException on getCanonicalPath() in some environments.
if command -v cygpath >/dev/null 2>&1; then
    SCRIPT_DIR="$(cygpath -m "$SCRIPT_DIR")"
    APP_DIR="$(cygpath -m "$APP_DIR")"
    REPO_ROOT="$(cygpath -m "$REPO_ROOT")"
    NATIVE_BUILD_DIR="$(cygpath -m "$NATIVE_BUILD_DIR")"
    BUILD_TOOLS="$(cygpath -m "$BUILD_TOOLS")"
    [[ -n "$KEYSTORE" ]] && KEYSTORE="$(cygpath -m "$KEYSTORE")"
fi

# Auto-detect version from build.gradle
VERSION=""
GRADLE_FILE="$APP_DIR/build.gradle"
if [[ -f "$GRADLE_FILE" ]]; then
    # Match only the top-level versionName (8-space indent in defaultConfig),
    # not versionNameSuffix. Grab the content inside the first double-quoted pair.
    VERSION=$(grep -E '^[[:space:]]+versionName[[:space:]]+"' "$GRADLE_FILE" \
        | head -1 \
        | sed -E 's/.*versionName[[:space:]]+"([^"]+)".*/\1/')
fi
if [[ -z "$VERSION" ]]; then
    VERSION="0.8.3"
fi
VERSION="v$VERSION"

# Auto-detect fork tag from build.gradle (FORK_TAG buildConfigField).
# Line format: buildConfigField "String", "FORK_TAG", "\"pisces\""
# Extract the inner-most quoted value (pisces).
FORK_TAG=""
if [[ -f "$GRADLE_FILE" ]]; then
    # Grep the FORK_TAG line, then peel off outer quotes layer by layer.
    FORK_TAG=$(grep 'FORK_TAG' "$GRADLE_FILE" | head -1 \
        | sed -E 's/.*FORK_TAG.*"\\\"([^"]+)\\\"".*/\1/' \
        || true)
fi
if [[ -z "$FORK_TAG" ]]; then
    FORK_TAG="upstream"
fi

BUILD_TYPE_CAP="$(echo "$BUILD_TYPE" | sed 's/\b./\u&/')"
FLAVOR_CAP="$(echo "$FLAVOR" | sed 's/\b./\u&/')"
GRADLE_TASK="assemble${FLAVOR_CAP}${BUILD_TYPE_CAP}"

echo "=== Building MnnLlmChat $VERSION / $BUILD_TYPE / $FLAVOR ==="

# Step 1: Build MNN native library (if missing or not skipped)
if [[ "$SKIP_NATIVE" == false ]]; then
    if [[ ! -f "$NATIVE_BUILD_DIR/lib/libMNN.so" ]]; then
        echo "=== Building MNN native library ==="
        cd "$REPO_ROOT/project/android"
        mkdir -p build_64
        cd build_64
        ../build_64.sh \
            -DMNN_LOW_MEMORY=true \
            -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true \
            -DMNN_BUILD_LLM=true \
            -DMNN_SUPPORT_TRANSFORMER_FUSE=true \
            -DMNN_ARM82=true \
            -DMNN_USE_LOGCAT=true \
            -DMNN_OPENCL=true \
            -DLLM_SUPPORT_VISION=true \
            -DMNN_BUILD_OPENCV=true \
            -DMNN_IMGCODECS=true \
            -DLLM_SUPPORT_AUDIO=true \
            -DMNN_BUILD_AUDIO=true \
            -DMNN_BUILD_DIFFUSION=ON \
            -DMNN_SEP_BUILD=OFF \
            -DBUILD_PLUGIN=ON \
            -DMNN_QNN=OFF \
            -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
            -DCMAKE_INSTALL_PREFIX=.
        make install
        echo "=== MNN native build done ==="
    else
        echo "=== MNN native library found, skipping build ==="
    fi
fi

# Validate signing env vars for release
if [[ "$BUILD_TYPE" == "release" ]]; then
    if [[ -z "$KEYSTORE" ]]; then
        echo "ERROR: KEY_STORE_LOCATION env var not set"
        exit 1
    fi
    if [[ ! -f "$KEYSTORE" ]]; then
        echo "ERROR: Keystore file not found: $KEYSTORE"
        exit 1
    fi
    if [[ -z "$KEYSTORE_PASS" ]]; then
        echo "ERROR: KEY_STORE_PASSWORD env var not set"
        exit 1
    fi
    if [[ -z "$KEY_ALIAS" ]]; then
        echo "ERROR: KEY_ALIAS env var not set"
        exit 1
    fi
fi

# Step 2: Build APK
echo "=== Building APK: $GRADLE_TASK ==="
cd "$SCRIPT_DIR"
./gradlew "$GRADLE_TASK"

# Find APK
BUILD_DIR="$APP_DIR/build/outputs/apk/$FLAVOR/$BUILD_TYPE"
UNSIGNED_APK=""
if [[ "$BUILD_TYPE" == "release" ]]; then
    UNSIGNED_APK="$BUILD_DIR/app-$FLAVOR-release-unsigned.apk"
else
    UNSIGNED_APK="$BUILD_DIR/app-$FLAVOR-debug.apk"
fi

if [[ ! -f "$UNSIGNED_APK" ]]; then
    echo "ERROR: APK not found at $UNSIGNED_APK"
    ls "$BUILD_DIR" 2>/dev/null || true
    exit 1
fi

# Step 3: Sign or copy
SIGNED_APK=""
if [[ "$BUILD_TYPE" == "release" ]]; then
    ALIGNED_APK="$BUILD_DIR/app-release-aligned.apk"
    SIGNED_APK="$SCRIPT_DIR/MnnLlmChat-${VERSION}-${FORK_TAG}-${FLAVOR}-signed.apk"

    echo "=== Aligning ==="
    "$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"

    echo "=== Signing ==="
    java -jar "$BUILD_TOOLS/lib/apksigner.jar" sign \
        --ks "$KEYSTORE" \
        --ks-pass "pass:$KEYSTORE_PASS" \
        --ks-key-alias "$KEY_ALIAS" \
        --key-pass "pass:$KEYSTORE_PASS" \
        --out "$SIGNED_APK" \
        "$ALIGNED_APK"

    rm -f "$ALIGNED_APK"
else
    SIGNED_APK="$SCRIPT_DIR/MnnLlmChat-${VERSION}-${FORK_TAG}-${FLAVOR}-debug.apk"
    cp -f "$UNSIGNED_APK" "$SIGNED_APK"
fi

SIZE=$(du -h "$SIGNED_APK" | cut -f1)
echo "=== Done: $SIGNED_APK ($SIZE) ==="
