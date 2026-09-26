package com.guang.cloudx

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.withoutDownloaded
import com.guang.cloudx.ui.home.DeselectDownloadedButton
import com.guang.cloudx.ui.home.MusicItem
import com.guang.cloudx.ui.home.MainTopBar
import com.guang.cloudx.ui.playList.PlayListTopBar
import com.guang.cloudx.ui.downloadManager.DownloadDetailSection
import com.guang.cloudx.ui.downloadManager.DownloadDetailsContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DownloadedMusicUiTest {
    @get:Rule val compose = createComposeRule()

    private fun song(id: Long) = Music("song", emptyList(), Album("album", 1, ""), id)

    @Test fun downloadedCardStillDownloadsImmediately() {
        var downloads = 0
        compose.setContent {
            MaterialTheme {
                MusicItem(
                    music = song(1),
                    isDownloaded = true,
                    isMultiSelectMode = false,
                    isSelected = false,
                    onDownloadClick = { downloads++ },
                    onClick = {},
                    onLongClick = {},
                )
            }
        }
        compose.onNodeWithText("已下载").assertIsDisplayed()
        compose.onNodeWithContentDescription("下载").performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }

    @Test fun deselectionIsAnExplicitSingleClick() {
        var selected = setOf(song(1), song(2))
        compose.setContent {
            MaterialTheme {
                DeselectDownloadedButton(enabled = true) {
                    selected = selected.withoutDownloaded(setOf(1L))
                }
            }
        }
        compose.runOnIdle { assertEquals(2, selected.size) }
        compose.onNodeWithContentDescription("取消选择已下载").performClick()
        compose.runOnIdle { assertEquals(setOf(song(2)), selected) }
    }

    @Test fun searchToolbarContainsDeselectActionOnSameRow() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                MainTopBar(
                    isSearchMode = false, isMultiSelectMode = true, inputText = "", selectedCount = 2,
                    hasDownloadedSelection = true, onDeselectDownloaded = { clicks++ },
                    onMenuClick = {}, onSearchClick = {}, onSearchTextChange = {}, onSearch = {},
                    onBackClick = {}, onSelectAll = {}, onInvertSelection = {}, onDownloadSelected = {},
                )
            }
        }
        val action = compose.onNodeWithContentDescription("取消选择已下载").assertIsDisplayed()
        val download = compose.onNodeWithContentDescription("下载").assertIsDisplayed()
        assertEquals(download.fetchSemanticsNode().boundsInRoot.center.y, action.fetchSemanticsNode().boundsInRoot.center.y, 1f)
        action.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun playlistToolbarDisablesDeselectWhenNoDownloadedSongsAreSelected() {
        compose.setContent {
            MaterialTheme {
                PlayListTopBar(
                    title = "album", isMultiSelectMode = true, selectedCount = 1,
                    hasDownloadedSelection = false, onDeselectDownloaded = {}, alpha = 1f,
                    onBackClick = {}, onSelectAll = {}, onInvertSelection = {}, onDownloadSelected = {},
                )
            }
        }
        val action = compose.onNodeWithContentDescription("取消选择已下载").assertIsDisplayed().assertIsNotEnabled()
        val download = compose.onNodeWithContentDescription("下载").assertIsDisplayed()
        assertEquals(download.fetchSemanticsNode().boundsInRoot.center.y, action.fetchSemanticsNode().boundsInRoot.center.y, 1f)
    }

    @Test fun longDetailsCanScrollToFileLocation() {
        compose.setContent {
            MaterialTheme {
                DownloadDetailsContent(listOf(DownloadDetailSection(
                    "本地文件", (1..20).map { "参数 $it" to "值 $it" } + ("歌曲文件 URI" to "content://example/music"),
                )))
            }
        }
        compose.onNodeWithText("content://example/music").performScrollTo().assertIsDisplayed()
    }
}
