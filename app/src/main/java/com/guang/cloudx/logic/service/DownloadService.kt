package com.guang.cloudx.logic.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.guang.cloudx.R
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.database.DownloadInfo
import com.guang.cloudx.logic.model.*
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.ui.downloadManager.TaskStatus
import com.guang.cloudx.ui.downloadManager.transferSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Commands and scheduling have one owner. A stopped worker is joined before its files are removed. */
class DownloadService : Service() {
    private data class Command(
        val action: String,
        val ids: List<Long>,
        val generation: Long = 0,
    )

    private data class Task(
        val info: DownloadInfo,
        val rules: MusicDownloadRules,
        val target: DocumentFile,
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val queue = DownloadQueueState<Task>()
    private var worker: Job? = null
    private var lastStartId = 0
    private val repository = MusicDownloadRepository()
    private val dao by lazy { AppDatabase.getDatabase(this).downloadDao() }
    private lateinit var notifications: NotificationManager

    companion object {
        const val ACTION_PAUSE = "com.guang.cloudx.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.guang.cloudx.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.guang.cloudx.action.CANCEL_DOWNLOAD"
        const val ACTION_RECOVER = "com.guang.cloudx.action.RECOVER_DOWNLOAD"
        private const val ACTION_FINISHED = "worker_finished"
        const val EXTRA_DB_ID = "dbId"
        const val EXTRA_DB_IDS = "dbIds"
        const val BROADCAST_PROGRESS = "DOWNLOAD_PROGRESS"
        const val BROADCAST_COMPLETED = "DOWNLOAD_COMPLETED"
        const val BROADCAST_FAILED = "DOWNLOAD_FAILED"
        const val BROADCAST_FINISHED = "DOWNLOAD_FINISHED"
        const val BROADCAST_PAUSED = "DOWNLOAD_PAUSED"

        @Volatile var isRunning = false
            private set
        private val measurements = MutableStateFlow<Map<Long, DownloadTelemetry>>(emptyMap())
        val telemetry = measurements.asStateFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel("download_channel", "音乐下载", NotificationManager.IMPORTANCE_LOW))
        }
        startForeground(1, notification("正在准备下载…", "", null))
        scope.launch {
            for (command in commands) {
                when (command.action) {
                    ACTION_FINISHED -> {
                        if (queue.finish(command.generation)) worker = null
                    }

                    ACTION_PAUSE, ACTION_CANCEL -> {
                        stopTasks(command.ids, command.action == ACTION_CANCEL)
                    }

                    ACTION_RECOVER -> {
                        recover()
                    }

                    ACTION_RESUME -> {
                        enqueue(command.ids)
                    }
                }
                if (commands.isEmpty) startNext()
                // A start received while a DAO call suspends is already in the channel.
                if (queue.isIdle && commands.isEmpty) {
                    sendBroadcast(Intent(BROADCAST_FINISHED).setPackage(packageName))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(lastStartId)
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastStartId = startId
        startForeground(1, notification("正在管理下载任务…", "", null))
        val ids =
            intent?.getLongArrayExtra(EXTRA_DB_IDS)?.toList()
                ?: listOfNotNull(intent?.getLongExtra(EXTRA_DB_ID, 0)?.takeIf { it > 0 })
        commands.trySend(Command(intent?.action ?: ACTION_RECOVER, ids.distinct()))
        return START_NOT_STICKY
    }

    private suspend fun recover() {
        val resumable = mutableListOf<Long>()
        for (task in dao.getAllDownloads()) {
            when (task.status) {
                TaskStatus.CANCELLING -> stopTasks(listOf(task.id), true)
                TaskStatus.PAUSING -> stopTasks(listOf(task.id), false)
                TaskStatus.QUEUED, TaskStatus.DOWNLOADING -> resumable += task.id
                else -> Unit // A manually paused task must never auto-resume.
            }
        }
        enqueue(resumable)
    }

    private suspend fun enqueue(ids: List<Long>) {
        val targets = mutableMapOf<String, DocumentFile>()
        for (id in ids) {
            if (queue.contains(id)) continue
            val info = dao.findById(id) ?: continue
            if (info.status == TaskStatus.COMPLETED || info.status == TaskStatus.CANCELLING) continue
            try {
                val task =
                    withContext(Dispatchers.IO) {
                        val rules =
                            Gson().fromJson(info.rulesJson, MusicDownloadRules::class.java)
                                ?: error("任务缺少下载参数，请重新下载")
                        val target =
                            targets.getOrPut(info.targetUri) {
                                DocumentFile
                                    .fromTreeUri(this@DownloadService, info.targetUri.toUri())
                                    ?.takeIf { it.isDirectory && it.canWrite() }
                                    ?: error("目标文件夹不可用，请重新授权后继续")
                            }
                        Task(info, rules, target)
                    }
                dao.setUnfinishedStatus(id, TaskStatus.QUEUED)
                measurements.update { it - id }
                queue.enqueue(id, task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.setUnfinishedStatus(id, TaskStatus.FAILED, e.localizedMessage ?: "无法恢复下载")
            }
        }
    }

    private suspend fun stopTasks(
        ids: List<Long>,
        cancel: Boolean,
    ) {
        // Remove the entire batch before the next task is allowed to start.
        queue.removeQueued(ids)
        // Persist the whole batch before waiting for I/O, so process death cannot restart paused tasks.
        if (cancel) dao.markCancelling(ids) else dao.markPausing(ids)
        for (id in ids) {
            val info = dao.findById(id) ?: continue
            if (info.status == TaskStatus.COMPLETED) continue
            if (!cancel && info.status !in setOf(TaskStatus.QUEUED, TaskStatus.DOWNLOADING, TaskStatus.PAUSING)) continue
            dao.setUnfinishedStatus(id, if (cancel) TaskStatus.CANCELLING else TaskStatus.PAUSING)
            queue.active?.takeIf { it.id == id }?.let { active ->
                worker?.cancelAndJoin()
                queue.finish(active.generation)
                worker = null
            }
            // A short, non-cancellable SAF commit may have finished while cancellation was requested.
            if (dao.findById(id)?.status == TaskStatus.COMPLETED) continue
            if (cancel) {
                try {
                    withContext(Dispatchers.IO) { repository.deleteDownloadArtifacts(this@DownloadService, info.music.id) }
                    dao.deleteUnfinished(id)
                    measurements.update { it - id }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dao.setUnfinishedStatus(id, TaskStatus.FAILED, "临时文件清理失败：${e.localizedMessage}")
                }
            } else {
                dao.setUnfinishedStatus(id, TaskStatus.PAUSED)
                measurements.update { map -> map[id]?.let { map + (id to it.copy(bytesPerSecond = null, etaSeconds = null)) } ?: map }
                sendBroadcast(Intent(BROADCAST_PAUSED).setPackage(packageName).putExtra(EXTRA_DB_ID, id))
            }
        }
    }

    private suspend fun startNext() {
        val attempt = queue.startNext() ?: return
        val task = attempt.task
        val id = attempt.id
        dao.setUnfinishedStatus(id, TaskStatus.DOWNLOADING)
        measurements.update { it + (id to DownloadTelemetry()) }
        worker =
            scope.launch(Dispatchers.IO) {
                val meter = DownloadTransferMeter()
                var committed = false
                var saved: MusicDownloadRepository.SavedAudio? = null
                try {
                    coroutineScope {
                        val reporter =
                            launch {
                                while (isActive) {
                                    val value = meter.sample()
                                    measurements.update { it + (id to value) }
                                    dao.updateProgress(id, value.stageProgress ?: 0, TaskStatus.DOWNLOADING)
                                    notifications.notify(
                                        1,
                                        notification(
                                            task.info.music.name,
                                            value.stage.displayName() + "\n" + transferSummary(value, true),
                                            value.stageProgress,
                                        ),
                                    )
                                    delay(500)
                                }
                            }
                        try {
                            repository.downloadMusic(
                                this@DownloadService,
                                task.rules,
                                task.info.music,
                                task.info.downloadLevel,
                                SharedPreferencesUtils(this@DownloadService).getCookie(),
                                task.target,
                                onBytes = meter::bytes,
                                onSaved = { result ->
                                    saved = result
                                    committed = true
                                },
                                onProgress = { _, progress, stage ->
                                    meter.stage(stage, progress)
                                    if (stage != DownloadStage.DOWNLOADING) measurements.update { it + (id to meter.sample()) }
                                },
                            )
                        } finally {
                            reporter.cancelAndJoin()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!committed) {
                        dao.setUnfinishedStatus(id, TaskStatus.FAILED, e.localizedMessage ?: "下载失败")
                        sendBroadcast(Intent(BROADCAST_FAILED).setPackage(packageName).putExtra(EXTRA_DB_ID, id))
                    }
                } finally {
                    withContext(NonCancellable) {
                        if (committed) {
                            dao.complete(id, saved?.fileName, saved?.uri)
                            measurements.update { it - id }
                            sendBroadcast(Intent(BROADCAST_COMPLETED).setPackage(packageName).putExtra(EXTRA_DB_ID, id))
                        }
                    }
                    commands.trySend(Command(ACTION_FINISHED, listOf(id), attempt.generation))
                }
            }
    }

    private fun notification(
        title: String,
        content: String,
        progress: Int?,
    ): Notification {
        val intent =
            Intent(Intent.ACTION_VIEW, "app://cloudx/download_manager".toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat
            .Builder(this, "download_channel")
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pending)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.download_24px)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress ?: 0, progress == null)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
