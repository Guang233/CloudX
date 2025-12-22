package com.guang.cloudx

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.guang.cloudx.logic.utils.applicationViewModels
import com.guang.cloudx.ui.downloadManager.DownloadViewModel

class MyApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        val viewModel by applicationViewModels<DownloadViewModel>(this)

        val intent = Intent(Intent.ACTION_VIEW, "app://cloudx/download_manager".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)


        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.updateProgressById(intent) {
                    NotificationManagerCompat.from(applicationContext).notify(
                        1,
                        NotificationCompat.Builder(applicationContext, "download_channel")
                            .setContentTitle("全部任务已下载完成")
                            .setContentText("点按通知以打开下载管理")
                            .setContentIntent(pendingIntent)
                            .setSmallIcon(R.drawable.download_24px)
                            .setOnlyAlertOnce(true)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("DOWNLOAD_PROGRESS")
            addAction("DOWNLOAD_COMPLETED")
            addAction("DOWNLOAD_FAILED")
            addAction("DOWNLOAD_FINISHED")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }
}