package com.guang.cloudx.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.guang.cloudx.logic.Repository
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.network.MusicNetwork.searchMusic
import kotlin.concurrent.thread

class MainViewModel: ViewModel() {

    data class SearchInfo(val keyword: String, val offset: Int, val limit: Int)
    var searchText = ""
    private val searchKeyword = MutableLiveData<SearchInfo>()
    val searchResults = searchKeyword.switchMap { info ->
        Repository.searchMusic(info.keyword, info.offset, info.limit)
    }

    fun searchMusic(keyword: String,  offset: Int, limit: Int) {
        searchKeyword.value = SearchInfo(keyword, offset, limit)
    }

}