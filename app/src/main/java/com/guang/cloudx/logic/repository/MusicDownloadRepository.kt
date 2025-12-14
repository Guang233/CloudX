package com.guang.cloudx.logic.repository

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.network.MusicNetwork
import com.guang.cloudx.logic.utils.AudioTagWriter
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong

class MusicDownloadRepository : ViewModelProvider.Factory {

    suspend fun downloadMusic(
        context: Context,
        rules: MusicDownloadRules,
        music: Music,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        onProgress: (Music, Int) -> Unit
    ) {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val tmpFile = File(cacheDir, music.id.toString())

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

            // 2. 执行分块下载
            if (rules.concurrentDownloads > 1) {
                downloadConcurrently(musicUrl.url, tmpFile, rules.concurrentDownloads) { progress ->
                    onProgress(music, progress)
                }
            } else {
                downloadFile(url = musicUrl.url, file = tmpFile) { progress ->
                    onProgress(music, progress)
                }
            }

            // 3. 检测类型并重命名
            val ext = detectFileTypeFromFile(tmpFile)
            val tmpWithExt = File(cacheDir, "$baseFileName.$ext")
            if (tmpWithExt.exists()) tmpWithExt.delete()
            tmpFile.renameTo(tmpWithExt)

            // 4. 下载封面
            val tmpCover = File(cacheDir, "${music.id}.jpg")
            downloadFile(url = music.album.picUrl, file = tmpCover)

            // 5. 获取歌词
            val lrc = MusicNetwork.getLyrics(music.id.toString(), cookie)
            val lrcText = lrc.let { if (it.lrc != "") createLyrics(lrc, music, rules) else null }

            // 6. 写入元数据
            AudioTagWriter.writeTags(
                tmpWithExt,
                AudioTagWriter.TagInfo(
                    title = music.name,
                    artist = music.artists.joinToString(rules.delimiter) { it.name },
                    album = music.album.name,
                    coverFile = tmpCover,
                    lyrics = lrcText
                )
            )
            tmpCover.delete()

            // 7. 移动到最终位置
            copyToSaf(context, tmpWithExt, targetDir, "$baseFileName.$ext")

            // 8. 写入歌词文件
            if (rules.isSaveLrc && lrcText != null) {
                writeLrcToSaf(context, lrcText, targetDir, "$baseFileName.lrc", rules.encoding)
            }

            // 9. 清理临时文件
            tmpWithExt.delete()

        } catch (e: Exception) {
            tmpFile.delete()
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
        val contentLength = getRemoteFileSize(url)
        if (contentLength <= 0) { // 如果无法获取文件大小，则回退到单线程下载
            downloadFile(url = url, file = outputFile, progressCallback = onProgress)
            return@withContext
        }

        val partSize = contentLength / parts
        val totalDownloaded = AtomicLong(0)
        val progressArray = LongArray(parts)

        val parentDir = outputFile.parentFile!!
        val partFiles = (0 until parts).map { File(parentDir, "${outputFile.name}.part$it") }

        try {
            coroutineScope {
                (0 until parts).map { partIndex ->
                    async {
                        val start = partIndex * partSize
                        val end = if (partIndex == parts - 1) contentLength - 1 else start + partSize - 1
                        downloadChunk(url, partFiles[partIndex], start, end) { downloaded ->
                            progressArray[partIndex] = downloaded
                            val currentTotal = progressArray.sum()
                            totalDownloaded.set(currentTotal)
                            onProgress((currentTotal * 100 / contentLength).toInt())
                        }
                    }
                }.awaitAll()
            }

            // 合并文件
            outputFile.outputStream().use { output ->
                partFiles.forEach { partFile ->
                    partFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
        } finally {
            // 清理分块文件
            partFiles.forEach { it.delete() }
        }
    }

    private suspend fun downloadChunk(
        url: String,
        outputFile: File,
        start: Long,
        end: Long,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Range", "bytes=$start-$end")
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.connect()

            if (conn.responseCode !in 200..299) {
                throw Exception("HTTP error: ${conn.responseCode} for range $start-$end")
            }

            var downloaded = 0L
            val buffer = ByteArray(8 * 1024)
            conn.inputStream.use { input ->
                outputFile.outputStream().use { output ->
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
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun getRemoteFileSize(url: String): Long = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.connect()
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                return@withContext conn.contentLengthLong
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            conn?.disconnect()
        }
        return@withContext -1L
    }

    suspend fun downloadFile(
        context: Context? = null,
        url: String,
        file: File? = null,
        documentFile: DocumentFile? = null,
        progressCallback: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(file != null || documentFile != null) { "必须提供 file 或 documentFile" }

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: ${conn.responseCode}")
            }

            val total: Long = conn.contentLengthLong
            var downloaded: Long = 0
            val buffer = ByteArray(8 * 1024)

            val output: OutputStream = when {
                file != null -> file.outputStream()
                documentFile != null -> {
                    context!!.contentResolver.openOutputStream(documentFile.uri)
                        ?: throw Exception("无法打开 OutputStream")
                }
                else -> throw IllegalStateException()
            }

            conn.inputStream.use { input: InputStream ->
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
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun copyToSaf(context: Context, sourceFile: File, targetDir: DocumentFile, finalFileName: String) = withContext(Dispatchers.IO) {
        val existing = targetDir.findFile(finalFileName)
        if (existing != null) {
            try {
                context.contentResolver.openFileDescriptor(existing.uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                } ?: throw Exception("无法打开现有文件")
            } catch (e: Exception) {
                context.contentResolver.delete(existing.uri, null, null)
                val newDoc = targetDir.createFile("audio/*", finalFileName) ?: throw Exception("无法创建音乐文件")
                context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw Exception("无法打开新文件")
            }
        } else {
            val musicDoc = targetDir.createFile("audio/*", finalFileName) ?: throw Exception("无法创建音乐文件")
            context.contentResolver.openOutputStream(musicDoc.uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw Exception("无法打开目标输出流")
        }
    }

    private suspend fun writeLrcToSaf(context: Context, lrcText: String, targetDir: DocumentFile, lrcName: String, encoding: String) = withContext(Dispatchers.IO) {
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
        val tlLrc = if (rules.isSaveTlLrc) if (rules.isSaveYrc && lyric.ytlrc != "") lyric.ytlrc else lyric.tlyric else ""
        val romaLrc = if (rules.isSaveRomaLrc) if (rules.isSaveYrc && lyric.yromalrc != "") lyric.yromalrc else lyric.romalrc else ""
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