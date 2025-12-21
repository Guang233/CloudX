package com.guang.cloudx.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.User
import com.guang.cloudx.logic.repository.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MainViewModel: ViewModel() {

    data class SearchInfo(val keyword: String, val offset: Int, val limit: Int, val cookie: String)
    data class UserInfo(val id: String, val cookie: String)

    // Compose 使用的 StateFlow
    private val _searchResultsFlow = MutableStateFlow<Result<List<Music>>?>(null)
    val searchResultsFlow: StateFlow<Result<List<Music>>?> = _searchResultsFlow.asStateFlow()

    private val _userDetailFlow = MutableStateFlow<Result<User>?>(null)
    val userDetailFlow: StateFlow<Result<User>?> = _userDetailFlow.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // UI 状态
    var inputText = ""
    var searchText = ""
    var isSearchMode = false
    var isMultiSelectionMode = false

    // 保留原有的 LiveData 版本供其他地方使用
    private val searchKeyword = MutableLiveData<SearchInfo>()
    private val userInfo = MutableLiveData<UserInfo>()
    val searchResults = searchKeyword.switchMap { info ->
        Repository.searchMusic(info.keyword, info.offset, info.limit, info.cookie)
    }
    val userDetail = userInfo.switchMap { info ->
        Repository.getUserDetail(info.id,  info.cookie)
    }

    fun searchMusic(keyword: String, offset: Int, limit: Int, cookie: String) {
        searchKeyword.value = SearchInfo(keyword, offset, limit, cookie)
    }

    fun getUserDetail(id: String, cookie: String) {
        userInfo.value = UserInfo(id, cookie)
    }

    // Compose 专用方法
    fun searchMusicFlow(keyword: String, offset: Int, limit: Int, cookie: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            Repository.searchMusicFlow(keyword, offset, limit, cookie).collect { result ->
                _searchResultsFlow.value = result
                _isRefreshing.value = false
            }
        }
    }

    fun getUserDetailFlow(id: String, cookie: String) {
        viewModelScope.launch {
            Repository.getUserDetailFlow(id, cookie).collect { result ->
                _userDetailFlow.value = result
            }
        }
    }

    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }

    fun clearSearchResults() {
        _searchResultsFlow.value = null
    }
}