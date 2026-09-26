# `MNN_KLEIDIAI` 编译开关调研

调研日期：2026-09-26（上游 674500016 之后，该选项默认值由 `ON` 改为 `OFF`）。
本文只做记录，未改动任何构建参数。

## 1. 结论速览

| 问题 | 结论 |
|---|---|
| 这个开关是什么 | 把 Arm 官方微内核库 **KleidiAI** 编译进 `MNNARM64`（INT4/INT8 量化 matmul + SME2 matmul） |
| 上游改默认为 OFF 会不会让我变慢 | **不会**。本 fork 一直没启用过（见第 3 节），运行时行为零变化 |
| 推荐开启吗 | 默认保持 OFF；要开必须同时开 `MNN_KLEIDIAI_DEFAULT_ON`，且先确认设备支持 SME2 |

## 2. 两层开关（关键）

编译进库 ≠ 运行时候用。这是两个独立的开关：

**第一层：编译期 `MNN_KLEIDIAI`（CMakeLists.txt:274，现默认 OFF）**

- 仅在 aarch64/arm64 生效（`source/backend/cpu/arm/CMakeLists.txt:32`）
- `include(cmake/KleidiAI.cmake)` → `download_kleidiai_and_collect_sources()`
- 从 GitHub 下载 `kleidiai-1.16.0` tarball 并校验 MD5（`0a9e9008adb6031f9e8cf70dff4a3321`）；
  **下载失败只 warning 并跳过，不会让构建失败**；可用 `-DKLEIDIAI_SRC_DIR=<本地源码>` 跳过下载
- 编入的算子实现：`KleidiAIConvolution.cpp` / `KleidiAIConvolutionDepthwise.cpp` /
  `KleidiAIConvInt8.cpp` / `KleidiAIDenseConvolution.cpp`

**第二层：运行期 `enableKleidiAI`**

```cpp
// source/core/Backend.hpp:71
#ifdef MNN_DEFAULT_USE_KLEIDIAI
    bool enableKleidiAI = true;
#else
    bool enableKleidiAI = false;   // ← 默认走这里
#endif
```

- `MNN_DEFAULT_USE_KLEIDIAI` 只在 `MNN_KLEIDIAI_DEFAULT_ON=ON` 时定义
  （`source/backend/cpu/arm/CMakeLists.txt:35`，该选项默认 OFF）
- 也可运行时用 `Interpreter::CPU_ENABLE_KLEIDIAI`（=16）hint 打开（`Session.cpp:121`）
- 消费者：`ConvolutionFloatFactory.cpp:132`、`CPUConvolutionDepthwise.cpp:314`

**本 fork 的状态**：`build_native.sh` 未设 `MNN_KLEIDIAI_DEFAULT_ON`；
全仓库只有 `benchmark/benchmark.cpp:133` 与 `test/main.cpp:71` 设过 `CPU_ENABLE_KLEIDIAI`，
`llm.cpp` 与 app 均未设置 → **APK 里 KleidiAI kernel 从未启用**。
因此上游默认值 ON→OFF 对本 fork **运行时零影响**，只是不再下载编译那份源码。

## 3. KleidiAI 是什么，能加速什么

Arm 官方开源微内核库（纯 C/H，无外部依赖、无动态内存分配），已集成进
XNNPACK / LiteRT / MNN / ONNX Runtime / ExecuTorch / llama.cpp。在 MNN 里覆盖：

- **INT4 / INT8 动态量化 matmul**：用 SDOT、I8MM 指令（不依赖 SME2，主流 armv8.2+ 可用）
- **SME2 上的 F32 / F16 / Int8 matmul**：依赖 Armv9.2-A SME2 硬件
- 1x1 卷积快速路径、depthwise（`CPUConvolutionDepthwise.cpp:314` 要求 `sme2` 可用）

Arm / Google 公开数据（均为 **SME2 硬件**上测得，不是通用收益）：
Gemma 3 聊天响应 6x；vivo X200 Pro 上 Phi-3 Mini 3.8B prompt 处理 2.6x；
Stable Audio Open Small 生成耗时减半以上。

## 4. 是否推荐开启

**默认保持 OFF（与上游一致）**，理由：

1. 上面那些 6x / 2.6x 收益依赖 SME2 硬件。骁龙 8 Gen 3 / 8 Elite（OnePlus 13、小米 14 Ultra）
   大概率不支持 SME2，此时只剩 INT4/INT8 量化路径的收益。
   实测设备是否支持：`adb shell cat /proc/cpuinfo | grep -i sme`（有输出才支持）。
2. 本 fork 主路径是 QNN / Hexagon / OpenCL，CPU 是兜底，受益面窄。
3. 构建时会访问 GitHub 下载 tarball，网络不通会静默跳过（表现为"开了等于没开"，难排查）。

**若要实测开启**，需要两个选项一起加（只加 `MNN_KLEIDIAI=ON` 是无效的开）：

```
-DMNN_KLEIDIAI=ON -DMNN_KLEIDIAI_DEFAULT_ON=ON
```

验证方式：用 `benchmark` 工具（支持 `enableKleidiAI` 参数）对比开关前后的耗时，
不要靠推理感觉判断。

## 5. 相关选项

| 选项 | 默认 | 作用 |
|---|---|---|
| `MNN_KLEIDIAI` | OFF（原 ON） | 编译 KleidiAI 源码进 MNNARM64 |
| `MNN_KLEIDIAI_DEFAULT_ON` | OFF | 定义 `MNN_DEFAULT_USE_KLEIDIAI`，让 `enableKleidiAI` 默认 true |
| `Interpreter::CPU_ENABLE_KLEIDIAI` | — | 运行时 hint，逐 session 打开 |
| `MNN_SME2` | ON | 编译 SME2 汇编核（与 KleidiAI 独立） |
| `CPU_SME2_NEON_DIVISION_RATIO` | 41 | LLM 推理时 SME 与 NEON 的线程分配比例 |
