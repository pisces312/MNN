#!/bin/bash
# MNN native (arm64-v8a) build script for MnnLlmChat — must run inside WSL (Ubuntu).
# Usage: ./build_native.sh [--clean]
#   --clean   rm -rf build_64 before configuring (default: incremental)
#
# Normally invoked by apps/Android/MnnLlmChat/build.sh from Git Bash via
# `wsl -d Ubuntu -- bash /mnt/d/.../build_native.sh`; can also be run directly in WSL.
set -e

CLEAN=false
for arg in "$@"; do
    case "$arg" in
        --clean) CLEAN=true ;;
        *) echo "Usage: $0 [--clean]"; exit 1 ;;
    esac
done

# WSL Linux NDK: r27d = 27.3.13750724 (source.properties verified). Built under WSL, not Git Bash/cmd.
export ANDROID_NDK=${ANDROID_NDK:-/mnt/d/dev/android-ndk-r27d}
echo "ANDROID_NDK=$ANDROID_NDK"

MNN_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$MNN_ROOT/project/android"

if [[ "$CLEAN" == true ]]; then
    echo "=== Clean build: rm -rf build_64 ==="
    rm -rf build_64
fi
mkdir -p build_64
cd build_64

echo "=== CMake Configure ==="
cmake ../../../ \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DANDROID_ABI="arm64-v8a" \
  -DANDROID_STL=c++_static \
  -DANDROID_NATIVE_API_LEVEL=android-21 \
  -DMNN_USE_SSE=OFF \
  -DMNN_BUILD_BENCHMARK=OFF \
  -DMNN_BUILD_TEST=OFF \
  -DMNN_BUILD_FOR_ANDROID_COMMAND=true \
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
  -DMNN_QNN=ON \
  -DMNN_WITH_PLUGIN=ON \
  -DQNN_SDK_ROOT="${QNN_SDK_ROOT:-/mnt/d/dev/qairt/2.39.0.250926}" \
  -DMNN_HEXAGON=ON \
  -DMNN_GPU_TIME_PROFILE=ON \
  -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
  -DCMAKE_INSTALL_PREFIX=.

echo "=== Make ==="
make -j$(nproc) install 2>&1

echo "=== Done ==="
ls -lh lib/libMNN.so 2>/dev/null && echo "libMNN.so OK" || { echo "libMNN.so MISSING"; exit 1; }
