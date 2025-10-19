package com.guang.cloudx.ui.downloadManager

import android.app.Application
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.util.ext.e
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

    private val repository: MusicDownloadRepository =  MusicDownloadRepository(3)
    private val _downloading = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val downloading: StateFlow<List<DownloadItemUi>> = _downloading

    private val _completed: MutableStateFlow<List<DownloadItemUi>> by lazy {
        val typeOf = object : TypeToken<List<DownloadItemUi>>() {}.type
        val data = Gson().fromJson<List<DownloadItemUi>>(
            SharedPreferencesUtils(application).getCompletedMusic(),
            typeOf
        ) ?: emptyList()
        MutableStateFlow(data)
    }

    val completed: StateFlow<List<DownloadItemUi>> = _completed

    /** 启动下载 */
    fun startDownloads(context: Context, musics: List<Music>, level: String, cookie: String, targetDir: DocumentFile, rules: MusicDownloadRules) {
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
                        rules,
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
                            moveToCompleted(music) {
                                val typeOf = object: TypeToken<List<DownloadItemUi>>(){}.type
                                val data = Gson().fromJson<List<DownloadItemUi>>(SharedPreferencesUtils(context).getCompletedMusic(), typeOf) ?: listOf()
                                SharedPreferencesUtils(context).putCompletedMusic(
                                    Gson().toJson(data + it)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    markAsFailed(music)
                    e.e()
                }
            }
        }
    }

    /** 失败 → 重试 */
    fun retryDownload(context: Context, item: DownloadItemUi, level: String, cookie: String, targetDir: DocumentFile, rules: MusicDownloadRules) {
        _downloading.update { it.filterNot { t -> t.music == item.music } }
        startDownloads(context,listOf(item.music), level, cookie, targetDir, rules)
    }

    /** 删除失败任务 */
    fun deleteFailed(item: DownloadItemUi) {
        _downloading.update { it.filterNot { t -> t.timeStamp == item.timeStamp } }
    }

    /** 删除已完成任务 */
    fun deleteCompleted(item: DownloadItemUi, deletedSavedData: () -> Unit) {
        _completed.update { it.filterNot { t -> t.timeStamp == item.timeStamp && t.music == item.music } }
        deletedSavedData()
    }

    /** 删除全部已完成 */
    fun deleteAllCompleted(deletedSavedData: () -> Unit) {
        _completed.value = emptyList()
        deletedSavedData()
    }

    /** 下载完成 → 移动到 completed */
    private fun moveToCompleted(music: Music, savedCompletedMusic: (DownloadItemUi)-> Unit) {
        val finished = DownloadItemUi(
            music = music,
            progress = 100,
            status = TaskStatus.COMPLETED
        )
        _downloading.update { it -> it.filterNot { it.music == music } }
        _completed.update { it + finished }
        savedCompletedMusic(finished)
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
