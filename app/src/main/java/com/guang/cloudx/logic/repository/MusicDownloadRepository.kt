package com.guang.cloudx.logic.repository

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.guang.cloudx.logic.model.DownloadStage
import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.network.MusicNetwork
import com.guang.cloudx.logic.utils.AudioTagWriter
import com.guang.cloudx.logic.utils.Mp3Transcoder
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray

class MusicDownloadRepository : ViewModelProvider.Factory {
    private class RangeNotSupportedException(message: String) : Exception(message)
    private data class RemoteFileInfo(val contentLength: Long, val supportsRange: Boolean)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_CONCURRENT_PARTS = 8
        const val MIN_PARALLEL_DOWNLOAD_BYTES = 4L * 1024 * 1024
        const val MIN_PART_SIZE_BYTES = 2L * 1024 * 1024

        val downloadClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun downloadMusic(
        context: Context,
        rules: MusicDownloadRules,
        music: Music,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        onProgress: (Music, Int, DownloadStage) -> Unit
    ) {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val tmpFile = File(cacheDir, music.id.toString())
        var tmpWithExt: File? = null
        var outputAudioFile: File? = null
        var tmpCover: File? = null

        try {
            // 1. 获取音乐 URL 和文件信息
            val musicUrl = MusicNetwork.getMusicUrl(music.id.toString(), level, cookie)
            val quality = when (musicUrl.level) {
                "standard" -> ""
                "exhigh" -> "[HQ]"
                "lossless" -> "[SQ]"
                "hires" -> "[HR]"
                else -> ""
            }
            val baseFileName = rules.fileName.replace("\${level}", quality)
                .replace("\${name}", music.name)
                .replace("\${id}", music.id.toString())
                .replace("\${artists}", music.artists.joinToString(rules.delimiter) { it.name })
                .replace("\${album}", music.album.name)
                .replace("\${albumId}", music.album.id.toString())
                .replace(Regex("[\\\\/:*?\"<>|]"), " ")

            tmpCover = File(cacheDir, "${music.id}.jpg")
            val coverFile = tmpCover!!

            coroutineScope {
                val coverDeferred = async(Dispatchers.IO) {
                    try {
                        downloadFile(url = music.album.picUrl, file = coverFile)
                        coverFile.takeIf { it.exists() && it.length() > 0 }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val lyricDeferred = async {
                    try {
                        MusicNetwork.getLyrics(music.id.toString(), cookie)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }

                // 2. 执行分块下载
                if (rules.concurrentDownloads > 1) {
                    downloadConcurrently(musicUrl.url, tmpFile, rules.concurrentDownloads) { progress ->
                        onProgress(music, scaleProgress(progress, 0, 80), DownloadStage.DOWNLOADING)
                    }
                } else {
                    downloadFile(url = musicUrl.url, file = tmpFile) { progress ->
                        onProgress(music, scaleProgress(progress, 0, 80), DownloadStage.DOWNLOADING)
                    }
                }
                onProgress(music, 80, DownloadStage.PROCESSING)

                // 3. 检测类型并重命名
                val ext = detectFileTypeFromFile(tmpFile)
                val downloadedAudioFile = File(cacheDir, "$baseFileName.$ext")
                tmpWithExt = downloadedAudioFile
                if (downloadedAudioFile.exists()) downloadedAudioFile.delete()
                if (!tmpFile.renameTo(downloadedAudioFile)) {
                    throw Exception("无法重命名下载文件")
                }

                var finalExt = ext
                var finalAudioFile = downloadedAudioFile

                if (rules.convertM4aToMp3 && ext == "m4a") {
                    val tmpMp3 = File(cacheDir, "$baseFileName.mp3")
                    Mp3Transcoder.transcodeM4aToMp3(downloadedAudioFile, tmpMp3) { progress ->
                        onProgress(music, scaleProgress(progress, 80, 95), DownloadStage.TRANSCODING)
                    }
                    downloadedAudioFile.delete()
                    finalAudioFile = tmpMp3
                    finalExt = "mp3"
                }
                outputAudioFile = finalAudioFile
                onProgress(music, 95, DownloadStage.WRITING_TAGS)

                // 4. 等待预取结果
                val coverForTags = coverDeferred.await()
                val lrc = lyricDeferred.await()
                val lrcText = lrc?.let { if (it.lrc != "") createLyrics(it, music, rules) else null }

                // 5. 写入元数据
                AudioTagWriter.writeTags(
                    finalAudioFile,
                    AudioTagWriter.TagInfo(
                        title = music.name,
                        artist = music.artists.joinToString(rules.delimiter) { it.name },
                        album = music.album.name,
                        coverFile = coverForTags,
                        lyrics = lrcText
                    )
                )
                coverFile.delete()
                onProgress(music, 98, DownloadStage.SAVING)

                // 6. 移动到最终位置
                copyToSaf(context, finalAudioFile, targetDir, "$baseFileName.$finalExt")

                // 7. 写入歌词文件
                if (rules.isSaveLrc && lrcText != null) {
                    writeLrcToSaf(context, lrcText, targetDir, "$baseFileName.lrc", rules.encoding)
                }

                // 8. 清理临时文件
                finalAudioFile.delete()
                onProgress(music, 100, DownloadStage.COMPLETED)
            }

        } catch (e: Exception) {
            tmpFile.delete()
            tmpWithExt?.delete()
            outputAudioFile?.delete()
            tmpCover?.delete()
            // 清理可能存在的分块临时文件
            (0 until rules.concurrentDownloads).forEach {
                File(cacheDir, "${music.id}.part$it").delete()
            }
            throw e
        }
    }

    private suspend fun downloadConcurrently(
        url: String,
        outputFile: File,
        parts: Int,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val remoteFileInfo = getRemoteFileInfo(url)
        val contentLength = remoteFileInfo.contentLength
        val partCount = choosePartCount(contentLength, parts)

        if (!remoteFileInfo.supportsRange || contentLength <= 0 || partCount <= 1) {
            downloadFile(url = url, file = outputFile, progressCallback = onProgress)
            return@withContext
        }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        RandomAccessFile(outputFile, "rw").use { it.setLength(contentLength) }

        try {
            val partSize = contentLength / partCount
            val progressArray = AtomicLongArray(partCount)

            coroutineScope {
                (0 until partCount).map { partIndex ->
                    async {
                        val start = partIndex * partSize
                        val end = if (partIndex == partCount - 1) contentLength - 1 else start + partSize - 1
                        downloadChunk(url, outputFile, start, end) { downloaded ->
                            progressArray.set(partIndex, downloaded)
                            onProgress(calculateProgress(progressArray, contentLength))
                        }
                    }
                }.awaitAll()
            }
            if (outputFile.length() != contentLength) {
                throw Exception("文件大小校验失败")
            }
            onProgress(100)
        } catch (e: RangeNotSupportedException) {
            outputFile.delete()
            downloadFile(url = url, file = outputFile, progressCallback = onProgress)
        } catch (e: Exception) {
            outputFile.delete()
            throw e
        }
    }

    private suspend fun downloadChunk(
        url: String,
        outputFile: File,
        start: Long,
        end: Long,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .header("Accept-Encoding", "identity")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (response.code == HttpURLConnection.HTTP_OK) {
                throw RangeNotSupportedException("服务器不支持分块下载")
            }
            if (response.code != HttpURLConnection.HTTP_PARTIAL) {
                throw Exception("HTTP error: ${response.code} for range $start-$end")
            }

            var downloaded = 0L
            val expectedLength = end - start + 1
            val buffer = ByteArray(BUFFER_SIZE)
            response.body.byteStream().use { input ->
                RandomAccessFile(outputFile, "rw").use { output ->
                    output.seek(start)
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        ensureActive()
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        onProgress(downloaded)
                        bytes = input.read(buffer)
                    }
                }
            }
            if (downloaded != expectedLength) {
                throw Exception("分块大小校验失败: $start-$end")
            }
        }
    }

    private fun scaleProgress(progress: Int, start: Int, end: Int): Int {
        return (start + (progress.coerceIn(0, 100) * (end - start) / 100)).coerceIn(start, end)
    }

    private fun choosePartCount(contentLength: Long, requestedParts: Int): Int {
        if (contentLength < MIN_PARALLEL_DOWNLOAD_BYTES) return 1

        val cappedRequest = requestedParts.coerceIn(1, MAX_CONCURRENT_PARTS)
        val partsBySize = (contentLength / MIN_PART_SIZE_BYTES).coerceAtLeast(1L).toInt()
        return minOf(cappedRequest, partsBySize)
    }

    private fun calculateProgress(progressArray: AtomicLongArray, total: Long): Int {
        var downloaded = 0L
        for (i in 0 until progressArray.length()) {
            downloaded += progressArray.get(i)
        }
        return ((downloaded * 100) / total).toInt().coerceIn(0, 100)
    }

    private fun parseTotalLength(contentRange: String?): Long {
        val total = contentRange
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.takeUnless { it == "*" }

        return total?.toLongOrNull() ?: -1L
    }

    private suspend fun getRemoteFileInfo(url: String): RemoteFileInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("Accept-Encoding", "identity")
            .build()

        return@withContext runCatching {
            downloadClient.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val totalLength = parseTotalLength(response.header("Content-Range"))
                        RemoteFileInfo(totalLength, totalLength > 0)
                    }

                    HttpURLConnection.HTTP_OK -> {
                        RemoteFileInfo(response.body.contentLength(), false)
                    }

                    else -> RemoteFileInfo(-1L, false)
                }
            }
        }.getOrElse {
            RemoteFileInfo(-1L, false)
        }
    }

    suspend fun downloadFile(
        context: Context? = null,
        url: String,
        file: File? = null,
        documentFile: DocumentFile? = null,
        progressCallback: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(file != null || documentFile != null) { "必须提供 file 或 documentFile" }

        val request = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP error: ${response.code}")
            }

            val body = response.body
            val total: Long = body.contentLength()
            var downloaded: Long = 0
            val buffer = ByteArray(BUFFER_SIZE)

            val output: OutputStream = when {
                file != null -> file.outputStream()
                documentFile != null -> {
                    context!!.contentResolver.openOutputStream(documentFile.uri)
                        ?: throw Exception("无法打开 OutputStream")
                }

                else -> throw IllegalStateException()
            }

            body.byteStream().use { input: InputStream ->
                output.use { out ->
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        ensureActive()
                        out.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (total > 0) {
                            progressCallback(((downloaded * 100) / total).toInt())
                        }
                        bytes = input.read(buffer)
                    }
                    out.flush()
                }
            }
            if (total <= 0) {
                progressCallback(100)
            }
        }
    }

    private suspend fun copyToSaf(context: Context, sourceFile: File, targetDir: DocumentFile, finalFileName: String) =
        withContext(Dispatchers.IO) {
            val existing = targetDir.findFile(finalFileName)
            if (existing != null) {
                try {
                    context.contentResolver.openFileDescriptor(existing.uri, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { out ->
                            sourceFile.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE) }
                        }
                    } ?: throw Exception("无法打开现有文件")
                } catch (e: Exception) {
                    context.contentResolver.delete(existing.uri, null, null)
                    val newDoc = targetDir.createFile("audio/*", finalFileName) ?: throw Exception("无法创建音乐文件")
                    context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE) }
                    } ?: throw Exception("无法打开新文件")
                }
            } else {
                val musicDoc = targetDir.createFile("audio/*", finalFileName) ?: throw Exception("无法创建音乐文件")
                context.contentResolver.openOutputStream(musicDoc.uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE) }
                } ?: throw Exception("无法打开目标输出流")
            }
        }

    private suspend fun writeLrcToSaf(
        context: Context,
        lrcText: String,
        targetDir: DocumentFile,
        lrcName: String,
        encoding: String
    ) = withContext(Dispatchers.IO) {
        val existingLrc = targetDir.findFile(lrcName)
        if (existingLrc != null) {
            try {
                context.contentResolver.openFileDescriptor(existingLrc.uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        out.write(lrcText.toByteArray(Charset.forName(encoding)))
                    }
                } ?: throw Exception("无法打开已存在的 lrc 输出流")
            } catch (e: Exception) {
                context.contentResolver.delete(existingLrc.uri, null, null)
                targetDir.createFile("application/octet-stream", lrcName)?.let {
                    context.contentResolver.openOutputStream(it.uri)?.use { out ->
                        out.write(lrcText.toByteArray(Charset.forName(encoding)))
                    }
                }
            }
        } else {
            targetDir.createFile("application/octet-stream", lrcName)?.let {
                context.contentResolver.openOutputStream(it.uri)?.use { out ->
                    out.write(lrcText.toByteArray(Charset.forName(encoding)))
                }
            }
        }
    }

    suspend fun cacheMusic(
        music: Music,
        parent: File,
        cookie: String
    ): File {
        val file = File(parent, music.id.toString())
        if (!file.exists()) {
            downloadFile(
                url = MusicNetwork.getMusicUrl(music.id.toString(), "standard", cookie).url,
                file = file
            )
            file.createNewFile()
        }
        return file
    }

    private fun detectFileTypeFromFile(file: File): String {
        val buffer = ByteArray(12)
        file.inputStream().use { it.read(buffer) }
        val hex = buffer.joinToString(" ") { String.format("%02X", it) }
        return when {
            hex.startsWith("49 44 33") || hex.startsWith("FF FB") -> "mp3"
            hex.startsWith("FF F1") || hex.startsWith("FF F9") -> "aac"
            hex.startsWith("52 49 46 46") && String(buffer.copyOfRange(8, 12)) == "WAVE" -> "wav"
            hex.startsWith("66 4C 61 43") -> "flac"
            hex.startsWith("4F 67 67 53") -> "ogg"
            hex.startsWith("00 00 00") && hex.contains("66 74 79 70") -> "m4a"
            else -> "unknown"
        }
    }

    private fun createLyrics(lyric: Lyric, music: Music, rules: MusicDownloadRules): String {
        val lrc = if (rules.isSaveYrc && lyric.yrc != "") lyric.yrc else lyric.lrc
        val tlLrc =
            if (rules.isSaveTlLrc) if (rules.isSaveYrc && lyric.ytlrc != "") lyric.ytlrc else lyric.tlyric else ""
        val romaLrc =
            if (rules.isSaveRomaLrc) if (rules.isSaveYrc && lyric.yromalrc != "") lyric.yromalrc else lyric.romalrc else ""
        return """
[ti:${music.name}]
[ar:${music.artists.joinToString("、") { it.name }}]
[al:${music.album.name}]

$lrc

$tlLrc

$romaLrc
""".trimIndent().trimEnd()
    }
}
