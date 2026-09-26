# CPU 推理速度波动调研：同版本 APK 时快时慢（差约一倍）

日期：2026-09-26
现象：同一 APK、同一手机、CPU 引擎跑 Qwen 模型，decode 速度在多次会话间波动，最差相差约 2x。

## 结论

**MNN 代码没有随机逻辑；波动来自默认配置（power=normal）下不做 CPU 核心绑定，推理线程落到哪个 core 簇由系统 EAS 调度器决定。** 线程全落大核 = 快；部分线程被丢到小核（LITTLE 簇）= 慢一半以上，正好对应"差一倍"。

## 代码证据链

1. **App 侧未传 power**
   - `ModelConfig.kt` 只有 `backend_type` / `thread_num`（默认 4），没有 `power` 字段 → 生成的 llm config 无 `power` 键。
   - 引擎侧默认值：`transformers/llm/engine/src/llmconfig.hpp:201` `power()` 默认返回 `"normal"`。

2. **normal 不绑定核心**
   - `transformers/llm/engine/src/llm.cpp:244-248`：只有 `"high"`/`"low"` 才设置 `BackendConfig::Power_High/Low`，normal 走 `Power_Normal`。
   - `source/backend/cpu/CPUBackend.cpp` `_validateCpuIds()`（190-207 行）：
     - `Power_High` → 选最大频率组（大核簇）
     - `Power_Low` → 选最小频率组（小核簇）
     - **`Power_Normal` → `mCpuIds` 保持为空**
   - `_bindCPUCore()`（CPUBackend.cpp:85）第一行 `if (mCpuIds.empty()) return;` → 不调用 `MNNSetSchedAffinity`（`CPURuntime.cpp:142`，内部是 `sched_setaffinity` 系统调用）。

3. **结果**：OpenMP / ThreadPool 工作线程在全核（含 LITTLE 簇）上自由调度，每次会话落核不同 → 速度不可复现。

## 其他因素（按影响排序）

| 因素 | 性质 | 说明 |
|---|---|---|
| 线程落核随机（无 affinity） | 代码+调度 | 主因，见上 |
| 线程共享同一 mask，非逐核 pin | 代码设计 | 即使绑了，`_bindCPUCore` 给所有线程设同一个全组 mask（CPUBackend.cpp:95-98），线程仍可在簇内互相挤占/迁移；后台 App 抢核时被挤走 |
| 热节流 | 非代码 | decode 持续满载，机身升温降频，跨 session 对比会失真 |
| mmap 首轮 page fault | 代码 | 仅影响第一轮 prefill，不是波动主因 |
| kernel 选择（fp16/dot/i8mm/SME2） | 确定性 | 启动时按设备检测一次，每次一致，可排除 |

## 附注：power=high 的行为

`_validateCpuIds()` 的 `Power_High` 分支按线程数从最高频簇开始装核；若 `thread_num` > 大核簇核数，会把次级簇并进来。源码注释明确警告：**跨簇混用（尤其混入小核）会显著掉速，应严格避免**（CPUBackend.cpp:168-171）。因此开 high 后线程数建议 ≤ 大核簇核数。

启动日志中 MNN 会打印各簇信息（`CPURuntime.cpp:1717-1723`）：
```
CPU Group: [ 0  1  2  3 ], 1017600 - 1804800   （小核簇）
CPU Group: [ 4  5  6  7 ], ...                 （大核簇，具体看 SoC）
```

## 验证方法

1. 模型 runtime config 加 `"power":"high"`（`Llm::set_config` 支持，`llm_bench.cpp:1137` 即此用法），多次测量 decode 速度，方差应显著收窄。
2. logcat 过滤 `CPU Group` 确认各簇核心号与频率。

## 修复方案（已落地）

- `ModelConfig` 新增 `power` 字段（序列化键 `"power"`，取值 `high` / `normal` / `low`，默认 `normal` 保持原行为）。
- 设置页新增 Power 选择项，写入 `custom_config.json`，重启会话生效。
- 对话性能信息展示中补充 model / backend / power / MNN 版本，便于现场核对。
