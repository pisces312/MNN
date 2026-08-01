// htp_info.c — standalone Hexagon DSP diagnostics via the htp_ops_getDiag RPC.
// Returns DSP-side info (HVX units, VTCM, power vote RCs, worker pool state)
// via QAIC rout scalars — no fd/mmap needed (works around fastrpc_mmap not
// being visible to HAP_mmap_get in unsigned PD on retail devices).
//
// Build (WSL): $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang htp_info.c -o htp_info -ldl -llog
// Run (device): LD_LIBRARY_PATH=. ADSP_LIBRARY_PATH="..." ./htp_info
#include <android/log.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define CDSP_DOMAIN_ID 3
#define LOGTAG "htp_info_step"

#define STEP(...)                                                       \
    do {                                                                \
        __android_log_print(ANDROID_LOG_INFO, LOGTAG, __VA_ARGS__);     \
        printf(__VA_ARGS__);                                            \
        printf("\n");                                                   \
        fflush(stdout);                                                 \
    } while (0)

typedef int (*open_session_fn)(int, int);
typedef int (*init_backend_fn)(void);
typedef void (*close_session_fn)(void);
typedef int (*get_diag_fn)(int *, int *, int *, int *, int *, int *,
                           int *, int *, int *, int *, int *, int *);

static void *must_dlsym(void *h, const char *name) {
    void *p = dlsym(h, name);
    if (!p) STEP("dlsym(%s) failed: %s", name, dlerror());
    return p;
}

int main(void) {
    STEP("step0: main entered");

    // libcdsprpc (NOT libadsprpc): the stub NEEDs libcdsprpc.so; loading
    // libadsprpc first creates a fatal 2nd xdsprpc instance (exit(1)).
    void *rpc = dlopen("/vendor/lib64/libcdsprpc.so", RTLD_NOW | RTLD_GLOBAL);
    if (!rpc) rpc = dlopen("libcdsprpc.so", RTLD_NOW | RTLD_GLOBAL);
    if (!rpc) { STEP("dlopen libcdsprpc.so failed: %s", dlerror()); return 1; }
    STEP("step1: libcdsprpc dlopen ok");

    void *stub = dlopen("libMNN_htpops.so", RTLD_NOW);
    if (!stub) { STEP("dlopen libMNN_htpops.so failed: %s", dlerror()); return 1; }
    STEP("step2: libMNN_htpops dlopen ok");

    open_session_fn open_dsp_session = (open_session_fn)must_dlsym(stub, "open_dsp_session");
    init_backend_fn init_htp_backend = (init_backend_fn)must_dlsym(stub, "init_htp_backend");
    close_session_fn close_dsp_session = (close_session_fn)must_dlsym(stub, "close_dsp_session");
    get_diag_fn rpc_getDiag = (get_diag_fn)must_dlsym(stub, "htp_ops_rpc_getDiag");
    if (!open_dsp_session || !init_htp_backend || !rpc_getDiag) {
        STEP("step3: required dlsym missing, abort");
        return 1;
    }
    STEP("step3: all dlsym ok");

    int osrc = open_dsp_session(CDSP_DOMAIN_ID, 1);
    STEP("step4: open_dsp_session rc=%d", osrc);
    if (osrc != 0) return 1;

    int ibrc = init_htp_backend();
    STEP("step5: init_htp_backend rc=%d", ibrc);

    int hvx_units, vtcm_size, hvx_arch;
    int rc_apptype, rc_dcvs_v3, rc_bus_prot, rc_ddr_perf, rc_hvx, rc_hmx;
    int prot_compiled, max_workers, pool_ok;
    hvx_units = vtcm_size = hvx_arch = -1;
    rc_apptype = rc_dcvs_v3 = rc_bus_prot = rc_ddr_perf = rc_hvx = rc_hmx = -1;
    prot_compiled = max_workers = pool_ok = -1;

    int err = rpc_getDiag(&hvx_units, &vtcm_size, &hvx_arch,
                          &rc_apptype, &rc_dcvs_v3, &rc_bus_prot,
                          &rc_ddr_perf, &rc_hvx, &rc_hmx,
                          &prot_compiled, &max_workers, &pool_ok);
    STEP("step6: getDiag rc=%d (0x%x)", err, err);

    printf("\n=== Hexagon DSP Diagnostic Data ===\n");
    printf("hvx_units (qurt_hvx_get_units>>8) = %d (0x%x)\n", hvx_units, hvx_units);
    printf("vtcm_size (bytes)                  = %d (0x%x)\n", vtcm_size, vtcm_size);
    printf("hvx_arch (__HVX_ARCH__)            = %d (0x%x)\n", hvx_arch, hvx_arch);
    printf("\n--- Power vote return codes (0=success, 0x4e/78=EBADPERMS, 0x14/20=EUNSUPPORTED) ---\n");
    printf("rc apptype       = %d (0x%x)\n", rc_apptype, rc_apptype);
    printf("rc dcvs_v3       = %d (0x%x)\n", rc_dcvs_v3, rc_dcvs_v3);
    printf("rc bus_protected = %d (0x%x)\n", rc_bus_prot, rc_bus_prot);
    printf("rc ddr_perf      = %d (0x%x)\n", rc_ddr_perf, rc_ddr_perf);
    printf("rc hvx           = %d (0x%x)\n", rc_hvx, rc_hvx);
    printf("rc hmx           = %d (0x%x)\n", rc_hmx, rc_hmx);
    printf("protected_compiled = %d\n", prot_compiled);
    printf("\n--- Worker pool ---\n");
    printf("g_max_num_workers = %d\n", max_workers);
    printf("worker_pool_ok    = %d\n", pool_ok);
    printf("=== end ===\n");
    fflush(stdout);

    if (close_dsp_session) close_dsp_session();
    STEP("step7: clean exit");
    return 0;
}
