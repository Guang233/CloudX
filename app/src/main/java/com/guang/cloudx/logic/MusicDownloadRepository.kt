package com.guang.cloudx.logic

import androidx.lifecycle.ViewModelProvider
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.network.MusicNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
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
                            val fileName = "${music.name}-${music.artists.joinToString("_") { it.name }}.mp3"
                            val file = File(targetDir, fileName)

                            downloadFile(musicUrl.url, file) { progress ->
                                onProgress(music, progress)
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
        music: Music,
        level: String,
        cookie: String,
        targetDir: File,
        onProgress: (Music, Int) -> Unit
    ) = downloadMusicList(listOf(music), level, cookie, targetDir, onProgress)

    private suspend fun downloadFile(
        url: String,
        file: File,
        progressCallback: (Int) -> Unit
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
}


