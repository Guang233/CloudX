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
import com.guang.cloudx.logic.model.DownloadTelemetry
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.repository.LocalMusicRepository
import com.guang.cloudx.logic.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TaskStatus { QUEUED, DOWNLOADING, PAUSING, PAUSED, CANCELLING, FAILED, COMPLETED }

fun TaskStatus.canPause(): Boolean = this == TaskStatus.QUEUED || this == TaskStatus.DOWNLOADING

fun TaskStatus.canContinue(): Boolean = this == TaskStatus.PAUSED

fun TaskStatus.canCancel(): Boolean = this != TaskStatus.COMPLETED && this != TaskStatus.CANCELLING

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
    val savedFileName: String? = null,
    val savedFileUri: String? = null,
    val telemetry: DownloadTelemetry? = null,
)

class DownloadViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()
    private val insertionMutex = Mutex()
    private val _downloading = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val downloading: StateFlow<List<DownloadItemUi>> = _downloading
    private val _completed = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val completed: StateFlow<List<DownloadItemUi>> = _completed

    init {
        viewModelScope.launch {
            if (!DownloadService.isRunning &&
                downloadDao.getAllDownloads().any {
                    it.status in setOf(TaskStatus.QUEUED, TaskStatus.DOWNLOADING, TaskStatus.PAUSING, TaskStatus.CANCELLING)
                }
            ) {
                sendCommand(application, DownloadService.ACTION_RECOVER, emptyList())
            }
            combine(downloadDao.observeAll(), DownloadService.telemetry) { tasks, telemetry ->
                tasks.map { it.toDownloadItemUi().copy(telemetry = telemetry[it.id]) }
            }.collect { tasks ->
                _downloading.value = tasks.filter { it.status != TaskStatus.COMPLETED }
                _completed.value = tasks.filter { it.status == TaskStatus.COMPLETED }
            }
        }
    }

    /** New requests keep explicit repeat-download behavior for completed songs. */
    @Suppress("UNUSED_PARAMETER")
    fun startDownloads(
        context: Context,
        musics: List<Music>,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules,
    ) {
        viewModelScope.launch {
            insertionMutex.withLock {
                val existing =
                    downloadDao.getAllDownloads().filter { it.status != TaskStatus.COMPLETED }.mapTo(
                        mutableSetOf(),
                    ) { it.music.id }
                val ids =
                    musics.distinctBy { it.id }.filterNot { it.id in existing }.map { music ->
                        downloadDao.insert(
                            DownloadInfo(
                                music = music,
                                progress = 0,
                                status = TaskStatus.QUEUED,
                                timeStamp = System.currentTimeMillis(),
                                downloadLevel = level,
                                rulesJson = Gson().toJson(rules),
                                targetUri = targetDir.uri.toString(),
                            ),
                        )
                    }
                if (ids.isNotEmpty()) sendCommand(context, DownloadService.ACTION_RESUME, ids)
            }
        }
    }

    fun pauseTasks(
        context: Context,
        ids: Collection<Long>,
    ) = sendCommand(context, DownloadService.ACTION_PAUSE, ids)

    fun resumeTasks(
        context: Context,
        ids: Collection<Long>,
    ) = sendCommand(context, DownloadService.ACTION_RESUME, ids)

    fun cancelTasks(
        context: Context,
        ids: Collection<Long>,
    ) = sendCommand(context, DownloadService.ACTION_CANCEL, ids)

    fun pauseDownload(
        context: Context,
        item: DownloadItemUi,
    ) = pauseTasks(context, listOf(item.id))

    fun resumeDownload(
        context: Context,
        item: DownloadItemUi,
    ) = resumeTasks(context, listOf(item.id))

    fun retryDownload(
        context: Context,
        item: DownloadItemUi,
    ) = resumeTasks(context, listOf(item.id))

    fun retryAllFailed(context: Context) = resumeTasks(context, _downloading.value.filter { it.status == TaskStatus.FAILED }.map { it.id })

    fun deleteFailed(item: DownloadItemUi) = cancelTasks(getApplication(), listOf(item.id))

    fun deleteAllFailed() =
        cancelTasks(
            getApplication(),
            _downloading.value
                .filter {
                    it.status == TaskStatus.FAILED ||
                        it.status == TaskStatus.PAUSED
                }.map { it.id },
        )

    fun deleteCompleted(
        item: DownloadItemUi,
        deletedSavedData: () -> Unit,
    ) {
        viewModelScope.launch {
            LocalMusicRepository(getApplication()).refresh()
            downloadDao.findById(item.id)?.takeIf { it.status == TaskStatus.COMPLETED }?.let { downloadDao.delete(it) }
            deletedSavedData()
        }
    }

    fun deleteAllCompleted(deletedSavedData: () -> Unit) {
        viewModelScope.launch {
            LocalMusicRepository(getApplication()).refresh()
            downloadDao.deleteAllByStatus(TaskStatus.COMPLETED)
            deletedSavedData()
        }
    }

    fun updateProgressById(
        intent: Intent?,
        onFinished: () -> Unit,
    ) {
        if (intent?.action == DownloadService.BROADCAST_FINISHED) onFinished()
    }

    private fun sendCommand(
        context: Context,
        action: String,
        ids: Collection<Long>,
    ) {
        if (ids.isEmpty() && action != DownloadService.ACTION_RECOVER) return
        val intent =
            Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(DownloadService.EXTRA_DB_IDS, ids.distinct().toLongArray())
            }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun DownloadInfo.toDownloadItemUi() =
        DownloadItemUi(
            id = id,
            music = music,
            progress = progress,
            status = status,
            timeStamp = timeStamp,
            failureReason = failureReason,
            downloadLevel = downloadLevel,
            rulesJson = rulesJson,
            targetUri = targetUri,
            savedFileName = savedFileName,
            savedFileUri = savedFileUri,
        )
}
