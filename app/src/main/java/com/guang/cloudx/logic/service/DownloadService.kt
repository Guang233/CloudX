package com.guang.cloudx.logic.service

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guang.cloudx.R
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.model.DownloadStage
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import kotlinx.coroutines.*
import java.util.*

class DownloadService : Service() {
    private data class DownloadTask(
        val music: Music,
        val dbId: Long,
        val rules: MusicDownloadRules,
        val level: String,
        val cookie: String,
        val targetDir: DocumentFile
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = MusicDownloadRepository()
    private lateinit var notificationManager: NotificationManager
    private val downloadDao by lazy { AppDatabase.getDatabase(this).downloadDao() }


    private val queueLock = Any()
    private val downloadQueue = LinkedList<DownloadTask>()
    private var isDownloading = false
    private var totalCompleted = 0
    private var currentTask: DownloadTask? = null
    private var activeDownloadJob: Job? = null
    private val progressMap = mutableMapOf<Long, Int>()
    private val progressUpdateAtMap = mutableMapOf<Long, Long>()
    private val stageMap = mutableMapOf<Long, DownloadStage>()
    private val pauseRequestedIds = mutableSetOf<Long>()

    companion object {
        const val ACTION_PAUSE = "com.guang.cloudx.action.PAUSE_DOWNLOAD"
        const val EXTRA_DB_ID = "dbId"
        const val BROADCAST_PROGRESS = "DOWNLOAD_PROGRESS"
        const val BROADCAST_COMPLETED = "DOWNLOAD_COMPLETED"
        const val BROADCAST_FAILED = "DOWNLOAD_FAILED"
        const val BROADCAST_FINISHED = "DOWNLOAD_FINISHED"
        const val BROADCAST_PAUSED = "DOWNLOAD_PAUSED"

        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(1, buildNotification("正在准备下载…", "", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            val dbId = intent.getLongExtra(EXTRA_DB_ID, 0L)
            if (dbId != 0L) pauseDownload(dbId)
            return START_STICKY
        }

        val musicsJson = intent?.getStringExtra("musicsJson") ?: return START_NOT_STICKY
        val rulesJson = intent.getStringExtra("rulesJson") ?: return START_NOT_STICKY
        val musicIdToDbIdMapJson = intent.getStringExtra("musicIdToDbIdMapJson") ?: return START_NOT_STICKY

        val musics = Gson().fromJson<List<Music>>(musicsJson, object : TypeToken<List<Music>>() {}.type)
        val rules = Gson().fromJson(rulesJson, MusicDownloadRules::class.java)
        val musicIdToDbIdMap =
            Gson().fromJson<Map<Long, Long>>(musicIdToDbIdMapJson, object : TypeToken<Map<Long, Long>>() {}.type)
        val cookie = intent.getStringExtra("cookie") ?: ""
        val level = intent.getStringExtra("level") ?: "standard"
        val uri = intent.getParcelableExtra<Uri>("targetUri") ?: return START_NOT_STICKY
        val targetDir = DocumentFile.fromTreeUri(this, uri) ?: return START_NOT_STICKY

        updateNotification("正在准备下载…", "", 0)

        synchronized(queueLock) {
            musics.forEach { music ->
                val dbId = musicIdToDbIdMap[music.id]
                if (dbId != null) {
                    if (downloadQueue.none { it.dbId == dbId }) {
                        val task = DownloadTask(
                            music = music,
                            dbId = dbId,
                            rules = rules,
                            level = level,
                            cookie = cookie,
                            targetDir = targetDir
                        )
                        downloadQueue.add(task)
                        progressMap.putIfAbsent(dbId, 0)
                    }
                }
            }
        }


        if (!isDownloading) {
            isDownloading = true
            scope.launch {

                while (isActive) {
                    val task = synchronized(queueLock) { downloadQueue.poll() }

                    // 如果队列为空则检查是否应退出
                    if (task == null) {
                        if (synchronized(queueLock) { downloadQueue.isEmpty() }) {
                            sendBroadcast(
                                Intent(BROADCAST_FINISHED)
                                    .setPackage(packageName)
                                    .putExtra("totalCompleted", totalCompleted)
                            )
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            isDownloading = false
                            break
                        } else {
                            delay(200)
                            continue
                        }
                    }

                    synchronized(queueLock) {
                        currentTask = task
                    }

                    var downloadJob: Job? = null
                    try {
                        supervisorScope {
                            val job = async {
                                repository.downloadMusic(
                                    this@DownloadService,
                                    task.rules,
                                    task.music,
                                    task.level,
                                    task.cookie,
                                    task.targetDir
                                ) { _, progress, stage ->
                                    val previousProgress = progressMap[task.dbId] ?: -1
                                    val previousStage = stageMap[task.dbId]
                                    val now = SystemClock.elapsedRealtime()
                                    val lastUpdateAt = progressUpdateAtMap[task.dbId] ?: 0L
                                    val shouldPublishProgress = progress == 100 ||
                                            progress != previousProgress && now - lastUpdateAt >= 400 ||
                                            stage != previousStage

                                    progressMap[task.dbId] = progress
                                    stageMap[task.dbId] = stage

                                    if (shouldPublishProgress) {
                                        progressUpdateAtMap[task.dbId] = now
                                        val avgProgress =
                                            if (progressMap.isNotEmpty()) progressMap.values.sum() / progressMap.size else 0
                                        updateNotification(
                                            "音乐下载中... ($totalCompleted/${totalCompleted + downloadQueue.size + 1})",
                                            "${stage.toDisplayText()} ${task.music.name} ($progress%)",
                                            avgProgress
                                        )

                                        sendBroadcast(
                                            Intent(BROADCAST_PROGRESS)
                                                .setPackage(packageName)
                                                .apply {
                                                    putExtra(EXTRA_DB_ID, task.dbId)
                                                    putExtra("progress", progress)
                                                }
                                        )
                                    }
                                }
                            }
                            downloadJob = job
                            var shouldCancelImmediately = false
                            synchronized(queueLock) {
                                activeDownloadJob = job
                                shouldCancelImmediately = pauseRequestedIds.contains(task.dbId)
                            }
                            if (shouldCancelImmediately) {
                                job.cancel(CancellationException("下载已暂停"))
                            }
                            job.await()
                        }

                        totalCompleted++
                        progressUpdateAtMap.remove(task.dbId)
                        stageMap.remove(task.dbId)

                        sendBroadcast(
                            Intent(BROADCAST_COMPLETED)
                                .setPackage(packageName)
                                .apply {
                                    putExtra(EXTRA_DB_ID, task.dbId)
                                }
                        )

                    } catch (e: CancellationException) {
                        val wasPaused = synchronized(queueLock) {
                            pauseRequestedIds.remove(task.dbId)
                        }
                        if (wasPaused) {
                            progressUpdateAtMap.remove(task.dbId)
                            stageMap.remove(task.dbId)
                            progressMap.remove(task.dbId)
                            sendPausedBroadcast(task.dbId)
                        } else {
                            throw e
                        }
                    } catch (e: Exception) {
                        progressUpdateAtMap.remove(task.dbId)
                        stageMap.remove(task.dbId)
                        sendBroadcast(
                            Intent(BROADCAST_FAILED)
                                .setPackage(packageName)
                                .apply {
                                    putExtra(EXTRA_DB_ID, task.dbId)
                                    putExtra("reason", e.localizedMessage)
                                }
                        )
                    } finally {
                        synchronized(queueLock) {
                            if (currentTask?.dbId == task.dbId) currentTask = null
                            if (activeDownloadJob == downloadJob) activeDownloadJob = null
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun pauseDownload(dbId: Long) {
        var removedQueuedTask = false
        var jobToCancel: Job? = null

        synchronized(queueLock) {
            val iterator = downloadQueue.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().dbId == dbId) {
                    iterator.remove()
                    removedQueuedTask = true
                    break
                }
            }

            if (removedQueuedTask) {
                progressMap.remove(dbId)
                progressUpdateAtMap.remove(dbId)
                stageMap.remove(dbId)
            } else if (currentTask?.dbId == dbId) {
                pauseRequestedIds.add(dbId)
                jobToCancel = activeDownloadJob
            }
        }

        if (removedQueuedTask) {
            sendPausedBroadcast(dbId)
        } else {
            jobToCancel?.cancel(CancellationException("下载已暂停"))
        }
    }

    private fun sendPausedBroadcast(dbId: Long) {
        sendBroadcast(
            Intent(BROADCAST_PAUSED)
                .setPackage(packageName)
                .putExtra(EXTRA_DB_ID, dbId)
        )
    }


    private fun createNotificationChannel() {
        val channelId = "download_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String, progress: Int): Notification {
        val intent = Intent(Intent.ACTION_VIEW, "app://cloudx/download_manager".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "download_channel")
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.download_24px)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()
    }


    private fun updateNotification(title: String, content: String, progress: Int) {
        notificationManager.notify(1, buildNotification(title, content, progress))
    }

    private fun DownloadStage.toDisplayText(): String = when (this) {
        DownloadStage.DOWNLOADING -> "正在下载"
        DownloadStage.PROCESSING -> "正在处理"
        DownloadStage.TRANSCODING -> "正在转码"
        DownloadStage.WRITING_TAGS -> "正在写入信息"
        DownloadStage.SAVING -> "正在保存"
        DownloadStage.COMPLETED -> "下载完成"
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
