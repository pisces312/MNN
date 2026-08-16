# MnnLlmChat Project Instructions

阿里 MNN 官方全功能多模态 LLM Android 应用的 fork（pisces312）。纯本地推理，不联网。

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
- 范围说明：此流程只产 `libMNN.so`。`libsherpa-mnn-jni.so`、`libQnnHtp*.so` 等是预编译已提交产物，**不要**在本流程重建

### 重建 htp-ops 库（libMNN_htpops*.so，仅上游 hexagon 变更后需要）

- **何时需要**：上游合并触及 `source/backend/hexagon/`（尤其 htp-ops-lib 或 DSP 命令协议）后，新 libMNN.so 与预编译 skel 协议会错配——症状是 hexagon backend 输出垃圾 token（如全是 8）。普通引擎变更不需要重编。2026-08 上游 `af19bb57`（vrmpy GEMV）即一例
- **环境**：WSL + Hexagon SDK v6.6.0.0（`HTP_OPS_SDK_ENV` 已写入 WSL `~/.bashrc`；非交互 `wsl -- bash -c` 调用需先 `source ~/.bashrc`）。详见 `source/backend/hexagon/htp-ops-lib/AGENTS.md`
- **命令**（WSL）：`cd source/backend/hexagon/htp-ops-lib && ./build.sh v81`（本机设备 SM8850 = v81）
- **部署**：`outputs/` 下 `libMNN_htpops.so`、`libMNN_htpops_skelV81.so` 拷入 `app/src/main/jniLibs/arm64-v8a/`；skel 需同时放两份——arch 专用名 `libMNN_htpops_skelV81.so` 与 fallback 名 `libMNN_htpops_skel.so`（内容相同；host 侧按设备 arch 选名，查不到时回退 fallback 名）
- 重建后随 jniLibs 一并 `git add` 提交（htp-ops 构建需 Hexagon SDK，同样无法快速复现）

### 重建 APK

```bash
./build.sh debug standard --skip-native    # 验证编译 → MnnLlmChat-*-pisces-standard-debug.apk
./build.sh release standard --skip-native  # 签名包 → MnnLlmChat-*-pisces-standard-signed.apk
```

- 产物在本目录（app 仓库根）下，版本号自动取自 `app/build.gradle` 的 `versionName`,fork tag 取自 `FORK_TAG`
- **APK 必须在 Windows 构建**(JDK/Android SDK 路径不兼容 WSL);**native 必须在 WSL 构建**(NDK/QNN SDK 是 Linux 路径)。`build.sh` 内部已通过 `wsl -d Ubuntu` 处理跨界调用，无需手工切换
- **上游合并后的完整 checklist**:① `build_native.sh --clean` 重建 libMNN.so;② 检查合并是否触及 `source/backend/hexagon/`，是则重编 htp-ops 库（见上节）;③ `./build.sh` 出 APK

## 版本信息

| 组件 | 版本 |
|------|------|
| MNN 引擎 | **3.6.0** |
| App versionName | 0.8.3.4 (fork of upstream 0.8.3) |
| App versionCode | 26081701 (日期式 YYMMDDNN) |
| Gradle | 8.9 |
| AGP | 8.7.3 |
| Kotlin | 2.1.21 |
| NDK | 27.2.12479018 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |

## 版本号规则（fork 专用）

本仓库是 MNN 官方 `apps/Android/MnnLlmChat` 的 fork，版本号需与官方结构隔离。

### versionName

格式：`MAJOR.MINOR.PATCH.FORK_INC`

- `MAJOR.MINOR.PATCH` 对齐 MNN 上游版本（如上游 0.8.3）
- 第 4 段 `FORK_INC` 是 fork 增量号，从 1 开始，每次 fork 发布 +1
- 上游版本升级时 `FORK_INC` 归零
- 全部纯数字，禁止任何非数字字符（避免 `UpdateChecker.isNewerVersion()` 解析崩溃）
- fork 版本号永远是 4 段，上游最多 3 段，段数即可区分

### versionCode

格式：`YYMMDDNN`（`YYMMDD` 发布日期 + `NN` 当天序号 01-99），天然单调递增。

### Fork 标识

`pisces` 作为 fork 代号，**不进入 versionName / versionCode**，只出现在：
- APK 文件名：`MnnLlmChat-v0.8.3.3-pisces-standard-signed.apk`
- `BuildConfig.FORK_TAG = "pisces"`（about 页 / 日志可读）
- git 分支名：`feature/pisces-xxx`
- GitHub Release 标题

**区分机制**：fork 与上游的区分通过 `BuildConfig.IS_FORK_BUILD = true`、`FORK_TAG`、versionName 段数三者实现。

### UpdateChecker 行为

