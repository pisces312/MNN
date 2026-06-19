// Created by ruoyi.sjd on 2025/5/8.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.alibaba.mls.api.download.ms
import android.util.Log
import com.alibaba.mls.api.ApplicationProvider
import com.alibaba.mls.api.FileDownloadException
import com.alibaba.mls.api.HfFileMetadata
import com.alibaba.mls.api.download.DownloadFileUtils.createSymlink
import com.alibaba.mls.api.download.DownloadFileUtils.deleteDirectoryRecursively
import com.alibaba.mls.api.download.DownloadFileUtils.getLastFileName
import com.alibaba.mls.api.download.DownloadFileUtils.getPointerPathParent
import com.alibaba.mls.api.download.DownloadFileUtils.isSymlinkSupported
import com.alibaba.mls.api.download.DownloadFileUtils.repoFolderName
import com.alibaba.mls.api.download.DownloadPausedException
import com.alibaba.mls.api.download.FileDownloadTask
import com.alibaba.mls.api.download.ModelFileDownloader
import com.alibaba.mls.api.download.ModelFileDownloader.FileDownloadListener
import com.alibaba.mls.api.download.ModelRepoDownloader
import com.alibaba.mls.api.ms.MsApiClient
import com.alibaba.mls.api.ms.MsRepoInfo
import com.alibaba.mls.api.download.ModelIdUtils
import com.alibaba.mls.api.download.TimeUtils
import com.alibaba.mls.api.download.DownloadCoroutineManager
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class MsModelDownloader(override var callback: ModelRepoDownloadCallback?,
                        cacheRootPath: String
) : ModelRepoDownloader() {
    companion object {
        private const val TAG = "MsModelDownloader"

        fun getCachePathRoot(modelDownloadPathRoot: String): String {
            return "$modelDownloadPathRoot/modelscope"
        }

        fun getModelPath(modelsDownloadPathRoot: String, modelId: String): File {
            return File(modelsDownloadPathRoot, getLastFileName(modelId))
        }
    }

    override var cacheRootPath: String = getCachePathRoot(cacheRootPath)
    private var msApiClient: MsApiClient = MsApiClient()
    override fun setListener(callback: ModelRepoDownloadCallback?) {
        this.callback = callback
    }

    /**
     * Unified method to fetch repo information from ModelScope API
     * @param modelId the model ID to fetch info for
     * @param calculateSize whether to calculate repo size (not needed for ModelScope as size is included)
     * @return MsRepoInfo object
     * @throws FileDownloadException if failed to fetch repo info
     */
    private suspend fun fetchRepoInfo(modelId: String, calculateSize: Boolean = false): MsRepoInfo {
        Log.i(TAG, "fetchRepoInfo: modelId=$modelId, calculateSize=$calculateSize")
        
        return withContext(DownloadCoroutineManager.downloadDispatcher) {
            runCatching {
                val msModelId = ModelIdUtils.getRepositoryPath(modelId)
                Log.i(TAG, "fetchRepoInfo: msModelId=$msModelId")
                val split = msModelId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (split.size != 2) {
                    Log.e(TAG, "fetchRepoInfo: invalid modelId format: $modelId -> $msModelId")
                    throw FileDownloadException("Invalid model ID format for $modelId, expected format: owner/repo")
                }
                
                val response = msApiClient.apiService.getModelFiles(split[0], split[1]).execute()
                Log.i(TAG, "fetchRepoInfo: response code=${response.code()}, isSuccessful=${response.isSuccessful}")
                if (response.isSuccessful && response.body() != null) {
                    val repoInfo = response.body()!!
                    
                    // Call onRepoInfo callback with repo metadata
                    // ModelScope tree API does not currently expose stable repo modified timestamp.
                    // Use 0 to indicate "unknown" and let upper layer fallback to size/signature checks.
                    val lastModified = 0L
                    val repoSize = repoInfo.Data?.Files?.filter { it.Type != "tree" }?.sumOf { it.Size } ?: 0L
                    callback?.onRepoInfo(modelId, lastModified, repoSize)
                    repoInfo
                } else {
                    val errorMsg = if (!response.isSuccessful) {
                        "API request failed with code ${response.code()}: ${response.message()}"
                    } else {
                        "API response was null or empty"
                    }
                    Log.e(TAG, "fetchRepoInfo failed for $modelId: $errorMsg")
                    throw FileDownloadException("Failed to fetch repo info for $modelId: $errorMsg")
                }
            }.getOrElse { exception ->
                Log.e(TAG, "fetchRepoInfo exception for $modelId: ${exception.javaClass.name}: ${exception.message}", exception)
                throw FileDownloadException("Failed to fetch repo info for $modelId: ${exception.message}")
            }
        }
    }

    override fun download(modelId: String) {
        Log.i(TAG, "download: modelId=$modelId, cacheRootPath=$cacheRootPath")
        DownloadCoroutineManager.launchDownload {
            downloadMsRepo(modelId)
        }
    }

    override suspend fun checkUpdate(modelId: String) {
        try {
            fetchRepoInfo(modelId)
        } catch (e: FileDownloadException) {
            Log.e(TAG, "Failed to check update for $modelId", e)
        }
    }

    override fun getDownloadPath(modelId: String): File {
        // Legacy: top-level symlink (internal storage / ext4)
        val legacyLink = File(cacheRootPath, getLastFileName(modelId))
        if (legacyLink.exists()) return legacyLink
        // Flat mode (external storage / FUSE): real snapshots directory
        val msModelId = ModelIdUtils.getRepositoryPath(modelId)
        val repoFolder = repoFolderName(msModelId, "model")
        return File(cacheRootPath, "$repoFolder/snapshots/_no_sha_")
    }

    override fun deleteRepo(modelId: String) {
        val msModelId = ModelIdUtils.getRepositoryPath(modelId)
        val msRepoFolderName = repoFolderName(msModelId, "model")
        val msStorageFolder = File(this.cacheRootPath, msRepoFolderName)
        Log.d(TAG, "removeStorageFolder: " + msStorageFolder.absolutePath)
        if (msStorageFolder.exists()) {
            val result = deleteDirectoryRecursively(msStorageFolder)
            if (!result) {
                Log.e(TAG, "remove storageFolder" + msStorageFolder.absolutePath + " faield")
            }
        }
        val msLinkFolder = this.getDownloadPath(modelId)
        Log.d(TAG, "removeMsLinkFolder: " + msLinkFolder.absolutePath)
        msLinkFolder.delete()
    }

    override suspend fun getRepoSize(modelId: String): Long {
        return withContext(DownloadCoroutineManager.downloadDispatcher) {
            runCatching {
                val repoInfo = fetchRepoInfo(modelId, calculateSize = true)
                repoInfo.Data?.Files?.filter { it.Type != "tree" }?.sumOf { it.Size } ?: 0L
            }.getOrElse { exception ->
                Log.e(TAG, "Failed to get repo size for $modelId", exception)
                // Try to get file_size from saved market data as fallback
                try {
                    val marketSize = com.alibaba.mls.api.download.DownloadPersistentData.getMarketSizeTotal(ApplicationProvider.get(), modelId)
                    if (marketSize > 0) {
                        Log.d(TAG, "Using saved market size as fallback for $modelId: $marketSize")
                        return@withContext marketSize
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get saved market size for $modelId", e)
                }
                0L
            }
        }
    }

    private suspend fun downloadMsRepo(modelId: String) {
        val modelScopeId = ModelIdUtils.getRepositoryPath(modelId)
        Log.i(TAG, "downloadMsRepo: modelId=$modelId, modelScopeId=$modelScopeId, cacheRootPath=$cacheRootPath")
        val split = modelScopeId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (split.size != 2) {
            Log.e(TAG, "downloadMsRepo: invalid modelId format: $modelId")
            callback?.onDownloadFailed(modelId, FileDownloadException("getRepoInfoFailed modelId format error: $modelId"))
            return
        }
        try {
            val repoInfo = fetchRepoInfo(modelId, calculateSize = true)
            Log.i(TAG, "downloadMsRepo: repoInfo fetched, files=${repoInfo.Data?.Files?.size ?: 0}")
            callback?.onDownloadTaskAdded()
            // Run the actual download on IO dispatcher to avoid blocking  
            withContext(Dispatchers.IO) {
                downloadMsRepoInner(modelId, modelScopeId, repoInfo)
            }
            callback?.onDownloadTaskRemoved()
        } catch (e: FileDownloadException) {
            Log.e(TAG, "downloadMsRepo failed for $modelId: ${e.message}", e)
            callback?.onDownloadFailed(modelId, e)
        } catch (e: Exception) {
            Log.e(TAG, "downloadMsRepo unexpected error for $modelId: ${e.javaClass.name}: ${e.message}", e)
            callback?.onDownloadFailed(modelId, FileDownloadException(e.message))
        }
    }

    private fun downloadMsRepoInner(modelId:String, modelScopeId: String, msRepoInfo: MsRepoInfo) {
        Log.i(TAG, "downloadMsRepoInner: modelId=$modelId, cacheRootPath=$cacheRootPath")
        val folderLinkFile =
            File(cacheRootPath, getLastFileName(modelScopeId))
        Log.i(TAG, "downloadMsRepoInner: folderLinkFile=${folderLinkFile.absolutePath}, exists=${folderLinkFile.exists()}")
        if (folderLinkFile.exists()) {
            Log.d(TAG, "downloadMsRepoInner: already exists, calling onDownloadFileFinished")
            callback?.onDownloadFileFinished(modelId, folderLinkFile.absolutePath)
            return
        }
        // Detect symlink support: external storage (FUSE) does not support symlinks,
        // so we use flat mode (files directly in snapshots/, no blobs/symlink layer).
        val flatMode = !isSymlinkSupported(cacheRootPath)
        Log.i(TAG, "downloadMsRepoInner: flatMode=$flatMode (symlinkSupported=${!flatMode})")
        val modelDownloader = ModelFileDownloader()
        val hasError = false
        val errorInfo = StringBuilder()
        val repoFolderName = repoFolderName(modelScopeId, "model")
        val storageFolder = File(this.cacheRootPath, repoFolderName)
        // Flat mode migration: clean up legacy blobs/ + incomplete symlinks left by
        // failed legacy-mode downloads on FUSE. Without this, blobs/ would accumulate
        // orphan files (wasting space) since flat mode never reads from blobs/.
        // Only clean if snapshots/ has no fully-downloaded flat files yet.
        if (flatMode) {
            val legacyBlobsDir = File(storageFolder, "blobs")
            val flatSnapshotsDir = File(storageFolder, "snapshots/_no_sha_")
            val hasFlatFiles = flatSnapshotsDir.exists() &&
                flatSnapshotsDir.listFiles()?.any { it.isFile } == true
            if (legacyBlobsDir.exists() && !hasFlatFiles) {
                Log.i(TAG, "downloadMsRepoInner flatMode: cleaning legacy blobs dir " +
                        "(no flat files yet): ${legacyBlobsDir.absolutePath}")
                deleteDirectoryRecursively(legacyBlobsDir)
                // Also remove stale snapshots/ symlinks (dangling after blobs removal)
                val legacySnapshotsDir = File(storageFolder, "snapshots")
                if (legacySnapshotsDir.exists()) {
                    Log.i(TAG, "downloadMsRepoInner flatMode: cleaning legacy snapshots dir: " +
                            legacySnapshotsDir.absolutePath)
                    deleteDirectoryRecursively(legacySnapshotsDir)
                }
            }
        }
        val parentPointerPath = getPointerPathParent(storageFolder, "_no_sha_")
        val downloadTaskList: List<FileDownloadTask>
        val totalAndDownloadSize = LongArray(2)
        Log.d(TAG, "downloadMsRepoInner collectMsTaskList")
        downloadTaskList = collectMsTaskList(
            modelScopeId,
            storageFolder,
            parentPointerPath,
            msRepoInfo,
            totalAndDownloadSize,
            flatMode
        )
        Log.d(TAG, "downloadMsRepoInner downloadTaskList： " + downloadTaskList.size)
        val fileDownloadListener =
            object : FileDownloadListener {
                override fun onDownloadDelta(
                    fileName: String?,
                    downloadedBytes: Long,
                    totalBytes: Long,
                    delta: Long
                ): Boolean {
                    totalAndDownloadSize[1] += delta
                    callback?.onDownloadingProgress(
                        modelId, "file", fileName,
                        totalAndDownloadSize[1], totalAndDownloadSize[0]
                    )
                    return pausedSet.contains(modelId)
                }
            }
        try {
            for (fileDownloadTask in downloadTaskList) {
                modelDownloader.downloadFile(fileDownloadTask, fileDownloadListener)
            }
        } catch (e: DownloadPausedException) {
            pausedSet.remove(modelId)
            callback?.onDownloadPaused(modelId)
            return
        } catch (e: Exception) {
            callback?.onDownloadFailed(modelId, e)
            return
        }
        if (!hasError) {
            if (flatMode) {
                // Flat mode: no top-level symlink, return snapshots dir directly as model path
                Log.d(TAG, "downloadMsRepoInner flat mode: using snapshots dir as model path")
                callback?.onDownloadFileFinished(modelId, parentPointerPath.absolutePath)
            } else {
                val folderLinkPath = folderLinkFile.absolutePath
                Log.d(TAG, "downloadMsRepoInner loop finished, creating symlink for $modelId")
                createSymlink(parentPointerPath.toString(), folderLinkPath)
                Log.d(TAG, "downloadMsRepoInner symlink created, calling onDownloadFileFinished")
                callback?.onDownloadFileFinished(modelId, folderLinkPath)
            }
            Log.d(TAG, "downloadMsRepoInner callback return")
        } else {
            Log.e(
                TAG,
                "Errors occurred during download: $errorInfo"
            )
        }
    }

    private fun collectMsTaskList(
        modelScopeId: String,
        storageFolder: File,
        parentPointerPath: File,
        msRepoInfo: MsRepoInfo,
        totalAndDownloadSize: LongArray,
        flatMode: Boolean = false
    ): List<FileDownloadTask> {
        val fileDownloadTasks: MutableList<FileDownloadTask> = ArrayList()
        for (i in msRepoInfo.Data?.Files?.indices!!) {
            val subFile = msRepoInfo.Data!!.Files!![i]
            val fileDownloadTask = FileDownloadTask()
            if (subFile.Type == "tree") {
                continue
            }
            fileDownloadTask.relativePath = subFile.Path
            fileDownloadTask.fileMetadata = HfFileMetadata()
            fileDownloadTask.fileMetadata!!.location = String.format(
                "https://modelscope.cn/api/v1/models/%s/repo?FilePath=%s",
                modelScopeId,
                subFile.Path
            )
            fileDownloadTask.fileMetadata!!.size = subFile.Size
            fileDownloadTask.fileMetadata!!.etag = subFile.Sha256
            fileDownloadTask.etag = subFile.Sha256
            fileDownloadTask.pointerPath = File(parentPointerPath, subFile.Path)
            if (flatMode) {
                // Flat mode: download directly into pointerPath, no blobs/symlink layer.
                // blobPath == pointerPath so ModelFileDownloader skips createSymlink.
                fileDownloadTask.blobPath = fileDownloadTask.pointerPath
                fileDownloadTask.blobPathIncomplete =
                    File(parentPointerPath, subFile.Path + ".incomplete")
            } else {
                fileDownloadTask.blobPath = File(storageFolder, "blobs/" + subFile.Sha256)
                fileDownloadTask.blobPathIncomplete =
                    File(storageFolder, "blobs/" + subFile.Sha256 + ".incomplete")
            }
            fileDownloadTask.downloadedSize =
                if (fileDownloadTask.blobPath!!.exists()) fileDownloadTask.blobPath!!.length() else (if (fileDownloadTask.blobPathIncomplete!!.exists()) fileDownloadTask.blobPathIncomplete!!.length() else 0)
            totalAndDownloadSize[0] += subFile.Size
            totalAndDownloadSize[1] += fileDownloadTask.downloadedSize
            fileDownloadTasks.add(fileDownloadTask)
        }
        return fileDownloadTasks
    }
}
