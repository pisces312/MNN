# htp-ops-lib 本机构建说明

Hexagon DSP ops 库（`libMNN_htpops.so` / `libMNN_htpops_skel.so`）的构建在 **WSL2 (Ubuntu)** 中进行。

## 环境变量

`build.sh` 支持以下环境变量（2026-08 上游重构后不再 hardcode 路径）：

| 变量 | 本机值 / 默认 | 说明 |
|---|---|---|
| `HTP_OPS_SDK_ENV` | `/home/pisces312/hexagon-sdk-v6.6.0.0/setup_sdk_env.source` | Hexagon SDK 环境脚本路径。**已写入 WSL `~/.bashrc` 顶部**（在非交互守卫之前），交互 shell 自动生效 |
| `HTP_OPS_CMAKE_ROOT` | 空 | 需要覆盖 cmake 路径时设置（映射为 `CMAKE_ROOT_PATH`） |
| `HTP_OPS_BUILD_ANDROID` | `1` | 是否同时构建 Android 侧 `libMNN_htpops.so`；设 `0` 跳过 |
| `HTP_OPS_PWL_VARIANT` | `learned8` | FP16 activation PWL 变体 |
| `HTP_OPS_USE_MAKE` | `0` | 设 `1` 时用 `-gMake` 生成 Makefile 构建 |
| `HEXAGON_STRIP` | `$DEFAULT_HEXAGON_TOOLS_ROOT/Tools/bin/hexagon-strip` | strip 工具路径覆盖 |
| `HEXAGON_NM` | 自动探测 `llvm-nm` / `hexagon-nm` | nm 工具路径覆盖（新版 SDK 只有 `llvm-nm`，见 `check_so_symbols.sh`） |

## 构建命令（WSL 内）

```bash
cd <repo>/source/backend/hexagon/htp-ops-lib
./build.sh v69   # 参数为 DSP_ARCH，如 v66/v68/v69/v73/v75，产物在 outputs/
```

产物：
- `outputs/libMNN_htpops.so`（Android aarch64 侧）
- `outputs/libMNN_htpops_skel.so`（DSP skel 侧，已 strip debug，并通过 `check_so_symbols.sh` 未定义符号检查）

## 注意

- 通过 `wsl -- bash -c` 非交互调用时 `~/.bashrc` 不会被读取，`HTP_OPS_SDK_ENV` 需显式传入或先 `source ~/.bashrc`。
- 从 Git Bash 经 `wsl -- bash -c '...'` 传命令时，单引号内的 `$VAR` 会被调用链提前展开（变成空串）；验证变量请用 `printenv VAR`，不要 `echo $VAR`。
- 本目录的 `dsp_capabilities_utils.c/h` 是 SDK `utils/examples` 同名文件的本地裁剪版（SDK 6.6 修正）：额外实现了 `get_hex_arch_ver()`（上游 `src/host/session.c` 需要，SDK 6.6 未提供，经 `remote_handle_control(DSPRPC_GET_DSP_INFO, ARCH_VER)` 查询）。上游若再改 `session.c` 的 skel 选择逻辑，注意同步检查这两个文件。
- 本机设备 SM8850 = v81，构建参数用 `./build.sh v81`；部署时 skel 需以 `libMNN_htpops_skelV81.so` 和 `libMNN_htpops_skel.so`（fallback）两份放入 app `jniLibs/arm64-v8a/`，新文件受 `*.so` gitignore 限制需 `git add -f`。
