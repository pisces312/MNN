#!/bin/bash
# Build htp-ops-lib (MNN custom HTP ops) in WSL.
#
# Usage:   bash build_wsl.sh [DSP_ARCH]      # e.g. bash build_wsl.sh v81
# Env vars (override before calling):
#   HEXAGON_SDK_ROOT   default /home/pisces312/hexagon-sdk-v6.7.0.0
#   ANDROID_NDK_ROOT   default /home/pisces312/android-ndk-r27d
#
# Outputs: outputs/libMNN_htpops.so (Android side) and
#          outputs/libMNN_htpops_skel.so (DSP side, arch-specific).

set -e

DSP_ARCH=${1:-v81}
export HEXAGON_SDK_ROOT=${HEXAGON_SDK_ROOT:-/home/pisces312/hexagon-sdk-v6.7.0.0}
export ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-/home/pisces312/android-ndk-r27d}

export PATH=$HEXAGON_SDK_ROOT/build/cmake/Ubuntu:$HEXAGON_SDK_ROOT/tools/wrapperTools:$PATH
export ANDROID_ROOT_DIR=$ANDROID_NDK_ROOT
export DEFAULT_HEXAGON_TOOLS_ROOT=$HEXAGON_SDK_ROOT/tools/HEXAGON_Tools/21.0.04
export DEFAULT_DSP_ARCH=v68
export DEFAULT_HLOS_ARCH=64
# SDK 6.7 slim install has no bundled cmake dir; use system cmake 3.28.3 (/usr/bin/cmake).
export CMAKE_ROOT_PATH=${CMAKE_ROOT_PATH:-/usr}
export QAIC_PATH=$HEXAGON_SDK_ROOT/ipc/fastrpc/qaic/bin
export PATH=$QAIC_PATH:$PATH

cd "$(dirname "$0")"

echo "=== Building Android (HLOS) side ==="
# SDK 6.7's android preset already targets arm64-v8a (PREBUILT_LIB_DIR=android_aarch64),
# and DEFAULT_HLOS_ARCH=64 makes build_cmake output to android_ReleaseG_aarch64/.
STUB_DIR=android_ReleaseG_aarch64
touch include/htp_ops.idl
build_cmake android BUILD=ReleaseG 2>&1 || { echo "FAILED: android aarch64 build"; exit 1; }

echo "=== Building Hexagon (DSP) side for $DSP_ARCH ==="
touch include/htp_ops.idl
build_cmake hexagon BUILD=ReleaseG DSP_ARCH=$DSP_ARCH DOMAIN_FLAG=3 2>&1 || { echo "FAILED: hexagon build"; exit 1; }

echo "=== Copying outputs ==="
rm -rf outputs
mkdir outputs
cp "$STUB_DIR/ship/libMNN_htpops.so" outputs/
# SDK 6.7 names the DSP build dir hexagon_ReleaseG__<arch> (empty tool variant).
SKEL_DIR=$(ls -d hexagon_ReleaseG_*_$DSP_ARCH 2>/dev/null | head -1)
cp "$SKEL_DIR/libMNN_htpops_skel.so" outputs/

echo "=== Verifying architecture ==="
file outputs/libMNN_htpops.so | grep -q "aarch64" || { echo "FAILED: stub is not aarch64"; file outputs/libMNN_htpops.so; exit 1; }
file outputs/libMNN_htpops_skel.so | grep -q "ELF" || { echo "FAILED: skel is not ELF"; exit 1; }
echo "Architecture OK."

echo "=== Checking symbols ==="
./check_so_symbols.sh outputs/libMNN_htpops_skel.so || { echo "FAILED: symbol check"; exit 1; }

echo "=== DONE ==="
ls -la outputs/
