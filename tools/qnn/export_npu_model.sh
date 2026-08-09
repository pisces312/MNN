#!/bin/bash
# Export MNN LLM model for QNN NPU (offline-compile compatible)
# Usage: bash export_npu_model.sh <hf_model_dir> <dst_dir>
set -eo pipefail
SRC=${1:-/mnt/d/models/Qwen3-0.6B}
DST=${2:-/mnt/d/models/qwen3_0_6b_npu}
CALIB=${3:-/mnt/d/workspace/mnn-research/calib_prompts.txt}
MNNCONVERT=${4:-/mnt/d/3rd-party-projects/MNN/build_convert/MNNConvert}
PY=${5:-~/miniconda3/envs/mnnexport/bin/python}
cd /mnt/d/3rd-party-projects/MNN/transformers/llm/export
$PY llmexport.py \
  --path "$SRC" \
  --export mnn \
  --quant_block 64 --quant_bit 4 \
  --generate_for_npu --seperate_embed --disable_transformer_c4 \
  --act_bit=16 --sym --omni --hqq \
  --calib_data "$CALIB" \
  --mnnconvert "$MNNCONVERT" \
  --dst_path "$DST"
echo "=== export done: $DST ==="
ls -la "$DST"