package com.guang.cloudx

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
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
                    DownloadTaskActions(tasks, true, setOf(1, 2), {}, {}, { paused = it }, { resumed = it }, { cancelled = it }, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("暂停所选").performClick()
        compose.onNodeWithContentDescription("继续所选").performClick()
        compose.onNodeWithContentDescription("取消所选任务").performClick()
        assertEquals(listOf(1L), paused)
        assertEquals(listOf(2L), resumed)
        assertEquals(listOf(1L, 2L), cancelled)
    }

    @Test fun emptyQueueDisablesAllPauseAndContinue() {
        compose.setContent {
            MaterialTheme {
                Row {
                    DownloadTaskActions(emptyList(), false, emptySet(), {}, {}, {}, {}, {}, {}, {})
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
        compose.onNodeWithText("test song").performClick()
        assertEquals(1, toggles)
        assertEquals(0, pauses)
        compose.onNodeWithText("排队中").assertExists()
        compose.onNodeWithContentDescription("取消任务").assertDoesNotExist()
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
