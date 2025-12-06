package com.guang.cloudx.ui.downloadManager

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.database.DownloadInfo
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskStatus { DOWNLOADING, FAILED, COMPLETED }

data class DownloadItemUi(
    val music: Music,
    val progress: Int,
    val status: TaskStatus,
    val timeStamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null
)

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()

    private val _downloading = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val downloading: StateFlow<List<DownloadItemUi>> = _downloading

    private val _completed = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val completed: StateFlow<List<DownloadItemUi>> = _completed

    init {
        loadAllTasks()
    }

    private fun loadAllTasks() {
        viewModelScope.launch {
            _downloading.value = downloadDao.getDownloadsByStatus(TaskStatus.DOWNLOADING)
                .map { it.toDownloadItemUi() }
            _completed.value = downloadDao.getDownloadsByStatus(TaskStatus.COMPLETED)
                .map { it.toDownloadItemUi() }
        }
    }

    /** 启动下载 */
    fun startDownloads(
        context: Context,
        musics: List<Music>,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules
    ) {
        val timeStampList = mutableListOf<Long>()
        viewModelScope.launch {
            musics.forEach { music ->
                val timeStamp = System.currentTimeMillis()
                val newTask = DownloadItemUi(
                    music = music,
                    progress = 0,
                    status = TaskStatus.DOWNLOADING,
                    timeStamp = timeStamp,
                )
                downloadDao.insert(newTask.toDownloadInfo())
                timeStampList.add(timeStamp)
                _downloading.update { it + newTask }
            }

            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("musicsJson", Gson().toJson(musics))
                putExtra("rulesJson", Gson().toJson(rules))
                putExtra("timeStampList", Gson().toJson(timeStampList))
                putExtra("cookie", cookie)
                putExtra("level", level)
                putExtra("targetUri", targetDir.uri)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    /** 失败 → 重试 */
    fun retryDownload(
        context: Context,
        item: DownloadItemUi,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules
    ) {
        viewModelScope.launch {
            downloadDao.delete(item.toDownloadInfo())
            _downloading.update { it.filterNot { t -> t.music == item.music } }
            startDownloads(context, listOf(item.music), level, cookie, targetDir, rules)
        }
    }

    /** 删除失败任务 */
    fun deleteFailed(item: DownloadItemUi) {
        viewModelScope.launch {
            downloadDao.delete(item.toDownloadInfo())
            _downloading.update { it.filterNot { t -> t.timeStamp == item.timeStamp } }
        }
    }

    /** 删除已完成任务 */
    fun deleteCompleted(item: DownloadItemUi, deletedSavedData: () -> Unit) {
        viewModelScope.launch {
            downloadDao.delete(item.toDownloadInfo())
            _completed.update { it.filterNot { t -> t.timeStamp == item.timeStamp && t.music == item.music } }
            deletedSavedData()
        }
    }

    /** 删除全部已完成 */
    fun deleteAllCompleted(deletedSavedData: () -> Unit) {
        viewModelScope.launch {
            downloadDao.deleteAll()
            _completed.value = emptyList()
            deletedSavedData()
        }
    }

    /** 下载完成 → 移动到 completed */
    private fun moveToCompleted(music: Music, timeStamp: Long) {
         viewModelScope.launch {
            val task = _downloading.value.find { it.music == music && it.timeStamp == timeStamp }
            if (task != null) {
                val finished = task.copy(status = TaskStatus.COMPLETED, progress = 100)
                downloadDao.update(finished.toDownloadInfo())
                _downloading.update { it.filterNot { it.music == music && it.timeStamp == timeStamp } }
                _completed.update { it + finished }
            }
        }
    }

    /** 标记失败 */
    private fun markAsFailed(music: Music, reason: String? = null) {
        viewModelScope.launch {
            val task = _downloading.value.find { it.music == music }
            if (task != null) {
                val failedTask = task.copy(status = TaskStatus.FAILED, progress = 0, failureReason = reason)
                downloadDao.update(failedTask.toDownloadInfo())
                _downloading.update { list ->
                    list.map {
                        if (it.music == music) failedTask else it
                    }
                }
            }
        }
    }

    fun updateProgressById(intent: Intent?, onFinished: () -> Unit) {
        val music = Gson().fromJson(intent?.getStringExtra("musicJson"), Music::class.java)
        val progress = intent?.getIntExtra("progress", 0) ?: 0
        val timeStamp = intent?.getLongExtra("timeStamp", 0) ?: 0
        val failedReason = intent?.getStringExtra("reason") ?: "未知原因"
        when (intent?.action) {
            "DOWNLOAD_PROGRESS" -> {
                _downloading.update { list ->
                    list.map {
                        if (it.music == music && it.timeStamp == timeStamp) it.copy(
                            progress = progress,
                            status = if (progress == 100) TaskStatus.COMPLETED else TaskStatus.DOWNLOADING
                        ) else it
                    }
                }
            }

            "DOWNLOAD_COMPLETED" -> {
                val finishedMusic =
                    _downloading.value.find { it.music == music && it.timeStamp == timeStamp }?.music ?: return
                moveToCompleted(finishedMusic, timeStamp)
            }

            "DOWNLOAD_FINISHED" -> {
                onFinished()
            }

            "DOWNLOAD_FAILED" -> {
                markAsFailed(music, failedReason)
            }
        }
    }

    private fun DownloadInfo.toDownloadItemUi(): DownloadItemUi {
        return DownloadItemUi(
            music = this.music,
            progress = this.progress,
            status = this.status,
            timeStamp = this.timeStamp,
            failureReason = this.failureReason
        )
    }

    private fun DownloadItemUi.toDownloadInfo(): DownloadInfo {
        return DownloadInfo(
            music = this.music,
            progress = this.progress,
            status = this.status,
            timeStamp = this.timeStamp,
            failureReason = this.failureReason
        )
    }
}
