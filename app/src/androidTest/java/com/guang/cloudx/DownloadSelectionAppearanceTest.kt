package com.guang.cloudx

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.guang.cloudx.ui.downloadManager.DownloadManagerTopBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class DownloadSelectionAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightTopBarKeepsItsBackgroundWhenSelecting() {
        assertStableBarBackground(lightColorScheme(surface = Color.White, surfaceContainer = Color.Red))
    }

    @Test fun darkTopBarKeepsItsBackgroundWhenSelecting() {
        assertStableBarBackground(darkColorScheme(surface = Color.Black, surfaceContainer = Color.Yellow))
    }

    private fun assertStableBarBackground(colors: ColorScheme) {
        var selecting by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = colors) {
                Box(Modifier.testTag("download-top-bar")) {
                    DownloadManagerTopBar(selecting, 1, {}, {}, actions = {})
                }
            }
        }
        val normal = compose.onNodeWithTag("download-top-bar").captureToImage().toPixelMap()
        val normalColor = normal[normal.width / 2, normal.height - 2]
        compose.runOnIdle { selecting = true }
        compose.waitForIdle() // Wait for the header crossfade, not a transient animation frame.
        val selected = compose.onNodeWithTag("download-top-bar").captureToImage().toPixelMap()
        assertEquals(normalColor, selected[selected.width / 2, selected.height - 2])
    }
}
