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
# NOTE: BUILD/HLOS_ARCH select the variant dir android_ReleaseG_aarch64 and the
# matching prebuilt fastrpc lib; plain "build_cmake android" defaults to
# armeabi-v7a and fails to link libcdsprpc.so.
build_cmake android BUILD=ReleaseG HLOS_ARCH=aarch64 DOMAIN_FLAG=3 2>&1 || { echo "FAILED: android build"; exit 1; }

echo "=== Building Hexagon (DSP) side for $DSP_ARCH ==="
build_cmake hexagon BUILD=ReleaseG DSP_ARCH=$DSP_ARCH DOMAIN_FLAG=3 2>&1 || { echo "FAILED: hexagon build"; exit 1; }

echo "=== Copying outputs ==="
rm -rf outputs
mkdir outputs
cp android_ReleaseG_aarch64/libMNN_htpops.so outputs/
cp hexagon_ReleaseG_toolv19_$DSP_ARCH/libMNN_htpops_skel.so outputs/

echo "=== Checking symbols ==="
./check_so_symbols.sh outputs/libMNN_htpops_skel.so || { echo "FAILED: symbol check"; exit 1; }

echo "=== DONE ==="
ls -la outputs/
