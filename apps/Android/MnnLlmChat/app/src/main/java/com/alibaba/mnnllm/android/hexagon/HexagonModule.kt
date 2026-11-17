// Created by pisces312 on 2026/07/27.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mnnllm.android.hexagon

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Runtime setup for the MNN Hexagon (HTP/cDSP) backend, with bundled HTP ops libs.
 *
 * The backend is selected per-model through the same backend_type path as cpu/opencl, and is
 * mutually exclusive with the QNN model path. libMNN.so dlopen()s the FastRPC stub
 * (libMNN_htpops.so) by soname; FastRPC then loads the DSP skeleton from ADSP_LIBRARY_PATH. Both ship
 * in the app native library dir. Set the search paths before any DSP session opens.
 */
object HexagonModule {
    private const val TAG = "HexagonModule"
    private const val STUB_LIB = "libMNN_htpops.so"
    private const val SKEL_LIB_NAME = "libMNN_htpops_skel.so"

    @Volatile
    private var prepared = false

    // SOC_MODEL -> Hexagon arch. HTP needs FP16 HMX, i.e. Hexagon v73+ (8 Gen2+).
    private val sSupportedSoc = hashMapOf<String, String>().apply {
        put("SM8850", "V81")
        put("SM8750", "V79")
        put("SM8650", "V75")
        put("SM8550", "V73")
    }

    private fun getSocModel(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            null
        }
    }

    private fun nativeLibDir(context: Context): String = context.applicationInfo.nativeLibraryDir

    /**
     * Whether the Hexagon backend can be offered: supported SoC, FastRPC stub and DSP skeleton all
     * packaged. QNN models never reach this path.
     */
    fun deviceSupported(context: Context): Boolean {
        val soc = getSocModel()
        if (soc == null || !sSupportedSoc.containsKey(soc)) {
            return false
        }
        val dir = nativeLibDir(context)
        if (!File(dir, STUB_LIB).exists() || !File(dir, SKEL_LIB_NAME).exists()) {
            Log.w(TAG, "SoC $soc supported but hexagon libs not packaged, Hexagon disabled")
            return false
        }
        return true
    }

    /**
     * Point FastRPC at the packaged stub/skel. Safe to call more than once; only takes effect the
     * first time. Must run before the model that uses the hexagon backend is loaded.
     */
    fun prepare(context: Context): Boolean {
        if (prepared) {
            return true
        }
        val path = nativeLibDir(context)
        return try {
            // Log instead of Timber: setup() runs before TimberConfig.initialize().
            Os.setenv("ADSP_LIBRARY_PATH", "$path;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp", true)
            Os.setenv("LD_LIBRARY_PATH", path, true)
            prepared = true
            Log.i(TAG, "Hexagon lib path ready: $path (soc=${getSocModel()})")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Hexagon prepare setenv failed", e)
            false
        }
    }

    // Called from MnnLlmApplication.onCreate, before Timber init.
    fun setup(context: Context): Boolean = prepare(context)

    fun isReady(): Boolean = prepared
}
