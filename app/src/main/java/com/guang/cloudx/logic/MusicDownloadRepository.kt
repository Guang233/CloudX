package com.guang.cloudx.logic

import androidx.lifecycle.ViewModelProvider
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.network.MusicNetwork
import com.guang.cloudx.logic.utils.AudioTagWriter
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MusicDownloadRepository(
    private val maxParallel: Int
) : ViewModelProvider.Factory {
    private val semaphore = Semaphore(maxParallel)

    suspend fun downloadMusicList(
        musics: List<Music>,
        level: String,
        cookie: String,
        targetDir: File,
        onProgress: (Music, Int) -> Unit
    ) {
        coroutineScope {
            musics.forEach { music ->
                launch {
                    semaphore.withPermit {
                        try {
                            val musicUrl = MusicNetwork.getMusicUrl(music.id.toString(), level, cookie)
                            val quality = when(musicUrl.level) {
                                "standard" -> ""
                                "exhigh" -> "[HQ]"
                                "lossless" -> "[SQ]"
                                "hires" -> "[HR]"
                                else -> ""
                            }
                            val fileName = "$quality${music.name}-${music.artists.joinToString("_") { it.name }}"
                            val file = File(targetDir, fileName)

                            downloadFile(musicUrl.url, file) { progress ->
                                onProgress(music, progress)
                            }

                            val ext = detectFileType(file)
                            val finalFile = File(file.parent, "$fileName.$ext")
                            if (finalFile.exists()) finalFile.delete()
                            file.renameTo(finalFile)

                            val coverFile = File(targetDir, "${music.id}.jpg")
                            downloadFile(music.album.picUrl, coverFile)
                            val lrc = createLyrics(MusicNetwork.getLyrics(music.id.toString(), cookie), music)
                            AudioTagWriter.writeTags(finalFile,
                                AudioTagWriter.TagInfo(
                                    title = music.name,
                                    artist = music.artists.joinToString("、") { it.name },
                                    album = music.album.name,
                                    lyrics = lrc,
                                    coverFile = coverFile
                                )
                            )
                            coverFile.delete()
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                }
            }
        }
    }

    suspend fun downloadMusic(
        music: Music,
        level: String,
        cookie: String,
        targetDir: File,
        onProgress: (Music, Int) -> Unit
    ) = downloadMusicList(listOf(music), level, cookie, targetDir, onProgress)

    private suspend fun downloadFile(
        url: String,
        file: File,
        progressCallback: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // 设置超时
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"

            // 检查 HTTP 状态码
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: ${conn.responseCode}")
            }

            val total: Long = conn.contentLengthLong
            var downloaded: Long = 0

            val buffer = ByteArray(8 * 1024)

            conn.inputStream.use { input: InputStream ->
                FileOutputStream(file).use { output ->
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        ensureActive() // 支持协程取消
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (total > 0) { // 只有在 Content-Length 有效时才计算进度
                            progressCallback(((downloaded * 100) / total).toInt())
                        }
                        bytes = input.read(buffer)
                    }
                    output.flush()
                }
            }

        } finally {
            conn.disconnect()
        }
    }

    private fun detectFileType(file: File): String {
        val buffer = ByteArray(12)
        FileInputStream(file).use { it.read(buffer) }

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


