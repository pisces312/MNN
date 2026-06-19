# CLAUDE.md - MNN Chat (MnnLlmChat)

## 项目概述

阿里 MNN 团队官方的全功能多模态 LLM Android 应用（v0.8.3）。纯本地运行，不联网。

## 版本信息

| 组件 | 版本 |
|------|------|
| MNN 引擎 | **3.6.0** |
| App versionName | 0.8.3 (versionCode 830) |
| Gradle | 8.9 |
| AGP | 8.7.3 |
| Kotlin | 2.1.21 |
| NDK | 27.2.12479018 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |

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

## 软硬件要求

- arm64-v8a 设备（仅支持 64 位）
- Android 8.0+ (API 26)
- 旗舰级设备（官方仅在 OnePlus 13 / 小米 14 Ultra 测试）
- 16KB 页大小支持（Android 15+ 兼容）
- 需要足够存储空间下载模型（单个模型数 GB）
