#!/bin/bash
# Build htp-ops-lib (MNN custom HTP ops) in WSL.
#
# Usage:   bash build_wsl.sh [DSP_ARCH]      # e.g. bash build_wsl.sh v81
# Env vars (override before calling):
#   HEXAGON_SDK_ROOT   default /home/pisces312/hexagon-sdk-v6.6.0.0
#   ANDROID_NDK_ROOT   default /home/pisces312/android-ndk-r27d
#
# Outputs: outputs/libMNN_htpops.so (Android side) and
#          outputs/libMNN_htpops_skel.so (DSP side, arch-specific).

set -e

DSP_ARCH=${1:-v81}
export HEXAGON_SDK_ROOT=${HEXAGON_SDK_ROOT:-/home/pisces312/hexagon-sdk-v6.6.0.0}
export ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-/home/pisces312/android-ndk-r27d}

export PATH=$HEXAGON_SDK_ROOT/build/cmake/Ubuntu:$HEXAGON_SDK_ROOT/tools/wrapperTools:$PATH
export ANDROID_ROOT_DIR=$HEXAGON_SDK_ROOT/tools/android-ndk-r25c
export DEFAULT_HEXAGON_TOOLS_ROOT=$HEXAGON_SDK_ROOT/tools/HEXAGON_Tools/19.0.07
export DEFAULT_DSP_ARCH=v68
export CMAKE_ROOT_PATH=$HEXAGON_SDK_ROOT/tools/cmake-3.28.3-linux-x86_64
export QAIC_PATH=$HEXAGON_SDK_ROOT/ipc/fastrpc/qaic/bin
export PATH=$QAIC_PATH:$PATH

cd "$(dirname "$0")"

echo "=== Building Android (HLOS) side ==="
# build_cmake android creates android_ReleaseG/ (32-bit armeabi-v7a) regardless
# of HLOS_ARCH — the flag doesn't propagate to ANDROID_ABI in this SDK version.
# We need android_ReleaseG_aarch64/ (arm64-v8a). If that dir exists (has correct
# cmake cache), just rebuild it; otherwise configure it first.
STUB_DIR=android_ReleaseG_aarch64
if [ ! -d "$STUB_DIR" ]; then
    echo "Configuring $STUB_DIR (first time)..."
    mkdir -p "$STUB_DIR"
    cd "$STUB_DIR"
    cmake -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DOS_TYPE=HLOS \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_NDK=$HEXAGON_SDK_ROOT/tools/android-ndk-r25c \
        -DCMAKE_TOOLCHAIN_FILE=$HEXAGON_SDK_ROOT/build/cmake/android_toolchain.cmake \
        -DDOMAIN_FLAG=3 \
        ..
    cd ..
fi
# Touch the IDL to force QAIC regeneration (incremental builds may miss it).
touch include/htp_ops.idl
cmake --build "$STUB_DIR" 2>&1 || { echo "FAILED: android aarch64 build"; exit 1; }

echo "=== Building Hexagon (DSP) side for $DSP_ARCH ==="
touch include/htp_ops.idl
build_cmake hexagon BUILD=ReleaseG DSP_ARCH=$DSP_ARCH DOMAIN_FLAG=3 2>&1 || { echo "FAILED: hexagon build"; exit 1; }

echo "=== Copying outputs ==="
rm -rf outputs
mkdir outputs
cp "$STUB_DIR/libMNN_htpops.so" outputs/
cp hexagon_ReleaseG_toolv19_$DSP_ARCH/libMNN_htpops_skel.so outputs/

echo "=== Verifying architecture ==="
file outputs/libMNN_htpops.so | grep -q "aarch64" || { echo "FAILED: stub is not aarch64"; file outputs/libMNN_htpops.so; exit 1; }
file outputs/libMNN_htpops_skel.so | grep -q "ELF" || { echo "FAILED: skel is not ELF"; exit 1; }
echo "Architecture OK."

echo "=== Checking symbols ==="
./check_so_symbols.sh outputs/libMNN_htpops_skel.so || { echo "FAILED: symbol check"; exit 1; }

echo "=== DONE ==="
ls -la outputs/
