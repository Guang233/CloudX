package com.guang.cloudx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.repository.UpdateRepository
import com.guang.cloudx.logic.repository.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val repo = UpdateRepository()

    private val _updateState = MutableStateFlow<UpdateResult>(UpdateResult.Loading)
    val updateState = _updateState

    fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateResult.Loading
            _updateState.value = repo.getLatestVersion()
        }
    }
}
