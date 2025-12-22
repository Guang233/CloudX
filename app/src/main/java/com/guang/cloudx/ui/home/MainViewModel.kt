package com.guang.cloudx.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.User
import com.guang.cloudx.logic.repository.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MainViewModel : ViewModel() {

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