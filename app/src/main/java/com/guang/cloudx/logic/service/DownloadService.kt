package com.guang.cloudx.logic.service

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guang.cloudx.R
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import com.guang.cloudx.ui.downloadManager.DownloadManagerActivity
import com.guang.cloudx.ui.downloadManager.TaskStatus
import kotlinx.coroutines.*
import java.util.*

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = MusicDownloadRepository()
    private lateinit var notificationManager: NotificationManager
    private val downloadDao by lazy { AppDatabase.getDatabase(this).downloadDao() }


    private val queueLock = Any()
    private val downloadQueue = LinkedList<Pair<Music, Long>>() // Pair of Music and its DB ID
    private var isDownloading = false
    private var totalCompleted = 0
    private val progressMap = mutableMapOf<Long, Int>()

    companion object {
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
        val musicsJson = intent?.getStringExtra("musicsJson") ?: return START_NOT_STICKY
        val rulesJson = intent.getStringExtra("rulesJson") ?: return START_NOT_STICKY
        val musicIdToDbIdMapJson = intent.getStringExtra("musicIdToDbIdMapJson") ?: return START_NOT_STICKY

        val musics = Gson().fromJson<List<Music>>(musicsJson, object : TypeToken<List<Music>>() {}.type)
        val rules = Gson().fromJson(rulesJson, MusicDownloadRules::class.java)
        val musicIdToDbIdMap = Gson().fromJson<Map<Long, Long>>(musicIdToDbIdMapJson, object : TypeToken<Map<Long, Long>>() {}.type)
        val cookie = intent.getStringExtra("cookie") ?: ""
        val level = intent.getStringExtra("level") ?: "standard"
        val uri = intent.getParcelableExtra<Uri>("targetUri") ?: return START_NOT_STICKY
        val targetDir = DocumentFile.fromTreeUri(this, uri) ?: return START_NOT_STICKY

        updateNotification("正在准备下载…", "", 0)

        synchronized(queueLock) {
            musics.forEach { music ->
                val dbId = musicIdToDbIdMap[music.id]
                if (dbId != null) {
                    val task = Pair(music, dbId)
                    if (!downloadQueue.contains(task)) {
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
                                Intent("DOWNLOAD_FINISHED")
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

                    val (music, dbId) = task

                    try {
                        repository.downloadMusic(
                            this@DownloadService,
                            rules,
                            music,
                            level,
                            cookie,
                            targetDir
                        ) { _, progress ->
                            progressMap[dbId] = progress
                            val avgProgress =
                                if (progressMap.isNotEmpty()) progressMap.values.sum() / progressMap.size else 0
                            updateNotification(
                                "音乐下载中... ($totalCompleted/${totalCompleted + downloadQueue.size + 1})",
                                "正在下载 ${music.name} ($progress%)",
                                avgProgress
                            )

                            sendBroadcast(
                                Intent("DOWNLOAD_PROGRESS")
                                    .setPackage(packageName)
                                    .apply {
                                        putExtra("dbId", dbId)
                                        putExtra("progress", progress)
                                    }
                            )
                        }

                        totalCompleted++
                        
                        sendBroadcast(
                            Intent("DOWNLOAD_COMPLETED")
                                .setPackage(packageName)
                                .apply {
                                    putExtra("dbId", dbId)
                                }
                        )

                    } catch (e: Exception) {
                        sendBroadcast(
                            Intent("DOWNLOAD_FAILED")
                                .setPackage(packageName)
                                .apply {
                                    putExtra("dbId", dbId)
                                    putExtra("reason", e.localizedMessage)
                                }
                        )
                    }
                }
            }
        }

        return START_STICKY
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
        val intent = Intent(this, DownloadManagerActivity::class.java).apply {
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

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
