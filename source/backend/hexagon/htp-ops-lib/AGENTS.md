# htp-ops-lib 本机构建说明

Hexagon DSP ops 库（`libMNN_htpops.so` / `libMNN_htpops_skel.so`）的构建在 **WSL2 (Ubuntu)** 中进行。本机使用 Hexagon SDK **v6.7.0.0**（`/home/pisces312/hexagon-sdk-v6.7.0.0`），入口脚本是 **`build_wsl.sh`**。

## 构建命令

```bash
# 从 Windows Git Bash 一次性调用（本机设备 SM8850 = v81）
MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- bash /mnt/d/3rd-party-projects/MNN/source/backend/hexagon/htp-ops-lib/build_wsl.sh v81
```

`build_wsl.sh` 打下述环境变量的默认值，一般无需手动设置：

| 变量 | 默认 | 说明 |
|---|---|---|
| `HEXAGON_SDK_ROOT` | `/home/pisces312/hexagon-sdk-v6.7.0.0` | SDK 根 |
| `ANDROID_NDK_ROOT` | `/home/pisces312/android-ndk-r27d` | 外部 NDK（6.7 **无内置 NDK**，不要用 SDK 自带路径） |
| `DEFAULT_HEXAGON_TOOLS_ROOT` | `$SDK/tools/HEXAGON_Tools/21.0.04` | Hexagon 工具链（6.7 的 tools 版本） |
| `DEFAULT_HLOS_ARCH` | `64` | 让 `build_cmake android` 产出 arm64（`android_ReleaseG_aarch64/`） |
| `CMAKE_ROOT_PATH` | `/usr` | 6.7 精简安装**无 bundled cmake**，用系统 cmake 3.28.3 |
| `HEXAGON_STRIP` | `$TOOLS/bin/hexagon-strip` | llvm-strip 21 软链，部署前 strip skel |

## 产物与部署

- `outputs/libMNN_htpops.so`（Android aarch64 stub）
- `outputs/libMNN_htpops_skel.so`（DSP skel，约 577KB，已 strip debug + 通过符号检查）

脚本自动 strip skel 并校验符号。部署到 app `jniLibs/arm64-v8a/` 时，skel 需放两份：arch 专用名 `libMNN_htpops_skelV81.so` 与 fallback 名 `libMNN_htpops_skel.so`（内容相同；host 侧按设备 arch 选名，查不到时回退 fallback）。新的 `*.so` 受 gitignore 限制需 `git add -f`。

## 注意（6.7 适配踩坑）

- **不要手动 cmake 配置 stub**：会冒 `PREBUILT_LIB_DIR` / `DSP_TYPE` 为空的错。一律走 SDK 的 `build_cmake`（preset 里变量齐全，6.7 的 android preset 默认就是 arm64-v8a）。
- **DSP 产物目录名**：6.7 是 `hexagon_ReleaseG__v81`（tool variant 为空，非 6.6 的 `hexagon_ReleaseG_toolv19_v81`）；脚本用 glob `hexagon_ReleaseG_*_$DSP_ARCH` 兼容。
- **`wsl -- bash -c '...'` 传参陷阱**：单引号内的 `$VAR` 会被调用链提前展开成空串（stripping 曾因此静默失败）；用绝对路径裸写，验证变量用 `printenv VAR`。
- `dsp_capabilities_utils.c/h` 是 SDK `utils/examples` 同名文件的本地裁剪版：额外实现 `get_hex_arch_ver()`（上游 `src/host/session.c` 需要）。上游若改 `session.c` 的 skel 选择逻辑，注意同步检查这两个文件。
- 上游若改动 `source/backend/hexagon/`，新 libMNN.so 与预编译 skel 协议会错配（症状：hexagon backend 输出垃圾 token），必须重编 htp-ops。
