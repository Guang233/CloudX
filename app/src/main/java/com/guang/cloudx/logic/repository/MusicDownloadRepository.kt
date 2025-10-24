package com.guang.cloudx.logic.repository

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.network.MusicNetwork
import com.guang.cloudx.logic.utils.AudioTagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

class MusicDownloadRepository(
    maxParallel: Int
) : ViewModelProvider.Factory {
    private val semaphore = Semaphore(maxParallel)

    // 这个方法，总之，改来改去，改坏了，就都丢给ai了，然后自己小改一下（
    suspend fun downloadMusicList(
        context: Context,
        rules: MusicDownloadRules,
        musics: List<Music>,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        onProgress: (Music, Int) -> Unit
    ) {
        supervisorScope {
            musics.forEach { music ->
                downloadMusic(context, rules, music, level, cookie, targetDir, onProgress)
            }
        }
    }


    suspend fun downloadMusic(
        context: Context,
        rules: MusicDownloadRules,
        music: Music,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        onProgress: (Music, Int) -> Unit
    ) {
        semaphore.withPermit {

            // ========= 1. 临时路径 =========
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val originalFileName = music.id.toString()
            val tmpFile = File(cacheDir, originalFileName)

            try {
                // ========= 2. 下载音乐到临时文件 =========
                val musicUrl = MusicNetwork.getMusicUrl(music.id.toString(), level, cookie)
                val quality = when (musicUrl.level) {
                    "standard" -> ""
                    "exhigh" -> "[HQ]"
                    "lossless" -> "[SQ]"
                    "hires" -> "[HR]"
                    else -> ""
                }
                val baseFileName = rules.fileName.replace($$"${level}", quality)
                    .replace($$"${name}", music.name.replace(Regex("[\\\\/:*?\"<>|]"), " "))
                    .replace($$"${id}", music.id.toString())
                    .replace($$"${artists}", music.artists.joinToString(rules.delimiter) { it.name })
                    .replace($$"${album}", music.album.name.replace(Regex("[\\\\/:*?\"<>|]"), " "))
                    .replace($$"${albumId}", music.album.id.toString())
                downloadFile(
                    context,
                    musicUrl.url,
                    file = tmpFile
                ) { progress -> onProgress(music, progress) }

                // ========= 3. 检测类型并重命名临时文件 =========
                val ext = detectFileTypeFromFile(tmpFile)
                val tmpWithExt = File(cacheDir, "$baseFileName.$ext")
                if (tmpWithExt.exists()) tmpWithExt.delete()
                tmpFile.renameTo(tmpWithExt)

                // ========= 4. 下载封面临时文件 =========
                val tmpCover = File(cacheDir, "${music.id}.jpg")
                downloadFile(
                    context,
                    music.album.picUrl,
                    file = tmpCover
                )

                val lrc = MusicNetwork.getLyrics(music.id.toString(), cookie)
                val lrcText = lrc.let { if (it.lrc != "") createLyrics(lrc, music, rules) else null }

                // ========= 5. 写入元数据 =========
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

                // ========= 6. 复制到 SAF 目录 =========
                val finalFileName = "$baseFileName.$ext"
                val existing = targetDir.findFile(finalFileName)

                if (existing != null) {
                    // 如果存在，尽量直接以 "w" 模式打开并写入（会截断覆盖）
                    try {
                        context.contentResolver.openFileDescriptor(existing.uri, "w")?.use { pfd ->
                            FileOutputStream(pfd.fileDescriptor).use { out ->
                                tmpWithExt.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                                out.flush()
                            }
                        } ?: throw Exception("无法打开现有文件输出流")
                    } catch (e: Exception) {
                        // 若 provider 不支持直接写（罕见），回退为删除后创建
                        context.contentResolver.delete(existing.uri, null, null)
                        val musicDoc = targetDir.createFile("audio/*", finalFileName)
                            ?: throw Exception("无法创建音乐文件")
                        context.contentResolver.openOutputStream(musicDoc.uri)?.use { out ->
                            tmpWithExt.inputStream().use { input -> input.copyTo(out) }
                            out.flush()
                        } ?: throw Exception("无法打开目标输出流")
                    }
                } else {
                    // 不存在则直接创建
                    val musicDoc = targetDir.createFile("audio/*", finalFileName)
                        ?: throw Exception("无法创建音乐文件")
                    context.contentResolver.openOutputStream(musicDoc.uri)?.use { out ->
                        tmpWithExt.inputStream().use { input -> input.copyTo(out) }
                        out.flush()
                    } ?: throw Exception("无法打开目标输出流")
                }


                // ========= 7. 写入 .lrc =========
                if (rules.isSaveLrc && lrcText != null) {
                    val lrcName = "$baseFileName.lrc"
                    val existingLrc = targetDir.findFile(lrcName)
                    if (existingLrc != null) {
                        try {
                            context.contentResolver.openFileDescriptor(existingLrc.uri, "w")?.use { pfd ->
                                FileOutputStream(pfd.fileDescriptor).use { out ->
                                    out.write(lrcText.toByteArray(Charset.forName(rules.encoding)))
                                }
                            } ?: throw Exception("无法打开已存在的 lrc 输出流")
                        } catch (e: Exception) {
                            // fallback: 删除后创建
                            context.contentResolver.delete(existingLrc.uri, null, null)
                            val lrcDoc = targetDir.createFile("application/octet-stream", lrcName)
                            lrcDoc?.let {
                                context.contentResolver.openOutputStream(it.uri)?.use { out ->
                                    out.write(lrcText.toByteArray(Charset.forName(rules.encoding)))
                                    out.flush()
                                }
                            }
                        }
                    } else {
                        val lrcDoc = targetDir.createFile("application/octet-stream", lrcName)
                        lrcDoc?.let {
                            context.contentResolver.openOutputStream(it.uri)?.use { out ->
                                out.write(lrcText.toByteArray(Charset.forName(rules.encoding)))
                                out.flush()
                            }
                        }
                    }

                }

                // ========= 8. 删除临时文件 =========
                tmpWithExt.delete()

            } catch (e: Exception) {
                tmpFile.delete()
                throw e
            }
        }
    }

    suspend fun downloadFile(
        context: Context,
        url: String,
        file: File? = null,                // 如果是普通 File
        documentFile: DocumentFile? = null, // 如果是 SAF 目录的 DocumentFile
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
                    context.contentResolver.openOutputStream(documentFile.uri)
                        ?: throw Exception("无法打开 OutputStream")
                }

                else -> throw IllegalStateException()
            }

            conn.inputStream.use { input: InputStream ->
                output.use { out ->
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        ensureActive() // 支持协程取消
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


