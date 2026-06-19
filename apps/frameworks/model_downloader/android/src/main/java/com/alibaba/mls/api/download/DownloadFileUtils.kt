// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mls.api.download

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object DownloadFileUtils {
    const val TAG: String = "FileUtils"

    fun repoFolderName(repoId: String?, repoType: String?): String {
        if (repoId == null || repoType == null) {
            return ""
        }
        val repoParts = repoId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val parts: MutableList<String> = ArrayList()
        parts.add(repoType + "s") // e.g., "models"
        for (part in repoParts) {
            if (part != null && !part.isEmpty()) {
                parts.add(part)
            }
        }
        // Join parts with "--" separator
        return java.lang.String.join("--", parts)
    }

    fun deleteDirectoryRecursively(dir: File?): Boolean {
        if (dir == null || !dir.exists()) {
            return false
        }

        val dirPath = dir.toPath()
        try {
            Files.walkFileTree(dirPath, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun postVisitDirectory(
                    directory: Path,
                    exc: IOException?
                ): FileVisitResult {
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    return FileVisitResult.TERMINATE
                }
            })
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun getPointerPath(storageFolder: File?, commitHash: String, relativePath: String): File {
        val commitFolder = File(storageFolder, "snapshots/$commitHash")
        return File(commitFolder, relativePath)
    }

    @JvmStatic
    fun getPointerPathParent(storageFolder: File?, sha: String): File {
        return File(storageFolder, "snapshots/$sha")
    }

    fun getLastFileName(path: String): String {
        if (path.isEmpty()) {
            return path
        }
        val pos = path.lastIndexOf('/')
        return if ((pos == -1)) path else path.substring(pos + 1)
    }

    fun createSymlink(target: String?, linkPath: String?) {
        if (target == null || linkPath == null) return
        val targetPath = Paths.get(target)
        val link = Paths.get(linkPath)
        createSymlink(targetPath, link)
    }
    fun createSymlink(target: Path?, linkPath: Path?) {
        try {
            Files.createSymbolicLink(linkPath, target)
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            if (Files.isSymbolicLink(linkPath)) {
                val existingTarget = Files.readSymbolicLink(linkPath)
                if (existingTarget != target) {
                    Files.delete(linkPath)
                    Files.createSymbolicLink(linkPath, target)
                }
            } else {
                Files.delete(linkPath)
                Files.createSymbolicLink(linkPath, target)
            }
        } catch (e: java.nio.file.AccessDeniedException) {
            // FUSE / sdcardfs (external storage) does not support symbolic links.
            // Fallback: copy file content so the link path becomes a real file.
            // Directory symlink targets are not supported here — callers must use
            // flat mode (see isSymlinkSupported) to avoid directory symlinks.
            Log.w(TAG, "createSymlink denied (FUSE?), falling back to copy: $linkPath -> $target", e)
            val tgtFile = target?.toFile()
            val linkFile = linkPath?.toFile()
            if (tgtFile != null && linkFile != null && tgtFile.isFile) {
                Files.copy(target, linkPath, StandardCopyOption.REPLACE_EXISTING)
            } else {
                throw e
            }
        }
    }

    /**
     * Probe whether the filesystem at [rootPath] supports symbolic links.
     * External storage (/storage/emulated/0/...) mounted via FUSE/sdcardfs does NOT,
     * even with MANAGE_EXTERNAL_STORAGE permission. Internal storage (ext4) does.
     *
     * Callers should use flat mode (skip blobs + symlinks) when this returns false.
     */
    @JvmStatic
    fun isSymlinkSupported(rootPath: String): Boolean {
        val root = File(rootPath)
        if (!root.exists() && !root.mkdirs()) return false
        val probe = File(root, ".symlink_probe_${System.nanoTime()}")
        val probePath = probe.toPath()
        val targetPath = Paths.get(".")
        return try {
            Files.createSymbolicLink(probePath, targetPath)
            Files.deleteIfExists(probePath)
            true
        } catch (e: Exception) {
            // AccessDeniedException (FUSE) or FileSystemException — not supported
            Log.i(TAG, "isSymlinkSupported($rootPath)=false: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }


}
