package com.guang.cloudx.ui.playList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.repository.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayListUiState(
    val playList: PlayList? = null,
    val musicList: List<Music> = emptyList(),
    val isRefreshing: Boolean = false,
    val isMultiSelectMode: Boolean = false,
    val selectedItems: Set<Music> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlayListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayListUiState())
    val uiState = _uiState.asStateFlow()

    private var currentId: String = ""
    private var currentCookie: String = ""
    private var currentType: String = ""

    fun getPlayList(id: String, cookie: String, type: String) {
        currentId = id
        currentCookie = cookie
        currentType = type

        _uiState.update { it.copy(isLoading = true, isRefreshing = true) }

        viewModelScope.launch {
            val liveData = if (type == "playlist") {
                Repository.getPlayList(id, cookie)
            } else {
                Repository.getAlbum(id, cookie)
            }

            // Convert LiveData to Flow or collect it
            liveData.asFlow().collect { result ->
                val playList = result.getOrNull()
                if (playList != null) {
                    _uiState.update {
                        it.copy(
                            playList = playList,
                            musicList = playList.musics,
                            isLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = "获取失败"
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        if (currentId.isNotEmpty()) {
            getPlayList(currentId, currentCookie, currentType)
        }
    }

    fun enterMultiSelectMode() {
        _uiState.update { it.copy(isMultiSelectMode = true) }
    }

    fun exitMultiSelectMode() {
        _uiState.update { it.copy(isMultiSelectMode = false, selectedItems = emptySet()) }
    }

    fun toggleSelection(music: Music) {
        _uiState.update { state ->
            val newSelection = if (state.selectedItems.contains(music)) {
                state.selectedItems - music
            } else {
                state.selectedItems + music
            }
            state.copy(selectedItems = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedItems = state.musicList.toSet())
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val newSelection = state.musicList.filter { !state.selectedItems.contains(it) }.toSet()
            state.copy(selectedItems = newSelection)
        }
    }
}