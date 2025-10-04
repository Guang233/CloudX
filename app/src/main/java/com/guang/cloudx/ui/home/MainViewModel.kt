package com.guang.cloudx.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.guang.cloudx.logic.Repository


class MainViewModel: ViewModel() {

    data class SearchInfo(val keyword: String, val offset: Int, val limit: Int, val cookie: String)
    data class UserInfo(val id: String, val cookie: String)

    var inputText = ""
    var searchText = ""
    var isSearchMode = false
    var isMultiSelectionMode = false
    private val searchKeyword = MutableLiveData<SearchInfo>()
    private val userInfo = MutableLiveData<UserInfo>()
    val searchResults = searchKeyword.switchMap { info ->
        Repository.searchMusic(info.keyword, info.offset, info.limit, info.cookie)
    }
    val userDetail = userInfo.switchMap { info ->
        Repository.getUserDetail(info.id,  info.cookie)
    }
    fun searchMusic(keyword: String,  offset: Int, limit: Int, cookie: String) {
        searchKeyword.value = SearchInfo(keyword, offset, limit, cookie)
    }

    fun getUserDetail(id: String, cookie: String) {
        userInfo.value = UserInfo(id, cookie)
    }
}