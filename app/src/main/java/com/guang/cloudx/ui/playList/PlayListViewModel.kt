package com.guang.cloudx.ui.playList

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.guang.cloudx.logic.Repository

class PlayListViewModel: ViewModel() {
    var isMultiSelectionMode = false

    data class PlayListId(val id: String, val cookie: String)
    private val playListId = MutableLiveData<PlayListId>()
    val playList = playListId.switchMap { info ->
        Repository.getPlayList(info.id,  info.cookie)
    }

    fun getPlayList(id: String, cookie: String) {
        playListId.value = PlayListId(id, cookie)
    }
}