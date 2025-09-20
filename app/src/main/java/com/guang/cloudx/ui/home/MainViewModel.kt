package com.guang.cloudx.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.guang.cloudx.logic.Repository


class MainViewModel: ViewModel() {

    data class SearchInfo(val keyword: String, val offset: Int, val limit: Int)

    var searchText = ""
    var isSearchMode = false
    private val searchKeyword = MutableLiveData<SearchInfo>()
    val searchResults = searchKeyword.switchMap { info ->
        Repository.searchMusic(info.keyword, info.offset, info.limit)
    }

    fun searchMusic(keyword: String,  offset: Int, limit: Int) {
        searchKeyword.value = SearchInfo(keyword, offset, limit)
    }

}