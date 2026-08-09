# CLAUDE.md - MNN Chat (MnnLlmChat)

## 项目概述

阿里 MNN 团队官方的全功能多模态 LLM Android 应用（v0.8.3）。纯本地运行，不联网。

## 版本信息

| 组件 | 版本 |
|------|------|
| MNN 引擎 | **3.6.0** |
| App versionName | 0.8.3.2 (fork of upstream 0.8.3, fork #2) |
| App versionCode | 26080101 (日期式 YYMMDDNN) |
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

示例：
| 上游版本 | fork 序号 | versionName |
|---|---|---|
| 0.8.3 | 1 | 0.8.3.1 |
| 0.8.3 | 2 | 0.8.3.2 |
| 0.8.4 | 1 | 0.8.4.1 |
| 0.9.0 | 1 | 0.9.0.1 |
| 1.0.0 | 1 | 1.0.0.1 |

**区分机制**：fork 与上游的区分不依赖 versionName 本身，而是通过：
1. `BuildConfig.IS_FORK_BUILD = true`（编译期 flag）
2. `BuildConfig.FORK_TAG = "pisces"`（fork 代号）
3. versionName 段数（fork 4 段 vs 上游 3 段，自然区分）

### versionCode

格式：`YYMMDDNN`

- `YYMMDD` 发布日期（2025-06-25 → 250625）
- `NN` 当天序号（01-99）
- 天然单调递增，无需维护计数器

示例：`25062501` = 2025-06-25 第 1 次发布

### Fork 标识

`pisces` 作为 fork 代号，**不进入 versionName / versionCode**，只出现在：
- APK 文件名：`MnnLlmChat-v0.8.3.2-pisces-standard-signed.apk`
- `BuildConfig.FORK_TAG = "pisces"`（about 页 / 日志可读）
- git 分支名：`feature/pisces-xxx`
- GitHub Release 标题

### UpdateChecker 行为

fork 版本（`IS_FORK_BUILD = true`）启动时：
- `UpdateChecker.checkForUpdates()` 直接 return，不发任何网络请求
- `UpdateChecker.registerDownloadReceiver()` 不注册 DownloadReceiver
- 设置页"检查更新"按钮不可点击，仅显示版本号
- fork 自身的更新通过 GitHub Release 分发，不走 app 内更新机制

## 构建步骤

1. 先编译 MNN 引擎库：
```bash
cd project/android
mkdir build_64 && cd build_64
../build_64.sh "-DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_ARM82=true -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DLLM_SUPPORT_VISION=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true -DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF -DCMAKE_INSTALL_PREFIX=."
make install
```
2. 再编译 App：`cd apps/Android/MnnLlmChat && ./installDebug.sh`

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

## 关键 CMake 配置

- ARM82 fp16 优化
- OpenCL GPU 后端
- 低内存模式
- 权重反量化 GEMM
- Transformer 融合优化
- 视觉/音频/Diffusion 全支持

## 调试

- Stetho 集成（debug build）
- BenchmarkDumperPlugin / LoggerDumperPlugin 等调试工具
- `adb shell dumpsys activity` 查看运行时信息
- 本地 API 可通过 `http://localhost:8080` 访问（app 内嵌 Ktor server）

## QNN (NPU) 编译与转换环境（WSL）

> 目标设备：SM8850（8 Elite Gen5，QNN SoC ID=87，Hexagon v81）。
> 完整调研与踩坑记录在 `D:\workspace\mnn-research\`（`qnn-backend-build.md`、`qnn-npu-model-usage.md`、`qnn-device-crash-analysis_*.md` 等），此处只记环境事实。

### QAIRT (QNN) SDK

- 版本 2.39.0.250926，Windows/WSL 共用同一份目录：`D:\dev\qairt\2.39.0.250926` = `/mnt/d/dev/qairt/2.39.0.250926`
- 编译期只需 `include/QNN/` 头文件（运行时 dlopen）；x86 宿主工具在 `bin/x86_64-linux-clang/`，目标库在 `lib/aarch64-android/` 与 `lib/hexagon-v81/unsigned/`

### Android libMNN.so（app 用，WSL 构建）

```bash
MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- bash -c 'export ANDROID_NDK=/mnt/d/dev/android-ndk-r27d && \
  cd /mnt/d/3rd-party-projects/MNN/project/android/build_64 && \
  ../build_64.sh -DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true \
    -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_ARM82=true \
    -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DLLM_SUPPORT_VISION=true \
    -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true \
    -DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF \
    -DBUILD_PLUGIN=ON -DMNN_QNN=ON -DMNN_WITH_PLUGIN=ON \
    -DQNN_SDK_ROOT=/mnt/d/dev/qairt/2.39.0.250926 \
    -DMNN_HEXAGON=ON -DMNN_GPU_TIME_PROFILE=ON \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
    -DCMAKE_INSTALL_PREFIX=.'
```

- 产物：`project/android/build_64/lib/libMNN.so`，APK 构建直接引用
- `MNN_WITH_PLUGIN=ON` 是跑 QNN 离线模型的硬要求；`BUILD_PLUGIN=ON` 是无效变量（无 CMakeLists 声明）
- QNN 与 Hexagon 同开需 hexagon 侧 dsprpc 符号改名补丁（已在 fork 中）
- **libMNN.so 必须提交到仓库**：构建环境复杂（WSL + NDK + QNN SDK + Hexagon + dsprpc 补丁），无法在 CI 或新环境快速复现。每次重编后务必 `git add project/android/build_64/lib/libMNN.so` 一并提交。

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

### 真机运行方式

- **backend 选 `cpu`**：离线产物由注册在 CPU backend 的 plugin 算子加载执行，HTP 加速在 plugin 内部完成；`npu` 选项是在线构图入口，LLM 必崩
- 手机模型目录 `/sdcard/mnn-models/<name>/`，目录名不得含 `qnn` 子串（会触发 app 下载流程）
- 必需文件 7 个：`config.json`（含 `backend_type=cpu`、`llm_model=qnn/llm.mnn`、`chunk_limits=[128,1]`）、`llm_config.json`、`tokenizer.mtok`、`embeddings_bf16.bin`、`llm.mnn.weight`（仅存在性检查，必须推）、`qnn/llm.mnn`、`qnn/graph0.bin`
- adb：`D:/dev/android_sdk/platform-tools/adb`，push 源路径用 Windows 形式（`D:/...`，Git Bash `/d/...` 不生效）
- 抓日志：`adb logcat | grep -aE "MNNJNI|MNN_QNN|Qnn"`

## 软硬件要求

- arm64-v8a 设备（仅支持 64 位）
- Android 8.0+ (API 26)
- 旗舰级设备（官方仅在 OnePlus 13 / 小米 14 Ultra 测试）
- 16KB 页大小支持（Android 15+ 兼容）
- 需要足够存储空间下载模型（单个模型数 GB）