fork 版本（`IS_FORK_BUILD = true`）启动时：
- `UpdateChecker.checkForUpdates()` 直接 return，不发任何网络请求
- `UpdateChecker.registerDownloadReceiver()` 不注册 DownloadReceiver
- 设置页"检查更新"按钮不可点击，仅显示版本号
- fork 自身的更新通过 GitHub Release 分发，不走 app 内更新机制

## 仓库结构

```
apps/Android/MnnLlmChat/
├── app/                          # 主 App 模块
│   ├── src/main/cpp/             # JNI 本地层
│   │   ├── llm_mnn_jni.cpp       # LLM JNI 桥接
│   │   ├── llm_session.cpp/h     # LLM 推理会话
│   │   ├── mnn_wrapper_jni.cpp   # MNN 通用 JNI 包装
│   │   ├── diffusion_jni.cpp     # 扩散模型（文生图）
│   │   ├── diffusion_session.cpp # Diffusion 推理
│   │   ├── sana_jni.cpp          # Sana 模型
│   │   ├── video/                # 视频解码（MediaCodec 硬解）
│   │   └── include/audio/        # 音频处理头文件
│   ├── src/main/java/com/alibaba/mnnllm/android/
│   │   ├── chat/                 # 聊天 UI（输入、消息列表、语音）
│   │   ├── llm/                  # LLM 管理（加载/卸载/推理）
│   │   ├── model/                # 模型信息/配置
│   │   ├── download/             # 模型下载管理
│   │   ├── history/              # 聊天历史持久化
│   │   ├── asr/                  # 语音识别
│   │   ├── audio/                # TTS 音频播放
│   │   ├── benchmark/            # 性能测试
│   │   ├── modelmarket/          # 模型市场
│   │   └── modelsettings/        # 模型参数设置
│   └── src/main/java/com/alibaba/mnnllm/api/openai/
│       └── network/              # 本地 OpenAI 兼容 API 服务（Ktor HTTP Server）
├── mnn_tts/ → ../../frameworks/mnn_tts/android  # TTS 模块
├── model_downloader/ → ../../frameworks/model_downloader/android  # 下载模块
└── scripts/                      # 构建/发布脚本
```

## 核心技术栈

- **本地推理引擎**: 链接 libMNN.so（需先编译 MNN）
- **音频**: Sherpa MNN JNI (`libsherpa-mnn-jni.so`)，支持 16K 采样率
- **视频输入**: MediaCodec 硬解 + ByteBuffer 处理
- **本地 API 服务**: Ktor 3.1.3 启动本地 HTTP Server，暴露 OpenAI 兼容接口
- **Markdown 渲染**: Markwon (fork v4.6.2-mnnchat.1)
- **相机**: CameraX 1.4.2
- **序列化**: kotlinx.serialization
- **依赖仓库**: Maven Central + Google + JitPack

## 构建 Flavor

| Flavor | 说明 |
|--------|------|
| standard | 标准版（默认） |
| googleplay | Google Play 版（含 Firebase + GMS） |

控制：`-PENABLE_FIREBASE=true` + `google-services.json`

## 支持的模型

Qwen、Gemma（含 Gemma 4 E2B/E4B）、Llama（TinyLlama、MobileLLM）、Baichuan、Yi、DeepSeek、InternLM、Phi、ReaderLM、SmoLM、LFM 系列

## 功能

- 文本对话（text-to-text）
- 图生文（image-to-text）
- 语音转文字（audio-to-text，ASR）
- 文生图（text-to-image，Diffusion + Sana）
- 视频输入（video input）
- 本地 OpenAI 兼容 API（可在 PC 通过 API 访问手机上的模型）
- 模型市场（浏览/下载/管理模型）
- 聊天历史（侧边栏）
- 性能基准测试

## 真机部署

- 手机上的模型目录：手机存储（内部存储根目录）下的 `mnn-models` 目录（即 `/sdcard/mnn-models/`）。每个模型一个子目录，内含 `config.json`、`llm_config.json`、`tokenizer.mtok`、模型权重等文件。
- adb：`D:/dev/android_sdk/platform-tools/adb`，push 源路径用 Windows 形式（`D:/...`，Git Bash `/d/...` 不生效）
- 抓日志：`adb logcat | grep -aE "MNNJNI|MNN_QNN|Qnn"`

## QNN (NPU) 编译与转换环境（WSL）

