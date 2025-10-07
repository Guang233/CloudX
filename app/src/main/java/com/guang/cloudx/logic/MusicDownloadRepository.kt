package com.guang.cloudx.logic

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.network.MusicNetwork
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MusicDownloadRepository(
    maxParallel: Int
) : ViewModelProvider.Factory {
    private val semaphore = Semaphore(maxParallel)

    suspend fun downloadMusicList(
        context: Context,
        isSaveLrc: Boolean,
        musics: List<Music>,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        onProgress: (Music, Int) -> Unit
    ) {
        coroutineScope {
            musics.forEach { music ->
                launch {
                    semaphore.withPermit {
                        try {
                            val musicUrl = MusicNetwork.getMusicUrl(music.id.toString(), level, cookie)
                            val quality = when (musicUrl.level) {
                                "standard" -> ""
                                "exhigh" -> "[HQ]"
                                "lossless" -> "[SQ]"
                                "hires" -> "[HR]"
                                else -> ""
                            }
                            val fileName = "$quality${music.name}-${music.artists.joinToString("_") { it.name }}"

                            val musicFile = targetDir.createFile("audio/*", fileName)
                                ?: throw Exception("无法创建音乐文件")

                            downloadFile(
                                context,
                                musicUrl.url,
                                documentFile = musicFile
                            ) { progress ->
                                onProgress(music, progress)
                            }

                            val ext = detectFileType(context, musicFile)
                            musicFile.renameTo("$fileName.$ext")

                            val coverFile = targetDir.createFile("image/jpeg", "${music.id}.jpg")
                                ?: throw Exception("无法创建封面文件")
                            downloadFile(
                                context,
                                music.album.picUrl,
                                documentFile = coverFile
                            )
                            coverFile.delete()

                            // 写入歌词
                            val lrc = createLyrics(MusicNetwork.getLyrics(music.id.toString(), cookie), music)
                            if (isSaveLrc) {
                                val lrcFile = targetDir.createFile("application/octet-stream", "$fileName.lrc")
                                if (lrcFile != null) {
                                    context.contentResolver.openOutputStream(lrcFile.uri)?.use { output ->
                                        output.write(lrc.toByteArray())
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            throw e
                        }
                    }
                }
            }
        }
    }


    suspend fun downloadMusic(
        context: Context,
        isSaveLrc: Boolean,
        music: Music,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        onProgress: (Music, Int) -> Unit
    ) = downloadMusicList(context, isSaveLrc, listOf(music), level, cookie, targetDir, onProgress)

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

    fun detectFileType(context: Context, docFile: DocumentFile): String {
        val buffer = ByteArray(12)
        context.contentResolver.openInputStream(docFile.uri)?.use { it.read(buffer) }

        val hex = buffer.joinToString(" ") { String.format("%02X", it) }

        return when {
            hex.startsWith("49 44 33") || hex.startsWith("FF FB") -> "mp3"
            hex.startsWith("FF F1") || hex.startsWith("FF F9") -> "aac"
            hex.startsWith("52 49 46 46") && String(buffer.copyOfRange(8, 12)) == "WAVE" -> "wav"
            hex.startsWith("66 4C 61 43") -> "flac"
            hex.startsWith("4F 67 67 53") -> "ogg"
            hex.startsWith("00 00 00") && hex.contains("66 74 79 70") -> "m4a"
            hex.startsWith("FF D8 FF") -> "jpg"
            hex.startsWith("89 50 4E 47") -> "png"
            else -> "unknown"
        }
    }

    private fun createLyrics(lrc: Lyric, music: Music): String = """
[ti:${music.name}]
[ar:${music.artists.joinToString("、") { it.name }}]
[al:${music.album.name}]

${lrc.lrc}

${lrc.tlyric}

${lrc.romalrc}
""".trimIndent()
}


