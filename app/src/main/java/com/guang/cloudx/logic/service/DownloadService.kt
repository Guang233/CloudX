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
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import com.guang.cloudx.ui.downloadManager.DownloadManagerActivity
import com.guang.cloudx.util.ext.d
import kotlinx.coroutines.*
import java.util.*

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = MusicDownloadRepository(maxParallel = 3)
    private lateinit var notificationManager: NotificationManager

    private val queueLock = Any()
    private val downloadQueue = LinkedList<Music>() // 队列
    private var isDownloading = false
    private var totalCompleted = 0
    private val musicTimeStampMap = mutableMapOf<Music, Long>()
    private val progressMap = mutableMapOf<Long, Int>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(1, buildNotification("正在准备下载…", "", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val musicsJson = intent?.getStringExtra("musicsJson") ?: return START_NOT_STICKY
        val rulesJson = intent.getStringExtra("rulesJson") ?: return START_NOT_STICKY
        val musics = Gson().fromJson<List<Music>>(musicsJson, object : TypeToken<List<Music>>() {}.type)
        val rules = Gson().fromJson(rulesJson, MusicDownloadRules::class.java)
        val timeStampList = Gson().fromJson<List<Long>>(
            intent.getStringExtra("timeStampList"),
            object : TypeToken<List<Long>>() {}.type
        )
        val cookie = intent.getStringExtra("cookie") ?: ""
        val level = intent.getStringExtra("level") ?: "standard"
        val uri = intent.getParcelableExtra<Uri>("targetUri") ?: return START_NOT_STICKY
        val targetDir = DocumentFile.fromTreeUri(this, uri) ?: return START_NOT_STICKY

        updateNotification("正在准备下载…", "", 0)

        synchronized(queueLock) {
            musics.forEachIndexed { index, music ->
                if (!downloadQueue.contains(music)) {
                    downloadQueue.add(music)
                    val ts = timeStampList.getOrNull(index) ?: System.currentTimeMillis()
                    musicTimeStampMap[music] = ts
                    progressMap.putIfAbsent(ts + music.id, 0)
                }
            }
        }


        if (!isDownloading) {
            isDownloading = true
            scope.launch {

                while (isActive) {
                    val item = synchronized(queueLock) { downloadQueue.poll() }

                    // 如果队列为空则检查是否应退出
                    if (item == null) {
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

                    val timestamp = musicTimeStampMap[item] ?: System.currentTimeMillis()
                    val id = timestamp + item.id

                    try {
                        repository.downloadMusic(
                            this@DownloadService,
                            rules,
                            item,
                            level,
                            cookie,
                            targetDir
                        ) { music, progress ->
                            progressMap[id] = progress
//                            progressMap.size.d()
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
                                        putExtra("musicJson", Gson().toJson(music))
                                        putExtra("timeStamp", timestamp)
                                        putExtra("progress", progress)
                                    }
                            )
                        }

                        totalCompleted++

                        sendBroadcast(
                            Intent("DOWNLOAD_COMPLETED")
                                .setPackage(packageName)
                                .apply {
                                    putExtra("timeStamp", timestamp)
                                    putExtra("musicJson", Gson().toJson(item))
                                }
                        )

                    } catch (e: Exception) {
                        sendBroadcast(
                            Intent("DOWNLOAD_FAILED")
                                .setPackage(packageName)
                                .apply {
                                    putExtra("reason", e.localizedMessage)
                                    putExtra("timeStamp", timestamp)
                                    putExtra("musicJson", Gson().toJson(item))
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
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}