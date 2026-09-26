package com.guang.cloudx

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.guang.cloudx.logic.repository.AppRelease
import com.guang.cloudx.ui.update.UpdateDialog
import com.guang.cloudx.ui.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticCheckDoesNotShowLoadingDialog() {
        compose.setContent {
            MaterialTheme {
                UpdateDialog(UpdateUiState(checking = true), "1.5.2", {}, {}, {})
            }
        }
        compose.onNodeWithText("检查更新").assertDoesNotExist()
    }

    @Test fun ignoreIsExplicitAndDoesNotOpenBrowser() {
        var ignored = 0
        var opened = 0
        val release = AppRelease("v1.6.0", "CloudX v1.6.0", "Fixes", null, "https://github.com/Guang233/CloudX/releases/tag/v1.6.0")
        compose.setContent {
            MaterialTheme {
                UpdateDialog(
                    state = UpdateUiState(release = release),
                    currentVersion = "1.5.2",
                    onDismiss = {},
                    onIgnoreVersion = { ignored++ },
                    onOpenRelease = { opened++ },
                )
            }
        }
        compose.onNodeWithText("发现新版本 v1.6.0").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithText("此版本不再提示").performClick()
        compose.runOnIdle {
            assertEquals(1, ignored)
            assertEquals(0, opened)
        }
    }

    @Test fun manualCheckCanBeCancelled() {
        var dismissed = 0
        compose.setContent {
            MaterialTheme {
                UpdateDialog(UpdateUiState(checking = true, manual = true), "1.5.2", { dismissed++ }, {}, {})
            }
        }
        compose.onNodeWithText("正在连接 GitHub…").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }
}
