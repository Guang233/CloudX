package com.guang.cloudx.ui.playList

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.guang.cloudx.logic.repository.Repository

class PlayListViewModel: ViewModel() {
    var isMultiSelectionMode = false

    data class PlayListId(val id: String, val cookie: String, val type: String)
    private val playListId = MutableLiveData<PlayListId>()
    val playList = playListId.switchMap { info ->
        if (info.type == "playlist") Repository.getPlayList(info.id,  info.cookie)
        else Repository.getAlbum(info.id, info.cookie)
    }

    fun getPlayList(id: String, cookie: String, type: String) {
        playListId.value = PlayListId(id, cookie, type)
    }
}