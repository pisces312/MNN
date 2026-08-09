#!/bin/bash
# Build MNN x86_64 host tools for QNN offline conversion (generateIO, compilefornpu, MNN2QNNModel)
set -eo pipefail
BUILD_DIR=~/mnn-x86-qnn-build
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"
cmake /mnt/d/3rd-party-projects/MNN \
  -DCMAKE_BUILD_TYPE=Release \
  -DMNN_BUILD_LLM=ON \
  -DMNN_QNN=ON \
  -DMNN_QNN_CONVERT_MODE=ON \
  -DMNN_WITH_PLUGIN=OFF \
  -DMNN_BUILD_TOOLS=ON \
  -DMNN_BUILD_TEST=OFF \
  -DMNN_BUILD_BENCHMARK=OFF \
  -DMNN_BUILD_CONVERTER=OFF \
  -DQNN_SDK_ROOT=/mnt/d/dev/qairt/2.39.0.250926
make -j8 generateIO compilefornpu MNN2QNNModel
echo "=== tools built ==="
ls -la generateIO compilefornpu MNN2QNNModel