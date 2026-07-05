package com.guang.cloudx.logic.repository

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
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
    private data class RemoteFileInfo(
        val contentLength: Long,
        val supportsRange: Boolean,
        val eTag: String? = null,
        val lastModified: String? = null
    )

    private data class DownloadCheckpoint(
        val version: Int = CHECKPOINT_VERSION,
        val mode: String = "",
        val identity: String = "",
        val contentLength: Long = -1L,
        val eTag: String? = null,
        val lastModified: String? = null,
        val ranges: MutableList<DownloadCheckpointRange>? = null
    )

    private data class DownloadCheckpointRange(
        val start: Long = 0L,
        val end: Long = -1L,
        var downloaded: Long = 0L
    )

    private class CheckpointStore(
        private val file: File,
        val checkpoint: DownloadCheckpoint
    ) {
        private val gson = Gson()
        private var lastSaveAt = 0L

        @Synchronized
        fun updateRange(index: Int, downloaded: Long, force: Boolean = false) {
            val range = checkpoint.ranges!![index]
            val rangeLength = range.end - range.start + 1
            range.downloaded = downloaded.coerceIn(0L, rangeLength)
            save(force)
        }

        @Synchronized
        fun save(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastSaveAt < CHECKPOINT_SAVE_INTERVAL_MS) return

            file.parentFile?.mkdirs()
            val json = gson.toJson(checkpoint)
            val tempFile = File("${file.absolutePath}.tmp")
            tempFile.writeText(json)
            if (file.exists()) file.delete()
            if (!tempFile.renameTo(file)) {
                file.writeText(json)
                tempFile.delete()
            }
            lastSaveAt = now
        }

    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_CONCURRENT_PARTS = 8
        const val MIN_PARALLEL_DOWNLOAD_BYTES = 4L * 1024 * 1024
        const val MIN_PART_SIZE_BYTES = 2L * 1024 * 1024
        const val CHECKPOINT_VERSION = 1
        const val CHECKPOINT_MODE_SINGLE = "single"
        const val CHECKPOINT_MODE_MULTI = "multi"
        const val CHECKPOINT_SAVE_INTERVAL_MS = 1_000L

        val downloadClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val checkpointGson = Gson()
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
        var audioDownloaded = false

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

                // 2. 执行可续传下载
                downloadAudioFile(
                    url = musicUrl.url,
                    outputFile = tmpFile,
                    checkpointIdentity = "${music.id}:${musicUrl.level}",
                    parts = rules.concurrentDownloads
                ) { progress ->
                    onProgress(music, scaleProgress(progress, 0, 80), DownloadStage.DOWNLOADING)
                }
                audioDownloaded = true
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
            if (audioDownloaded) {
                resetDownloadState(tmpFile)
            }
            tmpWithExt?.delete()
            outputAudioFile?.delete()
            tmpCover?.delete()
            throw e
        }
    }

    private suspend fun downloadAudioFile(
        url: String,
        outputFile: File,
        checkpointIdentity: String,
        parts: Int,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val remoteFileInfo = getRemoteFileInfo(url)
        val contentLength = remoteFileInfo.contentLength
        val partCount = choosePartCount(contentLength, parts)

        try {
            if (!remoteFileInfo.supportsRange || contentLength <= 0) {
                resetDownloadState(outputFile)
                downloadFile(url = url, file = outputFile, progressCallback = onProgress)
                deleteCheckpoint(outputFile)
                return@withContext
            }

            if (partCount > 1) {
                downloadConcurrently(
                    url = url,
                    outputFile = outputFile,
                    checkpointIdentity = checkpointIdentity,
                    remoteFileInfo = remoteFileInfo,
                    partCount = partCount,
                    onProgress = onProgress
                )
            } else {
                downloadWithResume(
                    url = url,
                    outputFile = outputFile,
                    checkpointIdentity = checkpointIdentity,
                    remoteFileInfo = remoteFileInfo,
                    onProgress = onProgress
                )
            }

            deleteCheckpoint(outputFile)
            onProgress(100)
        } catch (e: RangeNotSupportedException) {
            resetDownloadState(outputFile)
            downloadFile(url = url, file = outputFile, progressCallback = onProgress)
            deleteCheckpoint(outputFile)
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun downloadWithResume(
        url: String,
        outputFile: File,
        checkpointIdentity: String,
        remoteFileInfo: RemoteFileInfo,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val contentLength = remoteFileInfo.contentLength
        val checkpointRanges = buildRanges(contentLength, 1)
        val checkpointStore = prepareCheckpoint(
            outputFile = outputFile,
            mode = CHECKPOINT_MODE_SINGLE,
            identity = checkpointIdentity,
            remoteFileInfo = remoteFileInfo,
            expectedRanges = checkpointRanges
        )
        val range = checkpointStore.checkpoint.ranges!![0]

        val downloadedFromFile = when {
            !outputFile.exists() -> 0L
            outputFile.length() > contentLength -> {
                resetDownloadState(outputFile)
                0L
            }

            else -> outputFile.length()
        }
        checkpointStore.updateRange(0, downloadedFromFile, force = true)

        if (downloadedFromFile >= contentLength) {
            onProgress(100)
            return@withContext
        }

        if (downloadedFromFile > 0) {
            onProgress(((downloadedFromFile * 100) / contentLength).toInt().coerceIn(0, 100))
        }

        RandomAccessFile(outputFile, "rw").use { it.setLength(downloadedFromFile) }
        try {
            downloadChunk(url, outputFile, range.start + downloadedFromFile, range.end) { downloaded ->
                val totalDownloaded = downloadedFromFile + downloaded
                checkpointStore.updateRange(0, totalDownloaded)
                onProgress(((totalDownloaded * 100) / contentLength).toInt().coerceIn(0, 100))
            }
        } finally {
            checkpointStore.save(force = true)
        }
        checkpointStore.updateRange(0, contentLength, force = true)
    }

    private suspend fun downloadConcurrently(
        url: String,
        outputFile: File,
        checkpointIdentity: String,
        remoteFileInfo: RemoteFileInfo,
        partCount: Int,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val contentLength = remoteFileInfo.contentLength
        val checkpointRanges = buildRanges(contentLength, partCount)
        val checkpointStore = prepareCheckpoint(
            outputFile = outputFile,
            mode = CHECKPOINT_MODE_MULTI,
            identity = checkpointIdentity,
            remoteFileInfo = remoteFileInfo,
            expectedRanges = checkpointRanges
        )
        val ranges = checkpointStore.checkpoint.ranges!!

        outputFile.parentFile?.mkdirs()
        if (!outputFile.exists() || outputFile.length() != contentLength) {
            RandomAccessFile(outputFile, "rw").use { it.setLength(contentLength) }
        }

        val progressArray = AtomicLongArray(partCount)
        for (index in 0 until partCount) {
            progressArray.set(index, ranges[index].downloaded)
        }
        onProgress(calculateProgress(progressArray, contentLength))

        try {
            coroutineScope {
                (0 until partCount).map { partIndex ->
                    async {
                        val range = ranges[partIndex]
                        val rangeLength = range.end - range.start + 1
                        val alreadyDownloaded = range.downloaded.coerceIn(0L, rangeLength)
                        if (alreadyDownloaded >= rangeLength) return@async

                        val resumeStart = range.start + alreadyDownloaded
                        downloadChunk(url, outputFile, resumeStart, range.end) { downloaded ->
                            val totalForRange = alreadyDownloaded + downloaded
                            progressArray.set(partIndex, totalForRange)
                            checkpointStore.updateRange(partIndex, totalForRange)
                            onProgress(calculateProgress(progressArray, contentLength))
                        }
                        progressArray.set(partIndex, rangeLength)
                        checkpointStore.updateRange(partIndex, rangeLength, force = true)
                    }
                }.awaitAll()
            }
        } finally {
            checkpointStore.save(force = true)
        }

        if (outputFile.length() != contentLength) {
            throw Exception("文件大小校验失败")
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

    private fun buildRanges(contentLength: Long, partCount: Int): MutableList<DownloadCheckpointRange> {
        val partSize = contentLength / partCount
        return (0 until partCount).map { partIndex ->
            val start = partIndex * partSize
            val end = if (partIndex == partCount - 1) contentLength - 1 else start + partSize - 1
            DownloadCheckpointRange(start = start, end = end, downloaded = 0L)
        }.toMutableList()
    }

    private fun prepareCheckpoint(
        outputFile: File,
        mode: String,
        identity: String,
        remoteFileInfo: RemoteFileInfo,
        expectedRanges: MutableList<DownloadCheckpointRange>
    ): CheckpointStore {
        val checkpointFile = checkpointFile(outputFile)
        val existingCheckpoint = readCheckpoint(checkpointFile)

        if (
            existingCheckpoint != null &&
            isCheckpointCompatible(existingCheckpoint, mode, identity, remoteFileInfo, expectedRanges)
        ) {
            val multiCheckpointNeedsFile =
                mode == CHECKPOINT_MODE_MULTI &&
                        (!outputFile.exists() || outputFile.length() != remoteFileInfo.contentLength)
            if (!multiCheckpointNeedsFile) {
                val store = CheckpointStore(checkpointFile, existingCheckpoint)
                store.save(force = true)
                return store
            }
        }

        resetDownloadState(outputFile)
        val checkpoint = DownloadCheckpoint(
            version = CHECKPOINT_VERSION,
            mode = mode,
            identity = identity,
            contentLength = remoteFileInfo.contentLength,
            eTag = remoteFileInfo.eTag,
            lastModified = remoteFileInfo.lastModified,
            ranges = expectedRanges
        )
        return CheckpointStore(checkpointFile, checkpoint).also { it.save(force = true) }
    }

    private fun isCheckpointCompatible(
        checkpoint: DownloadCheckpoint,
        mode: String,
        identity: String,
        remoteFileInfo: RemoteFileInfo,
        expectedRanges: List<DownloadCheckpointRange>
    ): Boolean {
        val ranges = checkpoint.ranges ?: return false
        if (checkpoint.version != CHECKPOINT_VERSION) return false
        if (checkpoint.mode != mode) return false
        if (checkpoint.identity != identity) return false
        if (checkpoint.contentLength != remoteFileInfo.contentLength) return false
        if (!validatorMatches(checkpoint.eTag, remoteFileInfo.eTag)) return false
        if (!validatorMatches(checkpoint.lastModified, remoteFileInfo.lastModified)) return false
        if (ranges.size != expectedRanges.size) return false

        return ranges.indices.all { index ->
            val saved = ranges[index]
            val expected = expectedRanges[index]
            val length = saved.end - saved.start + 1
            saved.start == expected.start &&
                    saved.end == expected.end &&
                    length > 0 &&
                    saved.downloaded in 0..length
        }
    }

    private fun validatorMatches(saved: String?, current: String?): Boolean {
        return saved.isNullOrBlank() || current.isNullOrBlank() || saved == current
    }

    private fun readCheckpoint(file: File): DownloadCheckpoint? {
        return runCatching {
            checkpointGson.fromJson(file.readText(), DownloadCheckpoint::class.java)
        }.getOrNull()
    }

    private fun checkpointFile(outputFile: File): File {
        val parent = outputFile.parentFile
        return if (parent != null) {
            File(parent, "${outputFile.name}.download.json")
        } else {
            File("${outputFile.name}.download.json")
        }
    }

    private fun deleteCheckpoint(outputFile: File) {
        val checkpointFile = checkpointFile(outputFile)
        checkpointFile.delete()
        File("${checkpointFile.absolutePath}.tmp").delete()
    }

    private fun resetDownloadState(outputFile: File) {
        outputFile.delete()
        deleteCheckpoint(outputFile)
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
                        RemoteFileInfo(
                            contentLength = totalLength,
                            supportsRange = totalLength > 0,
                            eTag = response.header("ETag"),
                            lastModified = response.header("Last-Modified")
                        )
                    }

                    HttpURLConnection.HTTP_OK -> {
                        RemoteFileInfo(
                            contentLength = response.body.contentLength(),
                            supportsRange = false,
                            eTag = response.header("ETag"),
                            lastModified = response.header("Last-Modified")
                        )
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
