package com.guang.cloudx.ui.downloadManager

import android.app.Application
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.MusicDownloadRepository
import com.guang.cloudx.logic.model.Music
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskStatus { DOWNLOADING, FAILED, COMPLETED }

data class DownloadItemUi(
    val music: Music,
    val progress: Int,
    val status: TaskStatus,
    val timeStamp: Long = System.currentTimeMillis()
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
    fun startDownloads(context: Context, musics: List<Music>, level: String, cookie: String, targetDir: DocumentFile, isSaveLrc: Boolean, isSaveTlLrc: Boolean, isSaveRomaLrc: Boolean) {
        musics.forEach { music ->
            val newTask = DownloadItemUi(
                music = music,
                progress = 0,
                status = TaskStatus.DOWNLOADING
            )
            _downloading.update { it + newTask }

            viewModelScope.launch {
                try {
                    repository.downloadMusic(
                        context,
                        isSaveLrc,
                        isSaveTlLrc,
                        isSaveRomaLrc,
                        music,
                        level,
                        cookie,
                        targetDir,
                    ) { m, progress ->
                        // 更新下载进度
                        _downloading.update { list ->
                            list.map {
                                if (it.music == m) it.copy(
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
    fun retryDownload(context: Context, item: DownloadItemUi, level: String, cookie: String, targetDir: DocumentFile, isSaveLrc: Boolean, isSaveTlLrc: Boolean, isSaveRomaLrc: Boolean) {
        _downloading.update { it.filterNot { t -> t.music == item.music } }
        startDownloads(context,listOf(item.music), level, cookie, targetDir, isSaveLrc, isSaveTlLrc, isSaveRomaLrc)
    }

    /** 删除失败任务 */
    fun deleteFailed(item: DownloadItemUi) {
        _downloading.update { it.filterNot { t -> t.timeStamp == item.timeStamp } }
    }

    /** 删除已完成任务 */
    fun deleteCompleted(item: DownloadItemUi) {

        _completed.update { it.filterNot { t -> t.timeStamp == item.timeStamp } }
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
            music = music,
            progress = 100,
            status = TaskStatus.COMPLETED
        )
        _downloading.update { it -> it.filterNot { it.music == music } }
        _completed.update { it + finished }
    }

    /** 标记失败 */
    private fun markAsFailed(music: Music) {
        _downloading.update { list ->
            list.map {
                if (it.music == music) it.copy(status = TaskStatus.FAILED, progress = 0) else it
            }
        }
    }
}