> 目标设备：SM8850（8 Elite Gen5，QNN SoC ID=87，Hexagon v81）。
> 完整调研与踩坑记录在 `D:\workspace\mnn-research\`（`qnn-backend-build.md`、`qnn-npu-model-usage.md`、`qnn-device-crash-analysis_*.md` 等），此处只记环境事实。

### QAIRT (QNN) SDK

- 版本 2.39.0.250926，Windows/WSL 共用同一份目录：`D:\dev\qairt\2.39.0.250926` = `/mnt/d/dev/qairt/2.39.0.250926`
- 编译期只需 `include/QNN/` 头文件（运行时 dlopen）；x86 宿主工具在 `bin/x86_64-linux-clang/`，目标库在 `lib/aarch64-android/` 与 `lib/hexagon-v81/unsigned/`

### Android libMNN.so 的 QNN 要点

构建命令见上文「构建 → 重建 native」。QNN 特有事实：

- `MNN_WITH_PLUGIN=ON` 是跑 QNN 离线模型的硬要求（`build_native.sh` 已含）；`BUILD_PLUGIN=ON` 是无效变量（无 CMakeLists 声明）
- QNN 与 Hexagon 同开需 hexagon 侧 dsprpc 符号改名补丁（已在 fork 中）

### x86 转换工具链（WSL `~/mnn-x86-qnn-build`）

- 构建脚本：`D:\workspace\mnn-research\build_x86_qnn_tools.sh`；关键 flags：`-DMNN_QNN=ON -DMNN_QNN_CONVERT_MODE=ON -DMNN_WITH_PLUGIN=OFF -DMNN_BUILD_TOOLS=ON -DMNN_BUILD_LLM=ON`
- 产物工具：`generateIO`、`compilefornpu`、`MNN2QNNModel`；增量重编：`cd ~/mnn-x86-qnn-build && make compilefornpu -j8`
- 运行需：`QNN_SDK_ROOT=/mnt/d/dev/qairt/2.39.0.250926`、`LD_LIBRARY_PATH=$QNN_SDK_ROOT/lib/x86_64-linux-clang`、`PATH` 前置 `~/qnn_toolchain_shim`（qnn-model-lib-generator 的 clang++→g++ shim）

### 模型导出（llmexport.py，WSL conda env `mnnexport`）

- env：`~/miniconda3/envs/mnnexport`（torch 2.13.0+cpu + transformers 5.14.1 + datasets）
- NPU 导出必须选项：`--generate_for_npu --seperate_embed --sym --disable_transformer_c4 --quant_bit 4 --quant_block 64 --act_bit=16 --omni --hqq --calib_data <本地语料>`
  - `--disable_transformer_c4` 官方文档未提但必须（C4 布局/FusedRoPE 不被 QNNAttention 接受）
  - omni 校准需 llmexport.py 的 `model.float()` 补丁（transformers 5.x bf16 dtype 冲突，已在 fork）
- 校准语料：`D:\workspace\mnn-research\calib_prompts.txt`（≥128 行）

### QNN 离线转换

```bash
# WSL 内执行
bash /mnt/d/workspace/mnn-research/convert_qnn.sh /mnt/d/models/<model_dir> <cache_name>
```

- 封装 `generate_llm_qnn.py --soc_id 87 --dsp_arch v81 --chunk_size 128 --max_history_token 2048 --vtcm_mb 8`
- `max_history_token`（KVCACHE_SIZE_LIMIT）必须 >0，否则走子图切分路径（已验证崩溃）
- 产物：模型目录下 `qnn/llm.mnn`（plugin op，~1.2KB）+ `qnn/graph0.bin`（HTP context，~338MB）+ `config_qnn.json`

### QNN 真机运行方式

- **backend 选 `cpu`**：离线产物由注册在 CPU backend 的 plugin 算子加载执行，HTP 加速在 plugin 内部完成；`npu` 选项是在线构图入口，LLM 必崩
- 手机模型目录 `/sdcard/mnn-models/<name>/`，目录名不得含 `qnn` 子串（会触发 app 下载流程）
- 必需文件 7 个：`config.json`（含 `backend_type=cpu`、`llm_model=qnn/llm.mnn`、`chunk_limits=[128,1]`）、`llm_config.json`、`tokenizer.mtok`、`embeddings_bf16.bin`、`llm.mnn.weight`（仅存在性检查，必须推）、`qnn/llm.mnn`、`qnn/graph0.bin`

## 调试

- Stetho 集成（debug build）
- BenchmarkDumperPlugin / LoggerDumperPlugin 等调试工具
- `adb shell dumpsys activity` 查看运行时信息
- 本地 API 可通过 `http://localhost:8080` 访问（app 内嵌 Ktor server）

## 软硬件要求

- arm64-v8a 设备（仅支持 64 位）
- Android 8.0+ (API 26)
- 旗舰级设备（官方仅在 OnePlus 13 / 小米 14 Ultra 测试）
- 16KB 页大小支持（Android 15+ 兼容）
- 需要足够存储空间下载模型（单个模型数 GB）
