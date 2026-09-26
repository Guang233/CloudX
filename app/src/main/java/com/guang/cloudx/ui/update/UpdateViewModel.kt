package com.guang.cloudx.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guang.cloudx.BuildConfig
import com.guang.cloudx.logic.repository.UpdateRepository
import com.guang.cloudx.logic.utils.SharedPreferencesUtils

/** Activity-scoped: rotation/navigation must not repeat a launch check or duplicate its dialog. */
class UpdateViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val prefs = SharedPreferencesUtils(application)
    private val repository = UpdateRepository()
    private val controller =
        UpdateController(
            scope = viewModelScope,
            check = { repository.checkForUpdate(BuildConfig.VERSION_NAME) },
            ignoredTags = prefs::getIgnoredUpdateTags,
            saveIgnoredTag = prefs::ignoreUpdateTag,
        )
    val state = controller.state

    fun checkOnLaunch() = controller.checkOnLaunch()

    fun checkManually() = controller.checkManually()

    fun ignoreThisVersion() = controller.ignoreThisVersion()

    fun dismiss() = controller.dismiss()
}
