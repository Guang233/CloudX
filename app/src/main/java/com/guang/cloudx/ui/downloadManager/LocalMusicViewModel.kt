package com.guang.cloudx.ui.downloadManager

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guang.cloudx.logic.repository.LocalMusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalMusicViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = LocalMusicRepository(application)
    val files = repository.files.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob =
            viewModelScope.launch {
                try {
                    repository.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep the last known index when the database/provider is temporarily unavailable.
                }
            }
    }

    suspend fun deleteFile(
        item: DownloadItemUi,
        includeLyrics: Boolean,
    ): String {
        val uri = item.savedFileUri ?: return "未确认文件位置，无法删除"
        return try {
            repository.deleteFile(uri, item.music.id, includeLyrics)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "删除失败：${e.localizedMessage ?: "请检查目录权限"}"
        }
    }
}

/** Refresh on screen entry/return from the file manager, never once per song/card. */
@Composable
fun rememberLocalMusicViewModel(): LocalMusicViewModel {
    val model: LocalMusicViewModel = viewModel()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refresh()
            }
        owner.lifecycle.addObserver(observer)
        model.refresh()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return model
}
