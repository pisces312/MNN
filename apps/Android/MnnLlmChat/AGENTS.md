# MnnLlmChat Project Instructions

## 构建

- **APK 构建必须在 Windows 下进行**（不能在 WSL 里，因为 JDK/Android SDK 路径不兼容）。使用 `.\gradlew.bat assembleStandardDebug` 或 `assembleStandardRelease`。
- native 库（libMNN.so）的构建在 WSL 里用 build_64.sh，产物放在 `project/android/build_64/lib/`。APK 构建时直接引用。

## 真机部署

- 手机上的模型目录：手机存储（内部存储根目录）下的 `mnn-models` 目录（即 `/sdcard/mnn-models/`）。每个模型一个子目录，内含 `config.json`、`llm_config.json`、`tokenizer.mtok`、模型权重等文件。

## QNN (NPU) 环境速查

- 所有 QNN native 编译/转换都在 **WSL (Ubuntu)** 下进行；APK 构建在 Windows。详见 CLAUDE.md「QNN (NPU) 编译与转换环境（WSL）」一节。
- QAIRT SDK 2.39.0.250926：`/mnt/d/dev/qairt/2.39.0.250926`（Windows/WSL 共用）。
- Android libMNN.so：WSL 跑 `project/android/build_64.sh`，需 `-DMNN_QNN=ON -DMNN_WITH_PLUGIN=ON -DQNN_SDK_ROOT=...`，产物在 `project/android/build_64/lib/`。
- x86 转换工具链：WSL `~/mnn-x86-qnn-build`（compilefornpu/MNN2QNNModel/generateIO），增量 `make compilefornpu -j8`。
- 离线转换：`bash /mnt/d/workspace/mnn-research/convert_qnn.sh <model_dir> <cache_name>`（soc 87 / v81）。
- QNN 离线模型在 app 里 **backend 选 `cpu`**（plugin 算子内部走 HTP）；`npu` 选项是在线构图入口，LLM 必崩。
- 调研文档：`D:\workspace\mnn-research\`（构建、转换、使用方式、崩溃分析）。
