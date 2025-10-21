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
import kotlinx.coroutines.*

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = MusicDownloadRepository(maxParallel = 3)
    private lateinit var notificationManager: NotificationManager

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

        // 启动通知
        updateNotification("正在准备下载 ${musics.size} 首歌曲…", "", 0)

        scope.launch {
            val total = musics.size
            val progressList = MutableList(total) { 0 }
            var completedCount = 0

            val jobs = musics.mapIndexed { id, it ->
                async {
                    try {
                        repository.downloadMusic(
                            this@DownloadService,
                            rules,
                            it,
                            level,
                            cookie,
                            targetDir
                        ) { music, progress ->
                            progressList[id] = progress

                            val totalProgress = progressList.sum() / total

                            updateNotification(
                                "音乐下载（$completedCount/${musics.size}）",
                                "正在下载 ${it.name}（$progress%）",
                                totalProgress
                            )

                            sendBroadcast(
                                Intent("DOWNLOAD_PROGRESS")
                                    .setPackage(packageName)
                                    .apply {
                                        putExtra("musicJson", Gson().toJson(music))
                                        putExtra("timeStamp", timeStampList[id])
                                        putExtra("progress", progress)
                                    }
                            )
                        }

                        completedCount++
                        progressList[id] = 100
                        val totalProgress = progressList.sum() / total
                        updateNotification(
                            "音乐下载（$completedCount/${musics.size}）",
                            "已完成 ${it.name}",
                            totalProgress
                        )

                    } catch (e: Exception) {
                        sendBroadcast(
                            Intent("DOWNLOAD_FAILED")
                                .setPackage(packageName)
                                .apply {
                                    putExtra("reason", e.localizedMessage)
                                    putExtra("timeStamp", timeStampList[id])
                                    putExtra("musicJson", Gson().toJson(it))
                                }
                        )
                    }
                }
            }

            jobs.awaitAll()
            updateNotification("下载完成", "", 100)
            sendBroadcast(Intent("DOWNLOAD_FINISHED").setPackage(packageName))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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