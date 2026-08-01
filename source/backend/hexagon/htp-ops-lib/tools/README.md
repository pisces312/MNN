# htp-ops-lib tools

## htp_info

Standalone Hexagon DSP diagnostics tool. Queries DSP-side state through the
`getDiag` RPC (see `include/htp_ops.idl`): HVX units, VTCM size, HVX arch,
power vote return codes, and worker pool state.

Unlike the `getInfo` RPC (fd/mmap based), `getDiag` returns data via QAIC
`rout` scalars, so it also works on retail devices where `fastrpc_mmap`
mappings are not visible to `HAP_mmap_get` in the unsigned PD — and where
FARF logging is unavailable (no `adspmsgd`).

### Build (WSL / Linux, NDK)

```bash
$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang \
    htp_info.c -o htp_info -ldl -llog
```

### Run (on device)

Push next to the htp-ops stub/skel and model directory, e.g.
`/data/local/tmp/MNN/`:

```bash
adb push htp_info /data/local/tmp/MNN/
adb shell 'cd /data/local/tmp/MNN && chmod 755 htp_info && \
    export LD_LIBRARY_PATH=. && \
    export ADSP_LIBRARY_PATH="/data/local/tmp/MNN;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp" && \
    ./htp_info'
```

Notes:

- Requires `libMNN_htpops.so` (stub) built from the same htp-ops-lib source
  with the `getDiag` method, and `libMNN_htpops_skel.so` matching the device
  DSP arch (e.g. v81).
- Loads `libcdsprpc.so` only — do NOT preload `libadsprpc.so`, two xdsprpc
  instances in one process are fatal (silent `exit(1)`).
- Progress is also logged to logcat under tag `htp_info_step`.
