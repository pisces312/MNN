#!/bin/bash
# Convert an MNN LLM model to QNN offline artifacts (generate_llm_qnn.py)
# Usage: convert_qnn.sh <model_dir> <cache_name>
set -eo pipefail

MODEL_DIR="$1"           # e.g. /mnt/d/models/qwen3_0_6b_hexagon
CACHE_NAME="$2"          # e.g. qnn_cache_06b
export QNN_SDK_ROOT=/mnt/d/dev/qairt/2.39.0.250926
export LD_LIBRARY_PATH="$QNN_SDK_ROOT/lib/x86_64-linux-clang:${LD_LIBRARY_PATH:-}"
# qnn-model-lib-generator needs clang++; shim it to g++ (generic flags only, see make_clang_shim.sh)
export PATH="$HOME/qnn_toolchain_shim:$PATH"

MNN_BUILD=~/mnn-x86-qnn-build
# generate_llm_qnn.py resolves npu_convert.py as <mnn_path>/../source/backend/qnn/npu_convert.py
[ -e ~/source ] || ln -s /mnt/d/3rd-party-projects/MNN/source ~/source

WORK=~/qnn_convert_work
mkdir -p "$WORK"
cd "$WORK"

python3 /mnt/d/3rd-party-projects/MNN/transformers/llm/export/npu/generate_llm_qnn.py \
  --model "$MODEL_DIR" \
  --soc_id 87 \
  --dsp_arch v81 \
  --mnn_path "$MNN_BUILD" \
  --cache_path ~/"$CACHE_NAME" \
  --chunk_size 128 \
  --max_history_token 2048 \
  --vtcm_mb 8

echo "=== conversion done, artifacts: ==="
ls -la "$MODEL_DIR/qnn/" 2>/dev/null
cat "$MODEL_DIR/config_qnn.json" 2>/dev/null