package com.guang.cloudx.ui.downloadManager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.guang.cloudx.logic.MusicDownloadRepository
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Artist
import com.guang.cloudx.logic.model.Music
import java.io.File

enum class TaskStatus { DOWNLOADING, FAILED, COMPLETED }

data class DownloadItemUi(
    val id: Long,
    val coverUrl: String,
    val title: String,
    val artist: String,
    val progress: Int,
    val status: TaskStatus
)

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: MusicDownloadRepository =  MusicDownloadRepository(5)
    private val _downloading = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val downloading: StateFlow<List<DownloadItemUi>> = _downloading

    private val _completed = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val completed: StateFlow<List<DownloadItemUi>> = _completed

    /** 启动下载（支持批量） */
    fun startDownloads(musics: List<Music>, level: String, cookie: String, targetDir: File) {
        musics.forEach { music ->
            val newTask = DownloadItemUi(
                id = music.id,
                coverUrl = music.album.picUrl,
                title = music.name,
                artist = music.artists.joinToString("、") { it.name },
                progress = 0,
                status = TaskStatus.DOWNLOADING
            )
            _downloading.update { it + newTask }

            viewModelScope.launch {
                try {
                    repository.downloadMusic(
                        music,
                        level,
                        cookie,
                        targetDir
                    ) { m, progress ->
                        // 更新下载进度
                        _downloading.update { list ->
                            list.map {
                                if (it.id == m.id) it.copy(
                                    progress = progress,
                                    status = if (progress == 100) TaskStatus.COMPLETED else TaskStatus.DOWNLOADING
                                ) else it
                            }
                        }

                        if (progress == 100) {
                            moveToCompleted(music)
                        }
                    }
                } catch (e: Exception) {
                    markAsFailed(music)
                }
            }
        }
    }

    /** 失败 → 重试 */
    fun retryDownload(item: DownloadItemUi, level: String, cookie: String, targetDir: File) {
        _downloading.update { it.filterNot { t -> t.id == item.id } }
        val music = Music(item.title, listOf(Artist(item.artist, 0)), Album("", 0, item.coverUrl), item.id)
        startDownloads(listOf(music), level, cookie, targetDir)
    }

    /** 删除失败任务 */
    fun deleteFailed(item: DownloadItemUi) {
        _downloading.update { it.filterNot { t -> t.id == item.id } }
    }

    /** 删除已完成任务 */
    fun deleteCompleted(item: DownloadItemUi) {
        _completed.update { it.filterNot { t -> t.id == item.id } }
        // TODO 删除本地文件
    }

    /** 删除全部已完成 */
    fun deleteAllCompleted() {
        _completed.value = emptyList()
        // TODO 删除本地文件
    }

    /** 下载完成 → 移动到 completed */
    private fun moveToCompleted(music: Music) {
        val finished = DownloadItemUi(
            id = music.id,
            coverUrl = music.album.picUrl,
            title = music.name,
            artist = music.artists.joinToString("、") { it.name },
            progress = 100,
            status = TaskStatus.COMPLETED
        )
        _downloading.update { it.filterNot { it.id == music.id } }
        _completed.update { it + finished }
    }

    /** 标记失败 */
    private fun markAsFailed(music: Music) {
        _downloading.update { list ->
            list.map {
                if (it.id == music.id) it.copy(status = TaskStatus.FAILED, progress = 0) else it
            }
        }
    }
}
