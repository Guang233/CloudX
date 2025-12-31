package com.guang.cloudx.logic.utils

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * 全局 Application 级别的 ViewModelStore
 */
object AppViewModelStoreOwner : ViewModelStoreOwner {
    private val appViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}

/**
 * 获取 Application 作用域的 ViewModel
 */
inline fun <reified VM : ViewModel> applicationViewModels(
    application: Application
): Lazy<VM> = lazy {
    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    ViewModelProvider(AppViewModelStoreOwner, factory)[VM::class.java]
}
