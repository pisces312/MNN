# mmap 权重（`use_mmap`）使用说明

适用：MnnLlmChat（apps/Android/MnnLlmChat）与 `transformers/llm` 引擎。
记录 `use_mmap` 的开关位置、调用链、实际行为与代价，避免每次重新读源码。

## 1. 开关位置

| 层 | 位置 | 默认 |
|---|---|---|
| 模型 config.json | `"use_mmap": true` | `false`（`ModelConfig.kt:32` `@SerializedName("use_mmap")`） |
| Kotlin | `LlmSession.kt:79` 读 `config.useMmap`，为真时取 `MmapUtils.getMmapDir(modelId)` 并 `mkdirs()` | — |
| JNI / native | `llm_session.cpp:203`：`use_mmap = !extra_config["mmap_dir"].empty()`；为真时 `config["tmp_path"] = mmap_dir` | — |
| 引擎 | `llmconfig.hpp:395` `use_mmap` → `llm.cpp:216` `setExternalPath(tmp_path, EXTERNAL_WEIGHT_DIR)` | `false` |

即：app 侧只需让 `mmap_dir` 非空（由 config 的 `use_mmap` 决定），native 会自动把 `use_mmap` 和
`tmp_path` 一起设好。

mmap 目录：`MmapUtils.getMmapDir()` → `filesDir/tmps/<safeModelId>`（本地模型 `local_temps/`，
内置模型 `builtin_temps/`），**按 modelId 分目录**，跨模型不会互相污染。

## 2. 调用链（源码级）

```
use_mmap=true
  → Llm::setRuntimeHint / initRuntime
      llm.cpp:216   rtg->setExternalPath(tmp_path, Interpreter::EXTERNAL_WEIGHT_DIR)
      llm.cpp:231   rtg->setHint(MMAP_FILE_SIZE, mmap_size)      // 默认 1024
  → Session::ModeGroup::setExternalPath
      Session.cpp:152  runtimeHint.weightMemoryPath = path
  → CPUBackend::onCreate
      CPUBackend.cpp:281  if (hint().weightMemoryPath.size() > 0)
          createMmap(dir, prefix, "static", autoRemove, syncValid)
          mStaticAllocator.reset(new EagerBufferAllocator(mmapMem, 32, mmapFileSize))
```

**关键：`weightMemoryPath` 全仓库只有 `CPUBackend.cpp` 一处消费。**
CPU 后端的 STATIC buffer（权重）改从 mmap 文件池分配；QNN / Hexagon / OpenCL / Vulkan 的权重
不经由这个池 → **NPU / GPU 后端下开 `use_mmap` 基本无收益**，只有 CPU 侧算子（embedding 等）受益。

## 3. 相关参数

| 参数 | 位置 | 默认 | 含义 |
|---|---|---|---|
| `use_mmap` | llmconfig.hpp:395 | `false` | 权重是否走文件映射 |
| `use_cached_mmap` | llmconfig.hpp:398 | **`true`** | mmap 文件跨运行保留 + 信任缓存 |
| `mmap_size` | llmconfig.hpp:401 | `1024` | **单个 mmap 文件的最小分配粒度（MB）**，不是总量上限 |
| `kvcache_mmap` | llmconfig.hpp:407 | `false` | 独立开关：KV cache 落盘；prefix cache 依赖它（`llm.cpp:1403`） |
| `tmp_path` | llmconfig.hpp:410 | `""` | mmap 文件 / GPU shader cache / KV cache 目录 |

## 4. 实际行为

- **`use_cached_mmap=true`（默认）**：`autoRemove=false`，mmap 文件**不自动删除**。若
  `<prefix>sync.static` 存在 → `syncValid=true` → `useCachedMmap` 由 1 变 2 → 后续运行直接信任
  缓存池，跳过权重重打包（见 `ConvInt8TiledExecutor.cpp:725`、`ConvolutionCommon.cpp:571` 的
  `useCachedMmap > 1` 分支）。这是"二次加载更快"的来源。
- **`use_cached_mmap=false`**：`autoRemove=true`，进程退出删文件，每次重建 → 只剩首次写盘的开销。
- **文件名前缀**：`"0_0_0_0_"`，只编码 precision / memory / power 三档（源码里 `modelUUID`
  拼接那行是注释掉的）。

## 5. 代价与风险

1. **磁盘占用 ≈ 一份模型权重**，长期驻留在 mmap 目录，直到清缓存或删模型。
2. **首次加载变慢**（需写盘生成 mmap 文件 + 权重重打包）。
3. **推理抖动**：页未驻留时触发 major fault（从 UFS 读），首 token 变慢、偶发卡顿；页热后接近常驻。
4. **缓存过期**：同模型换权重但精度/内存/电源档位不变时，前缀不变 + `sync.static` 仍在 →
   会复用旧缓存。此时必须清 mmap 目录（`MmapUtils.clearMmapCache(modelId)`，或 app 内删模型）。
5. **`tmp_path` 不可写**：`llm.cpp:201` 只打一条 `MNN_ERROR` 继续，GPU shader cache 与 mmap
   权重一并失效（表现为每次都重新编译 shader、权重走普通内存）。
6. **LoRA 强制关闭**：`llm.cpp:505` 加载 lora 时写死 `use_mmap:false, use_cached_mmap:false`。

## 6. 建议

- 内存吃紧的低端机 + CPU 后端：值得开（RSS 可降，被 LMK 杀的概率下降）。
- QNN / Hexagon / OpenCL 后端：不必开，收益极小，白占一份磁盘。
- 开发调试期：保持关闭，避免缓存过期导致的"改了权重没生效"假象。

## 7. 待办（half-landed）

`ModelPreferences.KEY_USE_MMAP` 常量已定义但全仓库无引用 → **UI 侧开关未接线**。
目前只能在模型 `config.json` 里手动设 `use_mmap`；`BenchmarkService.kt` 里 `useMmap` 硬编码 `false`。
