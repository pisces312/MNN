# QNN NPU 推理指南

本指南介绍如何使用 Qualcomm QNN (Qualcomm Neural Network) 在高通 NPU 上运行 MNN LLM 模型。

## 概述

QNN 是高通提供的神经网络推理 SDK，支持在 Hexagon HTP (Hexagon Tensor Processor) 上运行模型。
相比 CPU 推理，NPU 推理具有以下优势：

- **低功耗**：NPU 功耗约为 CPU 的 1/3-1/5
- **不占 CPU**：NPU 独立运行，app UI 保持流畅
- **无热降频**：低功耗意味着 SoC 温度可控，推理速度长时间稳定

## 环境要求

### 硬件
- 高通骁龙处理器（推荐 SM8850/8 Elite Gen5 或更新）
- arm64-v8a 设备
- Android 8.0+ (API 26)

### 软件
- QNN SDK 2.39.0+（下载地址：https://developer.qualcomm.com/software/qualcomm-ai-engine-direct）
- Android NDK r27+
- Python 3.8+（推荐 conda 环境）
- MNN 源码

## 构建步骤

### 1. 构建 x86 转换工具（WSL）

```bash
cd /path/to/MNN/tools/qnn
bash build_x86_qnn_tools.sh
```

构建产物：
- `generateIO`：生成测试输入
- `compilefornpu`：模型切分与预处理
- `MNN2QNNModel`：模型转换

### 2. 构建 Android 版 libMNN.so（WSL）

```bash
cd /path/to/MNN/project/android
mkdir build_64 && cd build_64
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
  -DMNN_QNN=ON \
  -DQNN_SDK_ROOT=/mnt/d/dev/qairt/2.39.0.250926 \
  -DMNN_WITH_PLUGIN=ON \
  -DMNN_HEXAGON=ON \
  -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
  -DCMAKE_INSTALL_PREFIX=.
make install
```

## 模型导出与转换

### 1. 导出 NPU 模型

```bash
cd /path/to/MNN/tools/qnn
bash export_npu_model.sh <hf_model_dir> <dst_dir>
```

参数说明：
- `hf_model_dir`：HuggingFace 模型目录
- `dst_dir`：输出目录

导出选项说明：
- `--generate_for_npu`：生成 NPU 兼容的模型
- `--seperate_embed`：分离 embedding 层
- `--disable_transformer_c4`：禁用 C4 布局（QNN 必须）
- `--quant_bit 4`：4-bit 量化
- `--quant_block 64`：量化块大小
- `--act_bit=16`：激活值16-bit
- `--sym`：对称量化
- `--omni`：omni 量化
- `--hqq`：HQQ 量化

### 2. 转换为 QNN 离线模型

```bash
cd /path/to/MNN/tools/qnn
bash convert_qnn.sh <model_dir> <cache_name>
```

参数说明：
- `model_dir`：导出的 MNN 模型目录
- `cache_name`：缓存目录名称

转换产物：
- `qnn/llm.mnn`：plugin op 描述文件（~2KB）
- `qnn/graph0.bin`：HTP context binary（~325MB）
- `config_qnn.json`：配置文件

### 3. 配置文件说明

转换完成后，将 `config_qnn.json` 内容合并到模型目录的 `config.json`：

```json
{
  "llm_model": "qnn/llm.mnn",
  "llm_weight": "llm.mnn.weight",
  "use_external_weight": false,
  "chunk_limits": [128, 1],
  "backend_type": "cpu",
  "thread_num": 4,
  "precision": "low",
  "memory": "low"
}
```

关键配置：
- `llm_model`：指向 `qnn/llm.mnn`（plugin 模型）
- `use_external_weight`：设为 `false`（plugin 模型权重在 graph0.bin 中）
- `chunk_limits`：prefill 块大小（推荐128）
- `backend_type`：保持 `cpu`（plugin op 在 CPU backend 注册）

## 部署到设备

### 1. 推送模型文件

```bash
adb push <model_dir> /sdcard/mnn-models/<model_name>/
```

必需文件：
- `config.json`
- `llm_config.json`
- `tokenizer.mtok`
- `embeddings_bf16.bin`
- `qnn/llm.mnn`
- `qnn/graph0.bin`

**注意**：目录名不要包含 `qnn` 字样，否则会触发 app 的下载流程。

### 2. 安装 APK

```bash
cd /path/to/MNN/apps/Android/MnnLlmChat
./build.sh release standard
adb install -r MnnLlmChat-v0.8.3.3-pisces-standard-signed.apk
```

## 性能对比

### SM8850 实测数据（Qwen3-0.6B）

| 指标 | CPU fp16 (4T) | Hexagon | QNN HTP (NPU) |
|------|:---:|:---:|:---:|
| Prefill | ~306 t/s | ~49.7 t/s | 12.8~20.7 t/s |
| Decode | ~70 t/s | ~1.5 t/s | **~32 t/s** |

### 推荐后端选择

| 模型规模 | 推荐后端 | 原因 |
|---|---|---|
| ≤1B | CPU fp16 | prefill + decode 都最快 |
| 2-4B | QNN HTP | 低功耗 + 不占 CPU |
| ≥7B | QNN HTP | CPU 带宽瓶颈，NPU 更快 |

## 常见问题

### Q: 转换时报错 "QNN_SDK_ROOT not set"

A: 确保设置了 QNN_SDK_ROOT 环境变量：
```bash
export QNN_SDK_ROOT=/path/to/qairt/2.39.0.250926
```

### Q: 模型加载失败

A: 检查：
1. `config.json` 中 `llm_model` 路径是否正确
2. `qnn/llm.mnn` 和 `qnn/graph0.bin` 是否存在
3. `use_external_weight` 是否设为 `false`

### Q: NPU 推理速度比 CPU 慢

A: 小模型（≤1B）在 NPU 上可能比 CPU 慢，这是正常的。NPU 优势在大模型（4B+）和低功耗场景。

### Q: 如何查看 NPU 是否启用

A: 使用 logcat 过滤：
```bash
adb logcat | grep -aE "MNNJNI|MNN_QNN|Qnn"
```

应看到：
```
[MNN::Hexagon] vectorSize=64, vtcmSize=8388608, maxThreads=8
```

## 调试

### 日志过滤

```bash
adb logcat | grep -aE "MNNJNI|MNN_QNN|Qnn|Hexagon"
```

### 性能分析

构建时开启 profile：
```bash
-DMNN_GPU_TIME_PROFILE=ON
```

运行后在 logcat 中查看 DSP 算子耗时统计。

## 参考文档

- [QNN SDK 文档](https://developer.qualcomm.com/software/qualcomm-ai-engine-direct)
- [MNN GitHub](https://github.com/alibaba/MNN)
- [MNN QNN Backend 源码](https://github.com/alibaba/MNN/tree/master/source/backend/qnn)