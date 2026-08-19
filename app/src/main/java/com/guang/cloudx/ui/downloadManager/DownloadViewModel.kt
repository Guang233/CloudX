package com.guang.cloudx.ui.downloadManager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.database.DownloadInfo
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import com.guang.cloudx.logic.service.DownloadService
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskStatus { DOWNLOADING, PAUSED, FAILED, COMPLETED }

data class DownloadItemUi(
    val id: Long = 0,
    val music: Music,
    val progress: Int,
    val status: TaskStatus,
    val timeStamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null,
    val downloadLevel: String = "standard",
    val rulesJson: String = "",
    val targetUri: String = "",
    val savedFileName: String? = null
)

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()
    private val gson = Gson()
    private val repository = MusicDownloadRepository()
    private var recoveryStarted = false

    private val _downloading = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val downloading: StateFlow<List<DownloadItemUi>> = _downloading

    private val _completed = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val completed: StateFlow<List<DownloadItemUi>> = _completed

    init {
        loadAllTasks()
    }

    private fun loadAllTasks() {
        viewModelScope.launch {
            val allTasks = downloadDao.getAllDownloads()
            if (!DownloadService.isRunning) {
                allTasks.filter { it.status == TaskStatus.DOWNLOADING }
                    .filter { it.rulesJson.isBlank() || it.targetUri.isBlank() }
                    .forEach {
                    it.status = TaskStatus.FAILED
                    it.failureReason = "任务缺少恢复参数，请重新下载"
                    downloadDao.update(it)
                }
                recoverInterruptedDownloads(downloadDao.getDownloadsByStatus(TaskStatus.DOWNLOADING))
            }

            _downloading.value =
                (downloadDao.getDownloadsByStatus(TaskStatus.DOWNLOADING).map { it.toDownloadItemUi() } +
                        downloadDao.getDownloadsByStatus(TaskStatus.PAUSED)
                            .map { it.toDownloadItemUi() } +
                        downloadDao.getDownloadsByStatus(TaskStatus.FAILED)
                            .map { it.toDownloadItemUi() }).distinctBy { it.id }
            _completed.value = downloadDao.getDownloadsByStatus(TaskStatus.COMPLETED)
                .map { it.toDownloadItemUi() }
        }
    }

    private suspend fun recoverInterruptedDownloads(tasks: List<DownloadInfo>) {
        if (recoveryStarted) return
        recoveryStarted = true

        val context = getApplication<Application>()
        val prefs = SharedPreferencesUtils(context)
        data class RecoveryTask(
            val task: DownloadInfo,
            val rules: MusicDownloadRules,
            val targetDir: DocumentFile
        )
        data class RecoveryKey(val level: String, val rulesJson: String, val targetUri: String)

        tasks.mapNotNull { task ->
            val rules = runCatching {
                gson.fromJson(task.rulesJson, MusicDownloadRules::class.java)
            }.getOrNull()
            if (rules == null) {
                downloadDao.update(
                    task.copy(
                        status = TaskStatus.FAILED,
                        failureReason = "任务下载参数无效，请重新下载"
                    )
                )
                return@mapNotNull null
            }

            val targetDir = runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(task.targetUri))
            }.getOrNull()?.takeIf { it.isDirectory && it.canWrite() }
            if (targetDir == null) {
                downloadDao.update(
                    task.copy(
                        status = TaskStatus.FAILED,
                        failureReason = "目标文件夹不可用，请重新选择"
                    )
                )
                return@mapNotNull null
            }

            RecoveryTask(task, rules, targetDir)
        }.groupBy { (task, _, _) ->
            RecoveryKey(task.downloadLevel, task.rulesJson, task.targetUri)
        }.values.forEach { group ->
            val first = group.first()
            startDownloadService(
                context = context,
                musics = group.map { it.task.music },
                level = first.task.downloadLevel.ifBlank { "standard" },
                cookie = prefs.getCookie(),
                targetDir = first.targetDir,
                rules = first.rules,
                musicIdToDbIdMap = group.associate { it.task.music.id to it.task.id }
            )
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
            val existingIds = downloadDao.getAllDownloads()
                .filter { it.status != TaskStatus.COMPLETED }
                .mapTo(mutableSetOf()) { it.music.id }
            val uniqueMusics = musics.distinctBy { it.id }
                .filterNot { it.id in existingIds }
            if (uniqueMusics.isEmpty()) return@launch

            val musicIdToDbIdMap = mutableMapOf<Long, Long>()
            val newTasks = mutableListOf<DownloadItemUi>()

            uniqueMusics.forEach { music ->
                val newInfo = DownloadInfo(
                    music = music,
                    progress = 0,
                    status = TaskStatus.DOWNLOADING,
                    timeStamp = System.currentTimeMillis(),
                    downloadLevel = level,
                    rulesJson = gson.toJson(rules),
                    targetUri = targetDir.uri.toString()
                )
                val id = downloadDao.insert(newInfo)
                musicIdToDbIdMap[music.id] = id
                newTasks.add(newInfo.copy(id = id).toDownloadItemUi())
            }

            _downloading.update { it + newTasks }

            startDownloadService(context, uniqueMusics, level, cookie, targetDir, rules, musicIdToDbIdMap)
        }
    }

    /** 暂停下载 */
    fun pauseDownload(context: Context, item: DownloadItemUi) {
        viewModelScope.launch {
            val paused = item.copy(status = TaskStatus.PAUSED, failureReason = null)
            downloadDao.update(paused.toDownloadInfo())
            _downloading.update { list ->
                list.map { if (it.id == item.id) paused else it }
            }

            if (DownloadService.isRunning) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_PAUSE
                    putExtra(DownloadService.EXTRA_DB_ID, item.id)
                }
                context.startService(intent)
            }
        }
    }

    /** 恢复暂停任务 */
    fun resumeDownload(
        context: Context,
        item: DownloadItemUi,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules
    ) {
        viewModelScope.launch {
            val savedRules = item.rulesJson.takeIf { it.isNotBlank() }?.let {
                runCatching { gson.fromJson(it, MusicDownloadRules::class.java) }.getOrNull()
            } ?: rules
            val savedTargetDir = item.targetUri.takeIf { it.isNotBlank() }
                ?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
                ?.takeIf { it.canWrite() }
                ?: targetDir
            val savedLevel = item.downloadLevel.ifBlank { level }
            val resumed = item.copy(status = TaskStatus.DOWNLOADING, failureReason = null)
            downloadDao.update(resumed.toDownloadInfo())
            _downloading.update { list ->
                list.map { if (it.id == item.id) resumed else it }
            }

            startDownloadService(
                context = context,
                musics = listOf(item.music),
                level = savedLevel,
                cookie = cookie,
                targetDir = savedTargetDir,
                rules = savedRules,
                musicIdToDbIdMap = mapOf(item.music.id to item.id)
            )
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
            repository.deleteDownloadArtifacts(getApplication(), item.music.id)
            _downloading.update { it.filterNot { t -> t.id == item.id } }
        }
    }

    /** 删除所有失败或暂停任务 */
    fun deleteAllFailed() {
        viewModelScope.launch {
            val deletableTasks =
                _downloading.value.filter { it.status == TaskStatus.FAILED || it.status == TaskStatus.PAUSED }
            deletableTasks.forEach { downloadDao.delete(it.toDownloadInfo()) }
            deletableTasks.forEach { repository.deleteDownloadArtifacts(getApplication(), it.music.id) }
            _downloading.update { list ->
                list.filterNot { it.status == TaskStatus.FAILED || it.status == TaskStatus.PAUSED }
            }
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
            downloadDao.deleteAllByStatus(TaskStatus.COMPLETED)
            _completed.value = emptyList()
            deletedSavedData()
        }
    }

    /** 下载完成 → 移动到 completed */
    private fun moveToCompleted(dbId: Long, savedFileName: String?) {
        viewModelScope.launch {
            val task = _downloading.value.find { it.id == dbId }
            if (task != null) {
                val finished = task.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 100,
                    savedFileName = savedFileName ?: task.savedFileName
                )
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
                val failedTask = task.copy(status = TaskStatus.FAILED, failureReason = reason)
                downloadDao.update(failedTask.toDownloadInfo())
                _downloading.update { list ->
                    list.map {
                        if (it.id == dbId) failedTask else it
                    }
                }
            }
        }
    }

    /** 标记暂停 */
    private fun markAsPaused(dbId: Long) {
        viewModelScope.launch {
            val task = _downloading.value.find { it.id == dbId }
            if (task != null && task.status != TaskStatus.DOWNLOADING) {
                val pausedTask = task.copy(status = TaskStatus.PAUSED, failureReason = null)
                downloadDao.update(pausedTask.toDownloadInfo())
                _downloading.update { list ->
                    list.map {
                        if (it.id == dbId) pausedTask else it
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
        val savedFileName = intent?.getStringExtra("fileName")
        when (intent?.action) {
            "DOWNLOAD_PROGRESS" -> {
                var nextStatus: TaskStatus? = null
                _downloading.update { list ->
                    list.map {
                        if (it.id == dbId) it.copy(
                            progress = progress,
                            status = when {
                                it.status == TaskStatus.PAUSED -> TaskStatus.PAUSED
                                progress == 100 -> TaskStatus.COMPLETED
                                else -> TaskStatus.DOWNLOADING
                            }.also { status -> nextStatus = status }
                        ) else it
                    }
                }
                nextStatus?.let { status ->
                    viewModelScope.launch {
                        downloadDao.updateProgress(dbId, progress, status)
                    }
                }
            }

            "DOWNLOAD_COMPLETED" -> {
                moveToCompleted(dbId, savedFileName)
            }

            "DOWNLOAD_FINISHED" -> {
                onFinished()
            }

            "DOWNLOAD_FAILED" -> {
                markAsFailed(dbId, failedReason)
            }

            "DOWNLOAD_PAUSED" -> {
                markAsPaused(dbId)
            }
        }
    }

    private fun startDownloadService(
        context: Context,
        musics: List<Music>,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules,
        musicIdToDbIdMap: Map<Long, Long>
    ) {
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

    private fun DownloadInfo.toDownloadItemUi(): DownloadItemUi {
        return DownloadItemUi(
            id = this.id,
            music = this.music,
            progress = this.progress,
            status = this.status,
            timeStamp = this.timeStamp,
            failureReason = this.failureReason,
            downloadLevel = this.downloadLevel,
            rulesJson = this.rulesJson,
            targetUri = this.targetUri,
            savedFileName = this.savedFileName
        )
    }

    private fun DownloadItemUi.toDownloadInfo(): DownloadInfo {
        return DownloadInfo(
            id = this.id,
            music = this.music,
            progress = this.progress,
            status = this.status,
            timeStamp = this.timeStamp,
            failureReason = this.failureReason,
            downloadLevel = this.downloadLevel,
            rulesJson = this.rulesJson,
            targetUri = this.targetUri,
            savedFileName = this.savedFileName
        )
    }
}
