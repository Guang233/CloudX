package com.guang.cloudx.ui.myPlayLists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.repository.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 我的歌单 UI 状态
 *
 * @param playLists 当前展示的歌单列表
 * @param isLoading 是否正在加载
 * @param isRefreshing 是否正在下拉刷新
 * @param error 错误信息，为空表示无错误
 */
data class MyPlayListsUiState(
    val playLists: List<PlayList> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

/**
 * 我的歌单 ViewModel
 *
 * 从 [Repository] 拉取当前登录用户的歌单列表，UI 层通过 [loadMyPlayLists] 传入 userId 与 cookie。
 */
class MyPlayListsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MyPlayListsUiState())
    val uiState: StateFlow<MyPlayListsUiState> = _uiState.asStateFlow()

    /**
     * 加载用户歌单列表。
     * 已对接 /api/user/playlist，传入当前登录用户的 userId 与 cookie。
     * 未登录时显示提示信息。
     */
    fun loadMyPlayLists(userId: String, cookie: String) {
        if (userId.isEmpty() || cookie.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "请先登录",
                    playLists = emptyList()
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, isRefreshing = true, error = null) }

        viewModelScope.launch {
            Repository.getUserPlayListsFlow(userId, cookie).collect { result ->
                result.fold(
                    onSuccess = { playLists ->
                        _uiState.update {
                            it.copy(
                                playLists = playLists,
                                isLoading = false,
                                isRefreshing = false,
                                error = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = throwable.message ?: "获取歌单失败"
                            )
                        }
                    }
                )
            }
        }
    }

    /**
     * 下拉刷新入口
     */
    fun refresh(userId: String, cookie: String) {
        loadMyPlayLists(userId, cookie)
    }
}