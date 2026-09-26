package com.guang.cloudx.logic.utils

import java.io.File
import java.io.IOException

/** Only task-owned cache paths; never follows names from a destination directory or removes saved media. */
fun clearDownloadArtifacts(
    cacheDir: File,
    musicId: Long,
) {
    val temp = File(cacheDir, "download_temp")
    val artifacts =
        listOf(
            File(temp, musicId.toString()),
            File(temp, "$musicId.download.json"),
            File(temp, "$musicId.download.json.tmp"),
            File(temp, "$musicId.jpg"),
            File(temp, "$musicId.processing"),
            File(cacheDir, musicId.toString()),
            File(cacheDir, "$musicId.download.json"),
            File(cacheDir, "$musicId.download.json.tmp"),
        )
    artifacts.forEach { file ->
        if (file.exists() && !file.deleteRecursively()) throw IOException("无法清理临时文件：${file.name}")
    }
}
