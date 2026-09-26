package com.guang.cloudx

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.guang.cloudx.logic.model.*
import com.guang.cloudx.ui.downloadManager.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadTaskUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val music = Music("test song", emptyList(), Album("album", 1, ""), 1)

    @Test fun selectionActionsOnlyAffectEligibleSelectedTasks() {
        var paused = emptyList<Long>()
        var resumed = emptyList<Long>()
        var cancelled = emptyList<Long>()
        val tasks =
            listOf(
                DownloadItemUi(1, music, 0, TaskStatus.QUEUED),
                DownloadItemUi(2, music, 10, TaskStatus.PAUSED),
                DownloadItemUi(3, music, 0, TaskStatus.DOWNLOADING),
            )
        compose.setContent {
            MaterialTheme {
                Row {
                    DownloadTaskActions(tasks, true, setOf(1, 2), {}, {}, {}, { paused = it }, { resumed = it }, { cancelled = it }, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("所选任务操作").performClick()
        compose.onNodeWithText("暂停所选").performClick()
        compose.onNodeWithContentDescription("所选任务操作").performClick()
        compose.onNodeWithText("继续所选").performClick()
        compose.onNodeWithContentDescription("所选任务操作").performClick()
        compose.onNodeWithText("取消所选任务").performClick()
        assertEquals(listOf(1L), paused)
        assertEquals(listOf(2L), resumed)
        assertEquals(listOf(1L, 2L), cancelled)
    }

    @Test fun emptyQueueDisablesAllPauseAndContinue() {
        compose.setContent {
            MaterialTheme {
                Row {
                    DownloadTaskActions(emptyList(), false, emptySet(), {}, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("全部暂停").assertIsNotEnabled()
        compose.onNodeWithContentDescription("全部继续").assertIsNotEnabled()
    }

    @Test fun selectedRowClickDoesNotPauseOrResume() {
        var toggles = 0
        var pauses = 0
        compose.setContent {
            MaterialTheme {
                DownloadingItem(
                    DownloadItemUi(1, music, 0, TaskStatus.QUEUED),
                    onRetry = {},
                    onPause = { pauses++ },
                    onResumeDownload = {},
                    onDelete = {},
                    onLongClick = {},
                    selectionMode = true,
                    isSelected = true,
                    onToggleSelection = { toggles++ },
                )
            }
        }
        compose.onNodeWithText("test song").assertIsSelected().performClick()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        assertEquals(1, toggles)
        assertEquals(0, pauses)
        compose.onNodeWithText("排队中").assertExists()
        compose.onNodeWithContentDescription("取消任务").assertDoesNotExist()
    }

    @Test fun selectionHeaderClosesSelectionWithoutNavigatingBack() {
        var selecting by mutableStateOf(true)
        var backClicks = 0
        compose.setContent {
            MaterialTheme {
                DownloadManagerTopBar(selecting, 2, onBack = { backClicks++ }, onCloseSelection = { selecting = false }, actions = {})
            }
        }
        compose.onNodeWithText("已选 2 项").assertExists()
        compose.onNodeWithContentDescription("关闭").performClick()
        assertEquals(0, backClicks)
        compose.onNodeWithText("下载管理").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backClicks)
    }

    @Test fun selectAllIsIdempotentAndInvertCanClearSelection() {
        val tasks = listOf(DownloadItemUi(1, music, 0, TaskStatus.QUEUED), DownloadItemUi(2, music, 0, TaskStatus.PAUSED))
        var selected by mutableStateOf(setOf(1L))
        compose.setContent {
            MaterialTheme {
                DownloadManagerTopBar(true, selected.size, {}, {}, actions = { selecting ->
                    DownloadTaskActions(tasks, selecting, selected, {},
                        onSelectAll = { selected = tasks.map { it.id }.toSet() },
                        onInvertSelection = { selected = tasks.map { it.id }.toSet() - selected },
                        onPause = {}, onContinue = {}, onCancel = {}, onRetryFailed = {}, onClearFailed = {})
                })
            }
        }
        compose.onNodeWithContentDescription("反选").performClick()
        assertEquals(setOf(2L), selected)
        compose.onNodeWithContentDescription("全选").performClick()
        compose.onNodeWithContentDescription("全选").performClick()
        assertEquals(setOf(1L, 2L), selected)
        compose.onNodeWithText("已选 2 项").assertExists()
        compose.onNodeWithContentDescription("反选").performClick()
        assertEquals(emptySet<Long>(), selected)
        compose.onNodeWithContentDescription("所选任务操作").assertIsNotEnabled()
    }

    @Test fun longPressSelectsCardAndClickTogglesWithoutCheckbox() {
        var selecting by mutableStateOf(false)
        var selected by mutableStateOf(false)
        var pauses = 0
        compose.setContent {
            MaterialTheme {
                DownloadingItem(DownloadItemUi(1, music, 0, TaskStatus.QUEUED),
                    onRetry = {}, onPause = { pauses++ }, onResumeDownload = {}, onDelete = {},
                    onLongClick = { selecting = true; selected = true },
                    selectionMode = selecting, isSelected = selected, onToggleSelection = { selected = !selected })
            }
        }
        compose.onNodeWithText("test song").performTouchInput { longClick() }
        compose.onNodeWithText("test song").assertIsSelected().performClick()
        compose.onNodeWithText("test song").assertIsNotSelected()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        assertEquals(0, pauses)
    }

    @Test fun transcodingShowsItsOwnProgressWithoutNetworkEta() {
        compose.setContent {
            MaterialTheme {
                DownloadingItem(
                    DownloadItemUi(
                        1,
                        music,
                        80,
                        TaskStatus.DOWNLOADING,
                        telemetry = DownloadTelemetry(DownloadStage.TRANSCODING, 25, 1024, 1024, 999, 3),
                    ),
                    onRetry = {},
                    onPause = {},
                    onResumeDownload = {},
                    onDelete = {},
                    onLongClick = {},
                )
            }
        }
        compose.onNodeWithText("正在转码 · 25%").assertExists()
        compose.onNodeWithText("网络下载预计剩余", substring = true).assertDoesNotExist()
    }
}
