package com.guang.cloudx.ui.update

import com.guang.cloudx.logic.repository.AppRelease
import com.guang.cloudx.logic.repository.UpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Kept independent of Android for lifecycle, suppression and concurrency tests.
data class UpdateUiState(
    val checking: Boolean = false,
    val manual: Boolean = false,
    val release: AppRelease? = null,
    val message: String? = null,
)

class UpdateController(
    private val scope: CoroutineScope,
    private val check: suspend () -> UpdateResult,
    private val ignoredTags: () -> Set<String>,
    private val saveIgnoredTag: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state = mutableState.asStateFlow()
    private var checkedOnLaunch = false
    private var checkJob: Job? = null
    private var manualRequested = false

    fun checkOnLaunch() {
        if (checkedOnLaunch) return
        checkedOnLaunch = true
        startCheck(manual = false)
    }

    fun checkManually() = startCheck(manual = true)

    private fun startCheck(manual: Boolean) {
        // A settings click can adopt the startup request instead of racing a second request.
        if (checkJob?.isActive == true) {
            manualRequested = manualRequested || manual
            mutableState.value = mutableState.value.copy(manual = manualRequested)
            return
        }
        manualRequested = manual
        mutableState.value = UpdateUiState(checking = true, manual = manual)
        checkJob =
            scope.launch {
                val result =
                    try {
                        check()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        UpdateResult.Error("检查更新失败，请稍后重试")
                    }
                ensureActive()
                val interactive = manualRequested
                mutableState.value =
                    when (result) {
                        is UpdateResult.Available -> {
                            if (interactive || result.release.tag !in ignoredTags()) {
                                UpdateUiState(manual = interactive, release = result.release)
                            } else {
                                UpdateUiState()
                            }
                        }

                        UpdateResult.UpToDate -> {
                            UpdateUiState(message = if (interactive) "当前已是最新版本" else null)
                        }

                        UpdateResult.NoRelease -> {
                            UpdateUiState(message = if (interactive) "GitHub 上暂无可用正式版本，或仓库暂不可访问" else null)
                        }

                        is UpdateResult.Error -> {
                            UpdateUiState(message = if (interactive) result.message else null)
                        }
                    }
            }
    }

    fun ignoreThisVersion() {
        mutableState.value.release?.let { saveIgnoredTag(it.tag) }
        dismiss()
    }

    fun dismiss() {
        checkJob?.cancel()
        mutableState.value = UpdateUiState()
    }
}
