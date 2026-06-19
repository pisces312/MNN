#!/bin/bash
set -e

export ANDROID_NDK=/mnt/d/dev/android_sdk/ndk/27.0.12077973
echo "ANDROID_NDK=$ANDROID_NDK"

MNN_ROOT=/mnt/d/3rd-party-projects/MNN
cd $MNN_ROOT/project/android

# Create build dir
rm -rf build_64
mkdir -p build_64
cd build_64

echo "=== CMake Configure ==="
cmake ../../../ \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DANDROID_ABI="arm64-v8a" \
  -DANDROID_STL=c++_static \
  -DANDROID_NATIVE_API_LEVEL=android-21 \
  -DMNN_USE_LOGCAT=true \
  -DMNN_BUILD_BENCHMARK=OFF \
  -DMNN_BUILD_TEST=OFF \
  -DMNN_USE_SSE=OFF \
  -DMNN_BUILD_FOR_ANDROID_COMMAND=true \
  -DMNN_LOW_MEMORY=true \
  -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true \
  -DMNN_BUILD_LLM=true \
  -DMNN_SUPPORT_TRANSFORMER_FUSE=true \
  -DMNN_ARM82=true \
  -DMNN_OPENCL=true \
  -DLLM_SUPPORT_VISION=true \
  -DMNN_BUILD_OPENCV=true \
  -DMNN_IMGCODECS=true \
  -DLLM_SUPPORT_AUDIO=true \
  -DMNN_BUILD_AUDIO=true \
  -DMNN_BUILD_DIFFUSION=ON \
  -DMNN_SEP_BUILD=OFF \
  -DCMAKE_INSTALL_PREFIX=.

echo "=== Make ==="
make -j$(nproc) install 2>&1

echo "=== Done ==="
ls -lh lib/libMNN.so 2>/dev/null && echo "libMNN.so OK" || echo "libMNN.so MISSING"
