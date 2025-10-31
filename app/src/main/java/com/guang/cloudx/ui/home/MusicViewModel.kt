package com.guang.cloudx.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel : ViewModel() {
    val musicFile = MutableLiveData<File?>()

    fun cacheMusic(music: Music, parent: File) {
        viewModelScope.launch {
            runCatching {
                MusicDownloadRepository(maxParallel = 1).cacheMusic(music, parent)
            }.onSuccess {
                musicFile.postValue(it)
            }.onFailure {
                musicFile.postValue(null)
            }
        }
    }
}