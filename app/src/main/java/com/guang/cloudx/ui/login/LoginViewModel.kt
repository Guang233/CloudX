package com.guang.cloudx.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.logic.model.UserData
import com.guang.cloudx.logic.repository.Repository
import kotlinx.coroutines.launch


class LoginViewModel : ViewModel() {
    var captchaState by mutableStateOf<UiState<Boolean>>(UiState.Idle)
        private set

    var loginState by mutableStateOf<UiState<UserData>>(UiState.Idle)
        private set

    fun sendCaptcha(phone: String) {
        viewModelScope.launch {
            captchaState = UiState.Loading
            runCatching {
                Repository.sendCaptcha(phone)
            }.onSuccess {
                captchaState = UiState.Success(it)
            }.onFailure { e ->
                captchaState = UiState.Error("验证码发送失败：${e.message}")
            }
        }
    }

    fun login(phone: String, captcha: String) {
        viewModelScope.launch {
            loginState = UiState.Loading
            runCatching {
                Repository.getLoginData(phone, captcha)
            }.onSuccess {
                loginState = UiState.Success(it)
            }.onFailure { e ->
                loginState = UiState.Error("登录失败：${e.message}")
            }
        }
    }

    sealed class UiState<out T> {
        object Idle : UiState<Nothing>()
        object Loading : UiState<Nothing>()
        data class Success<T>(val data: T) : UiState<T>()
        data class Error(val message: String) : UiState<Nothing>()
    }

}