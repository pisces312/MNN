// Created by pisces312 on 2026/07/27.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mnnllm.android.hexagon

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Hexagon NPU (cDSP) setup, mirrors the QNN integration in
 * [com.alibaba.mnnllm.android.qnn.QnnModule] but for bundled MNN HTP ops libs.
 *
 * - libMNN_htpops.so (AArch64 FastRPC stub) ships in jniLibs and is dlopen'ed
 *   by bare soname from libMNN.so, so the app native lib dir must be (and is)
 *   on the linker search path.
 * - libMNN_htpops_skel.so (Q6DSP skeleton) is not loaded by the Android
 *   linker; FastRPC reads it from ADSP_LIBRARY_PATH. With legacy jniLibs
 *   packaging it is extracted to applicationInfo.nativeLibraryDir, which is
 *   world-readable so adsprpcd can access it.
 *
 * Must run before any class whose static initializer calls
 * System.loadLibrary("mnnllmapp") (e.g. CrashUtil), i.e. first thing in
 * MnnLlmApplication.onCreate().
 */
object HexagonModule {
    private const val TAG = "HexagonModule"
    private const val SKEL_LIB_NAME = "libMNN_htpops_skel.so"

    @Volatile
    private var hexagonReady = false

    fun setup(context: Context): Boolean {
        if (hexagonReady) {
            return true
        }
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        if (!File(nativeLibDir, SKEL_LIB_NAME).exists()) {
            // NOTE: Log instead of Timber - setup() runs before TimberConfig.initialize()
            Log.i(TAG, "skel lib not found in $nativeLibDir, hexagon backend unavailable")
            return false
        }
        return try {
            Os.setenv(
                "ADSP_LIBRARY_PATH",
                "$nativeLibDir;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp",
                true
            )
            hexagonReady = true
            Log.i(TAG, "ADSP_LIBRARY_PATH set to $nativeLibDir")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "setenv ADSP_LIBRARY_PATH failed", t)
            false
        }
    }

    fun isReady(): Boolean = hexagonReady
}
