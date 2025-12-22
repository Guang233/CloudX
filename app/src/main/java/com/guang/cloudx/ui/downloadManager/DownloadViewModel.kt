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
    val id: Long = 0,
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
            // 如果服务不在运行，才将正在下载的任务标记为失败
            if (!DownloadService.isRunning) {
                val downloadingTasks = downloadDao.getDownloadsByStatus(TaskStatus.DOWNLOADING)
                downloadingTasks.forEach {
                    it.status = TaskStatus.FAILED
                    it.failureReason = "下载已中断"
                    downloadDao.update(it)
                }
            }

            _downloading.value =
                (downloadDao.getDownloadsByStatus(TaskStatus.DOWNLOADING).map { it.toDownloadItemUi() } +
                        downloadDao.getDownloadsByStatus(TaskStatus.FAILED)
                            .map { it.toDownloadItemUi() }).distinctBy { it.id }
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
        viewModelScope.launch {
            val musicIdToDbIdMap = mutableMapOf<Long, Long>()
            val newTasks = mutableListOf<DownloadItemUi>()

            musics.forEach { music ->
                val newInfo = DownloadInfo(
                    music = music,
                    progress = 0,
                    status = TaskStatus.DOWNLOADING,
                    timeStamp = System.currentTimeMillis()
                )
                val id = downloadDao.insert(newInfo)
                musicIdToDbIdMap[music.id] = id
                newTasks.add(newInfo.copy(id = id).toDownloadItemUi())
            }

            _downloading.update { it + newTasks }

            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("musicsJson", Gson().toJson(musics))
                putExtra("rulesJson", Gson().toJson(rules))
                putExtra("musicIdToDbIdMapJson", Gson().toJson(musicIdToDbIdMap))
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
            _downloading.update { it.filterNot { t -> t.id == item.id } }
            startDownloads(context, listOf(item.music), level, cookie, targetDir, rules)
        }
    }

    /** 全部重试失败任务 */
    fun retryAllFailed(
        context: Context,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules
    ) {
        viewModelScope.launch {
            val failedTasks = _downloading.value.filter { it.status == TaskStatus.FAILED }
            if (failedTasks.isEmpty()) return@launch

            // 删除旧的失败记录
            failedTasks.forEach { downloadDao.delete(it.toDownloadInfo()) }
            _downloading.update { it.filterNot { t -> t.status == TaskStatus.FAILED } }

            // 重新开始下载
            startDownloads(context, failedTasks.map { it.music }, level, cookie, targetDir, rules)
        }
    }

    /** 删除失败任务 */
    fun deleteFailed(item: DownloadItemUi) {
        viewModelScope.launch {
            downloadDao.delete(item.toDownloadInfo())
            _downloading.update { it.filterNot { t -> t.id == item.id } }
        }
    }

    /** 删除所有失败任务 */
    fun deleteAllFailed() {
        viewModelScope.launch {
            val failedTasks = _downloading.value.filter { it.status == TaskStatus.FAILED }
            failedTasks.forEach { downloadDao.delete(it.toDownloadInfo()) }
            _downloading.update { it.filterNot { t -> t.status == TaskStatus.FAILED } }
        }
    }

    /** 删除已完成任务 */
    fun deleteCompleted(item: DownloadItemUi, deletedSavedData: () -> Unit) {
        viewModelScope.launch {
            downloadDao.delete(item.toDownloadInfo())
            _completed.update { it.filterNot { t -> t.id == item.id } }
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
    private fun moveToCompleted(dbId: Long) {
        viewModelScope.launch {
            val task = _downloading.value.find { it.id == dbId }
            if (task != null) {
                val finished = task.copy(status = TaskStatus.COMPLETED, progress = 100)
                downloadDao.update(finished.toDownloadInfo())
                _downloading.update { it.filterNot { it.id == dbId } }
                _completed.update { it + finished }
            }
        }
    }

    /** 标记失败 */
    private fun markAsFailed(dbId: Long, reason: String? = null) {
        viewModelScope.launch {
            val task = _downloading.value.find { it.id == dbId }
            if (task != null) {
                val failedTask = task.copy(status = TaskStatus.FAILED, progress = 0, failureReason = reason)
                downloadDao.update(failedTask.toDownloadInfo())
                _downloading.update { list ->
                    list.map {
                        if (it.id == dbId) failedTask else it
                    }
                }
            }
        }
    }

    fun updateProgressById(intent: Intent?, onFinished: () -> Unit) {
        val dbId = intent?.getLongExtra("dbId", 0L) ?: 0L
        if (dbId == 0L) return

        val progress = intent?.getIntExtra("progress", 0) ?: 0
        val failedReason = intent?.getStringExtra("reason") ?: "未知原因"
        when (intent?.action) {
            "DOWNLOAD_PROGRESS" -> {
                _downloading.update { list ->
                    list.map {
                        if (it.id == dbId) it.copy(
                            progress = progress,
                            status = if (progress == 100) TaskStatus.COMPLETED else TaskStatus.DOWNLOADING
                        ) else it
                    }
                }
            }

            "DOWNLOAD_COMPLETED" -> {
                moveToCompleted(dbId)
            }

            "DOWNLOAD_FINISHED" -> {
                onFinished()
            }

            "DOWNLOAD_FAILED" -> {
                markAsFailed(dbId, failedReason)
            }
        }
    }

    private fun DownloadInfo.toDownloadItemUi(): DownloadItemUi {
        return DownloadItemUi(
            id = this.id,
            music = this.music,
            progress = this.progress,
            status = this.status,
            timeStamp = this.timeStamp,
            failureReason = this.failureReason
        )
    }

    private fun DownloadItemUi.toDownloadInfo(): DownloadInfo {
        return DownloadInfo(
            id = this.id,
            music = this.music,
            progress = this.progress,
            status = this.status,
            timeStamp = this.timeStamp,
            failureReason = this.failureReason
        )
    }
}
