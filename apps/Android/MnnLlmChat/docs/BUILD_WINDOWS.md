# MNN Chat Windows 构建记录

> 日期: 2026-06-19 | 主机: PISCES312 (ThinkBook 16 G7 IAH)

## 环境

| 工具 | 路径/版本 |
|------|-----------|
| CMake | 3.22.1 (Android SDK) |
| Ninja | 1.10.x (Android SDK) |
| NDK | 27.0.12077973 (`D:\dev\android_sdk\ndk\27.0.12077973`) |
| Gradle | 8.9 (腾讯云镜像下载) |
| AGP | 8.7.3 |
| Kotlin | 2.1.21 |
| JDK | 21 (Android Studio JBR) |

## 构建流程

### 第一阶段：编译 MNN 引擎 (libMNN.so)

```
源文: build_mnn_win.ps1
```

**关键步骤：**

1. 尝试 WSL2 编译 → **失败**：NDK 是 Windows 版，WSL 无法执行 Windows 二进制文件
2. 改用 Windows 原生 CMake + Ninja
3. CMake 配置：816 个目标，Release + arm64-v8a
4. 选项：LLM + Vision + Audio + Diffusion + OpenCL + ARM82 + OpenCV
5. 编译时间：约 22 分钟
6. 产物：`project/android/build_64/lib/libMNN.so` (7.33 MB)

**注意：** `LLM_SUPPORT_AUDIO` / `LLM_SUPPORT_VISION` / `MNN_CPU_WEIGHT_DEQUANT_GEMM` 三个 CMake 选项在 MNN 3.6 中未被识别（已移除/改名），不影响编译。

### 第二阶段：编译 APK

```
源文: apps/Android/MnnLlmChat/
```

APK 通过 CMakeLists.txt 直接链接第一阶段产出的 `libMNN.so`：

```cmake
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${LIB_PATH}/libMNN.so")
```

**无需重新编译 MNN 引擎。**

## 踩坑记录

### 1. NDK 版本不匹配

- 原项目要求 `ndkVersion "27.2.12479018"`
- 本地仅有 `27.0.12077973`
- **解决**：修改两处 `build.gradle` 的 ndkVersion：
  - `app/build.gradle`
  - `apps/frameworks/mnn_tts/android/build.gradle`

### 2. WSL vs Windows NDK

- `build_64.sh` 是 bash 脚本，但本地 NDK 是 Windows 版
- WSL 内的 Clang 无法使用 Windows 版 NDK 的 toolchain
- **解决**：写 PowerShell 脚本，用 Windows 版 CMake + Ninja 直接编译

### 3. Gradle 下载被墙

- `services.gradle.org` 和 GitHub Releases 均被阻断（TLS 握手失败）
- **解决**：腾讯云镜像 `mirrors.cloud.tencent.com/gradle/` 下载，修改 `gradle-wrapper.properties` 指向本地 zip

### 4. OpenCL 编译警告

- `MmapPool.cpp` 和 `OpenCLBackend.cpp` 有 `%d` vs `%zu` format 警告（36 处）
- **不影响编译**，仅警告

### 5. Kotlin 弃用警告

- `PreferenceManager`、`startActivityForResult`、`imageUri` 等 API 已弃用
- compileSdk 35 下正常，仅警告

## 产物

| 文件 | 大小 | 路径 |
|------|------|------|
| libMNN.so | 7.33 MB | `project/android/build_64/lib/` |
| APK (standard debug) | 41.86 MB | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` |
| Sherpa MNN JNI | 自动下载 | `app/src/main/jniLibs/arm64-v8a/libsherpa-mnn-jni.so` |

## 快速重建

```powershell
# 1. 编译 MNN 引擎（仅首次或引擎改动后需要）
powershell -File build_mnn_win.ps1

# 2. 编译 APK
$env:ANDROID_HOME="D:\dev\android_sdk"
$env:ANDROID_SDK_ROOT="D:\dev\android_sdk"
$env:GRADLE_USER_HOME="D:\dev\gradle-home"
cd apps\Android\MnnLlmChat
.\gradlew assembleStandardDebug --no-daemon
```
