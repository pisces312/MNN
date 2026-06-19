// Created by ruoyi.sjd on 2025/2/28.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.alibaba.mnnllm.android.mainsettings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.alibaba.mls.api.download.ModelDownloadManager
import com.alibaba.mls.api.source.ModelSources
import com.alibaba.mnnllm.android.MNN
import com.alibaba.mnnllm.android.MnnLlmApplication
import com.alibaba.mnnllm.android.R
import com.alibaba.mnnllm.android.databinding.FragmentMainSettingsBinding
import com.alibaba.mnnllm.android.debug.DebugActivity
import com.alibaba.mnnllm.android.modelmarket.ModelRepository
import com.alibaba.mnnllm.android.privacy.PrivacyPolicyManager
import com.alibaba.mnnllm.android.update.UpdateChecker
import com.alibaba.mnnllm.android.utils.AppLogger
import com.alibaba.mnnllm.android.utils.AppUtils
import com.alibaba.mnnllm.api.openai.manager.ApiServiceManager
import com.alibaba.mnnllm.api.openai.service.ApiServerConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MainSettingsFragment : Fragment() {

    companion object {
        const val TAG = "MainSettingsFragment"
        private const val DEBUG_CLICK_COUNT = 5
        private const val DEBUG_CLICK_TIMEOUT = 3000L // 3 seconds
    }

    private var _binding: FragmentMainSettingsBinding? = null
    private val binding get() = _binding!!

    private var updateChecker: UpdateChecker? = null
    private var debugClickCount = 0
    private val debugClickHandler = Handler(Looper.getMainLooper())
    private var debugClickRunnable: Runnable? = null
    private var updateCheckRunnable: Runnable? = null

    private var suppressCrashDiagnosticsCallback = false
    private var crashDiagnosticsToggleInProgress = false
    private var crashDiagnosticsDialogShowing = false

    // SAF directory picker for model storage path
    private val storageDirPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { handleStorageTreeUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSettings()
    }

    override fun onResume() {
        super.onResume()
        updateChecker?.checkForUpdates(requireContext(), false)
    }

    private fun setupSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        binding.itemStopDownload.isChecked = sharedPreferences.getBoolean("stop_download_on_chat", true)
        binding.itemStopDownload.setOnCheckedChangeListener { isChecked ->
            sharedPreferences.edit().putBoolean("stop_download_on_chat", isChecked).apply()
        }

        setupDownloadProvider(sharedPreferences)
        setupVoiceModelManagement()
        setupStorageManagement()

        binding.itemEnableApi.isChecked = MainSettings.isApiServiceEnabled(requireContext())
        binding.itemEnableApi.setOnCheckedChangeListener { isChecked ->
            sharedPreferences.edit().putBoolean("enable_api_service", isChecked).apply()
        }

        setupCrashDiagnostics()
        setupResetApiConfig()
        setupUpdateAndVersion()
        setupDebugMode(sharedPreferences)
    }

    private fun setupDownloadProvider(
        sharedPreferences: android.content.SharedPreferences
    ) {
        val providers = listOf(
            ModelSources.sourceHuffingFace,
            ModelSources.sourceModelScope,
            "Modelers"
        )

        fun providerLabel(provider: String): String {
            return when (provider) {
                ModelSources.sourceHuffingFace -> provider
                ModelSources.sourceModelScope -> getString(R.string.modelscope)
                else -> getString(R.string.modelers)
            }
        }

        val defaultProvider = MainSettings.getDownloadProviderString(requireContext())
        if (!sharedPreferences.contains("download_provider")) {
            sharedPreferences.edit().putString("download_provider", defaultProvider).apply()
        }
        val currentProvider = MainSettings.getDownloadProviderString(requireContext())

        binding.dropdownDownloadProvider.setDropDownItems(
            providers,
            itemToString = { providerLabel(it as String) },
            onDropdownItemSelected = { _, selected ->
                val provider = selected as String
                MainSettings.setDownloadProvider(requireContext(), provider)
                val sourceType = when (provider) {
                    ModelSources.sourceHuffingFace -> ModelSources.ModelSourceType.HUGGING_FACE
                    ModelSources.sourceModelScope -> ModelSources.ModelSourceType.MODEL_SCOPE
                    else -> ModelSources.ModelSourceType.MODELERS
                }
                ModelSources.setSourceType(sourceType)
                ModelRepository.clear()
                Toast.makeText(context, R.string.settings_complete, Toast.LENGTH_LONG).show()
            }
        )
        binding.dropdownDownloadProvider.setCurrentItem(currentProvider)
    }

    private fun setupVoiceModelManagement() {
        binding.btnVoiceModelManagement.setOnClickListener {
            val sheet = com.alibaba.mnnllm.android.chat.voice.VoiceModelMarketBottomSheet.newInstance()
            sheet.show(childFragmentManager, "voice_model_market")
        }
    }

    private fun setupStorageManagement() {
        binding.btnStorageManagement.setOnClickListener {
            startActivity(Intent(requireContext(), StorageManagementActivity::class.java))
        }
        setupModelStoragePath()
        setupLogViewer()
    }

    private fun setupModelStoragePath() {
        val context = requireContext()
        binding.tvStoragePath.text = MainSettings.getModelStoragePath(context)

        binding.layoutStoragePath.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.model_storage_path_dialog_title)
                .setMessage(MainSettings.getModelStoragePath(context))
                .setPositiveButton(R.string.browse) { _, _ ->
                    // Use SAF directory picker
                    storageDirPicker.launch(null)
                }
                .setNegativeButton(R.string.type_path) { _, _ ->
                    openManualPathInput()
                }
                .setNeutralButton(R.string.restore_default) { _, _ ->
                    restoreDefaultPath()
                }
                .show()
        }
    }

    private fun handleStorageTreeUri(uri: Uri) {
        val context = requireContext()
        val path = getPathFromTreeUri(context, uri)
        if (path != null) {
            AppLogger.i(TAG, "SAF directory selected: $path")
            applyStoragePath(path)
        } else {
            AppLogger.w(TAG, "Failed to extract path from SAF URI: $uri")
            Toast.makeText(context, "Cannot determine path from selection. Try manual input.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openManualPathInput() {
        val context = requireContext()
        val editText = android.widget.EditText(context)
        editText.setText(MainSettings.getModelStoragePath(context))
        editText.setSingleLine()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.model_storage_path_dialog_title)
            .setMessage("Enter the absolute path to store models (e.g. /storage/emulated/0/MNN/models)")
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newPath = editText.text.toString().trim()
                if (newPath.isNotEmpty()) {
                    AppLogger.i(TAG, "Manual path entered: $newPath")
                    applyStoragePath(newPath)
                }
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun restoreDefaultPath() {
        val context = requireContext()
        MainSettings.resetModelStoragePath(context)
        val defaultPath = MainSettings.getModelStoragePath(context)
        try {
            ModelDownloadManager.getInstance(context).updateCacheDir(defaultPath)
            binding.tvStoragePath.text = defaultPath
            AppLogger.i(TAG, "Storage path restored to default: $defaultPath")
            Toast.makeText(context, "Restored to default", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to restore default path: ${e.message}")
            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyStoragePath(newPath: String) {
        val context = requireContext()
        try {
            ModelDownloadManager.getInstance(context).updateCacheDir(newPath)
            MainSettings.setModelStoragePath(context, newPath)
            binding.tvStoragePath.text = newPath
            AppLogger.i(TAG, "Storage path updated to: $newPath")
            Toast.makeText(context, "Model storage path updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update storage path: ${e.message}")
            Toast.makeText(context, "Failed: ${e.message}\n\nMake sure you have granted 'All files access' permission.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Extract real filesystem path from a SAF tree URI.
     */
    private fun getPathFromTreeUri(context: Context, treeUri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":", limit = 2)
            if (split.size < 2) return null
            val type = split[0]
            val subPath = split[1]
            return if (type.equals("primary", ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/$subPath"
            } else {
                "/storage/$type/$subPath"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting path from tree URI: ${e.message}")
            return null
        }
    }

    private fun setupLogViewer() {
        binding.btnViewLogs.setOnClickListener {
            val context = requireContext()
            val entries = AppLogger.getRecent(200)

            val scrollView = ScrollView(context).apply {
                setPadding(16, 16, 16, 16)
            }
            val textView = TextView(context).apply {
                if (entries.isEmpty()) {
                    text = context.getString(R.string.log_empty)
                } else {
                    text = entries.joinToString("\n") { it.formatted() }
                }
                setTextIsSelectable(true)
                textSize = 12f
            }
            scrollView.addView(textView)

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.log_dialog_title)
                .setView(scrollView)
                .setPositiveButton(R.string.log_copy) { _, _ ->
                    if (entries.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("MNN Logs", entries.joinToString("\n") { it.formatted() })
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.log_clear) { _, _ ->
                    AppLogger.clear()
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupResetApiConfig() {
        binding.btnResetApi.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_api_config)
                .setMessage(R.string.reset_api_config_confirm_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    ApiServerConfig.resetToDefault(requireContext())
                    if (MainSettings.isApiServiceEnabled(requireContext()) && ApiServiceManager.isApiServiceRunning()) {
                        // Run stop/start off main thread to avoid ANR
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                ApiServiceManager.stopApiService(requireContext())
                                delay(500)
                                ApiServiceManager.startApiService(requireContext())
                            }
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.api_config_reset_service_restarted),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.api_config_reset_to_default),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupCrashDiagnostics() {
        val privacyManager = PrivacyPolicyManager.getInstance(requireContext())
        setCrashDiagnosticsChecked(privacyManager.isCrashReportingConsented())

        binding.itemCrashDiagnostics.setOnCheckedChangeListener { isChecked ->
            if (suppressCrashDiagnosticsCallback || crashDiagnosticsToggleInProgress) {
                return@setOnCheckedChangeListener
            }
            crashDiagnosticsToggleInProgress = true
            binding.root.post { crashDiagnosticsToggleInProgress = false }

            if (isChecked) {
                privacyManager.setUserConsent(consented = true)
                (requireActivity().application as? MnnLlmApplication)?.applyCrashReportingConsent()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.privacy_policy_consent_enabled),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnCheckedChangeListener
            }

            if (crashDiagnosticsDialogShowing) {
                return@setOnCheckedChangeListener
            }
            crashDiagnosticsDialogShowing = true

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.crash_diagnostics_disable_title)
                .setMessage(R.string.crash_diagnostics_disable_confirm_message)
                .setPositiveButton(R.string.crash_diagnostics_disable_confirm_action) { _, _ ->
                    privacyManager.setUserConsent(consented = false)
                    (requireActivity().application as? MnnLlmApplication)?.applyCrashReportingConsent()
                    setCrashDiagnosticsChecked(false)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.privacy_policy_consent_disabled),
                        Toast.LENGTH_LONG
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    setCrashDiagnosticsChecked(true)
                }
                .setOnDismissListener {
                    crashDiagnosticsDialogShowing = false
                }
                .show()
        }
    }

    private fun setCrashDiagnosticsChecked(checked: Boolean) {
        suppressCrashDiagnosticsCallback = true
        binding.itemCrashDiagnostics.isChecked = checked
        suppressCrashDiagnosticsCallback = false
    }

    private fun setupUpdateAndVersion() {
        if (com.alibaba.mnnllm.android.BuildConfig.IS_GOOGLE_PLAY_BUILD) {
            binding.btnCheckUpdate.isClickable = false
            binding.btnCheckUpdate.text = getString(R.string.current_version, AppUtils.getAppVersionName(requireContext()))
        } else {
            binding.btnCheckUpdate.text = getString(
                R.string.current_version_check_update,
                AppUtils.getAppVersionName(requireContext())
            )
            binding.btnCheckUpdate.setOnClickListener {
                handleDebugClick()
                updateCheckRunnable?.let { debugClickHandler.removeCallbacks(it) }
                updateCheckRunnable = Runnable {
                    updateChecker = UpdateChecker(requireContext())
                    updateChecker?.checkForUpdates(requireContext(), true)
                }
                debugClickHandler.postDelayed(updateCheckRunnable!!, 1000L)
            }
        }

        try {
            val version = MNN.getVersion()
            binding.tvMnnVersion.text = version
        } catch (_: Exception) {
            binding.tvMnnVersion.text = "N/A"
        }
    }

    private fun setupDebugMode(
        sharedPreferences: android.content.SharedPreferences
    ) {
        val isActivated = sharedPreferences.getBoolean("debug_mode_activated", false)
        updateDebugModeVisibility(isActivated)

        binding.btnDebugMode.setOnClickListener {
            startActivity(Intent(requireContext(), DebugActivity::class.java))
        }
    }

    private fun handleDebugClick() {
        debugClickCount++

        debugClickRunnable?.let { debugClickHandler.removeCallbacks(it) }

        if (debugClickCount >= DEBUG_CLICK_COUNT) {
            updateCheckRunnable?.let { debugClickHandler.removeCallbacks(it) }
            updateDebugModeVisibility(true)
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putBoolean("debug_mode_activated", true)
                .apply()
            debugClickCount = 0
            Log.d(TAG, "Debug mode preference activated")
        } else {
            debugClickRunnable = Runnable {
                debugClickCount = 0
                Log.d(TAG, "Debug click count reset due to timeout")
            }
            debugClickHandler.postDelayed(debugClickRunnable!!, DEBUG_CLICK_TIMEOUT)
        }
    }

    private fun updateDebugModeVisibility(visible: Boolean) {
        binding.dividerDebug.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnDebugMode.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        debugClickRunnable?.let { debugClickHandler.removeCallbacks(it) }
        updateCheckRunnable?.let { debugClickHandler.removeCallbacks(it) }
        _binding = null
    }
}
