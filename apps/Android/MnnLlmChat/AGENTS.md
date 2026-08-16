# MnnLlmChat Project Instructions

## 构建

**单一入口：`./build.sh`（Git Bash / Windows）**，负责 native 检查 → gradle 构建 → 签名：

```
./build.sh [debug|release] [standard|googleplay] [--skip-native] [--rebuild-native]
# 默认 release standard;native 缺失时自动构建;--rebuild-native 强制 clean 重建 native
```

### 环境前提

- **Windows 侧（APK 构建）**：
  - Git Bash;Java 17;`ANDROID_HOME` 或 `ANDROID_SDK_ROOT`(SDK 内需含 `build-tools/36.0.0` 与 NDK,app 侧 CMake 用 SDK NDK 27.2.12479018)
  - release 签名需环境变量：`KEY_STORE_LOCATION` / `KEY_STORE_PASSWORD` / `KEY_ALIAS`(debug 不需要)
- **WSL 侧（native 构建，发行版名 `Ubuntu`)**:
  - Android NDK r27d **Linux 版**，默认 `/mnt/d/dev/android-ndk-r27d`（可用 `ANDROID_NDK` 覆盖）
  - QAIRT (QNN) SDK 2.39.0.250926，默认 `/mnt/d/dev/qairt/2.39.0.250926`（可用 `QNN_SDK_ROOT` 覆盖）
  - WSL 内需有 cmake / make

### 重建 native（libMNN.so）

```bash
# WSL 内:
bash /mnt/d/3rd-party-projects/MNN/build_native.sh [--clean]
# 或从 Git Bash:
MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- bash /mnt/d/3rd-party-projects/MNN/build_native.sh [--clean]
```

- 上游合并或引擎代码变更后用 `--clean` 全量重建；`./build.sh --rebuild-native` 等效
- 产物：`project/android/build_64/lib/libMNN.so`；验证：`ls -lh` 确认时间戳为新
- **重建后必须 `git add project/android/build_64/lib/libMNN.so` 提交**（构建环境复杂，无法快速复现）
- 范围说明：此流程只产 `libMNN.so`。app `jniLibs/` 里的 `libMNN_htpops*.so`、`libsherpa-mnn-jni.so` 是预编译已提交产物（htp ops 需 Hexagon SDK 单独构建），**不要**在本流程重建

### 重建 APK

```bash
./build.sh debug standard --skip-native    # 验证编译 → MnnLlmChat-*-pisces-standard-debug.apk
./build.sh release standard --skip-native  # 签名包 → MnnLlmChat-*-pisces-standard-signed.apk
```

- 产物在本目录（app 仓库根）下，版本号自动取自 `app/build.gradle` 的 `versionName`,fork tag 取自 `FORK_TAG`
- **APK 必须在 Windows 构建**(JDK/Android SDK 路径不兼容 WSL);**native 必须在 WSL 构建**(NDK/QNN SDK 是 Linux 路径)。`build.sh` 内部已通过 `wsl -d Ubuntu` 处理跨界调用，无需手工切换

## 真机部署

- 手机上的模型目录：手机存储（内部存储根目录）下的 `mnn-models` 目录（即 `/sdcard/mnn-models/`）。每个模型一个子目录，内含 `config.json`、`llm_config.json`、`tokenizer.mtok`、模型权重等文件。

## QNN (NPU) 环境速查

- 所有 QNN native 编译/转换都在 **WSL (Ubuntu)** 下进行；APK 构建在 Windows。详见 CLAUDE.md「QNN (NPU) 编译与转换环境（WSL）」一节。
- QAIRT SDK 2.39.0.250926：`/mnt/d/dev/qairt/2.39.0.250926`（Windows/WSL 共用）。
- Android libMNN.so:WSL 跑 MNN 仓库根的 `build_native.sh`(flag 集已含 `-DMNN_QNN=ON -DMNN_WITH_PLUGIN=ON -DQNN_SDK_ROOT=...`),产物在 `project/android/build_64/lib/`。**libMNN.so 必须提交**(构建环境复杂,无法快速复现)。
- x86 转换工具链：WSL `~/mnn-x86-qnn-build`（compilefornpu/MNN2QNNModel/generateIO），增量 `make compilefornpu -j8`。
- 离线转换：`bash /mnt/d/workspace/mnn-research/convert_qnn.sh <model_dir> <cache_name>`（soc 87 / v81）。
- QNN 离线模型在 app 里 **backend 选 `cpu`**（plugin 算子内部走 HTP）；`npu` 选项是在线构图入口，LLM 必崩。
- 调研文档：`D:\workspace\mnn-research\`（构建、转换、使用方式、崩溃分析）。
