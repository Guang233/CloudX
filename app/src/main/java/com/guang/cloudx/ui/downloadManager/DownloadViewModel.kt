package com.guang.cloudx.ui.downloadManager

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.service.DownloadService
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class TaskStatus { DOWNLOADING, FAILED, COMPLETED }

data class DownloadItemUi(
    val music: Music,
    val progress: Int,
    val status: TaskStatus,
    val timeStamp: Long = System.currentTimeMillis()
)

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _downloading = MutableStateFlow<List<DownloadItemUi>>(emptyList())
    val downloading: StateFlow<List<DownloadItemUi>> = _downloading

    private val _completed: MutableStateFlow<List<DownloadItemUi>> by lazy {
        val typeOf = object : TypeToken<List<DownloadItemUi>>() {}.type
        val data = Gson().fromJson<List<DownloadItemUi>>(
            SharedPreferencesUtils(application).getCompletedMusic(),
            typeOf
        ) ?: emptyList()
        MutableStateFlow(data)
    }

    val completed: StateFlow<List<DownloadItemUi>> = _completed

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

        musics.forEach { music ->
            // 我这个结构还是有点问题的，暂且单独传一个列表
            val timeStamp = System.currentTimeMillis()
            val newTask = DownloadItemUi(
                music = music,
                progress = 0,
                status = TaskStatus.DOWNLOADING,
                timeStamp = timeStamp,
            )
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

    /** 失败 → 重试 */
    fun retryDownload(
        context: Context,
        item: DownloadItemUi,
        level: String,
        cookie: String,
        targetDir: DocumentFile,
        rules: MusicDownloadRules
    ) {
        _downloading.update { it.filterNot { t -> t.music == item.music } }
        startDownloads(context, listOf(item.music), level, cookie, targetDir, rules)
    }

    /** 删除失败任务 */
    fun deleteFailed(item: DownloadItemUi) {
        _downloading.update { it.filterNot { t -> t.timeStamp == item.timeStamp } }
    }

    /** 删除已完成任务 */
    fun deleteCompleted(item: DownloadItemUi, deletedSavedData: () -> Unit) {
        _completed.update { it.filterNot { t -> t.timeStamp == item.timeStamp && t.music == item.music } }
        deletedSavedData()
    }

    /** 删除全部已完成 */
    fun deleteAllCompleted(deletedSavedData: () -> Unit) {
        _completed.value = emptyList()
        deletedSavedData()
    }

    /** 下载完成 → 移动到 completed */
    private fun moveToCompleted(music: Music, savedCompletedMusic: (DownloadItemUi) -> Unit) {
        val finished = DownloadItemUi(
            music = music,
            progress = 100,
            status = TaskStatus.COMPLETED
        )
        _downloading.update { it -> it.filterNot { it.music == music } }
        _completed.update { it + finished }
        savedCompletedMusic(finished)
    }

    /** 标记失败 */
    private fun markAsFailed(music: Music) {
        _downloading.update { list ->
            list.map {
                if (it.music == music) it.copy(status = TaskStatus.FAILED, progress = 0) else it
            }
        }
    }

    fun updateProgressById(intent: Intent?, onFinished: () -> Unit) {
        val music = Gson().fromJson(intent?.getStringExtra("musicJson"), Music::class.java)
        val progress = intent?.getIntExtra("progress", 0) ?: 0
        val timeStamp = intent?.getLongExtra("timeStamp", 0) ?: 0
        val failedReason = intent?.getStringExtra("reason") ?: ""
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

                if (progress == 100) {
                    val finishedMusic =
                        _downloading.value.find { it.music == music && it.timeStamp == timeStamp }?.music ?: return
                    moveToCompleted(finishedMusic) { finished ->
                        val typeOf = object : TypeToken<List<DownloadItemUi>>() {}.type
                        val data = Gson().fromJson<List<DownloadItemUi>>(
                            SharedPreferencesUtils(getApplication()).getCompletedMusic(),
                            typeOf
                        ) ?: listOf()
                        SharedPreferencesUtils(getApplication()).putCompletedMusic(
                            Gson().toJson(data + finished)
                        )
                    }
                }
            }

            "DOWNLOAD_FINISHED" -> {
                onFinished()
            }

            "DOWNLOAD_FAILED" -> {
                markAsFailed(music)
            }
        }
    }

}
