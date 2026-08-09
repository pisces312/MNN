# QNN 模型移除冗余 weight 文件

## 背景

QNN 离线模型的 `llm.mnn.weight`（~303MB）从未被运行时读取——plugin 模型的权重全部编译在 `qnn/graph0.bin` 中。但 `llm.cpp` 的加载检查硬性要求该文件存在。

## 解决方案

新增 `use_external_weight` 配置项（默认 `true`），设为 `false` 时跳过 weight 文件检查和 `setExternalFile`。

### config.json 示例

```json
{
  "llm_model": "qnn/llm.mnn",
  "use_external_weight": false,
  "chunk_limits": [128, 1]
}
```

### 改动文件

- `transformers/llm/engine/src/llmconfig.hpp`：新增 `use_external_weight()` 方法
- `transformers/llm/engine/src/llm.cpp`：条件跳过 weight 检查和 setExternalFile
- `transformers/llm/export/npu/generate_llm_qnn.py`：自动生成 `"use_external_weight": false`

## 收益

每个 QNN 模型节省 **303MB** 存储，部署时少推送一个大文件。

## 注意

- `embeddings_bf16.bin` 仍需保留（CPU embedding 层）
- 普通 CPU 模型不受影响（默认 `use_external_weight: true`）