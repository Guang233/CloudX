package com.guang.cloudx.ui.downloadManager

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.guang.cloudx.ui.home.TooltipIconButton

/** Switch selection controls without changing the download screen's original bar colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onCloseSelection: () -> Unit,
    actions: @Composable (Boolean) -> Unit,
) {
    AnimatedContent(
        targetState = selectionMode,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "DownloadTopBarState",
    ) { selecting ->
        TopAppBar(
            title = { Text(if (selecting) "已选 $selectedCount 项" else "下载管理", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                TooltipIconButton(
                    onClick = if (selecting) onCloseSelection else onBack,
                    imageVector = if (selecting) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (selecting) "关闭" else "返回",
                )
            },
            actions = { actions(selecting) },
            colors = TopAppBarDefaults.topAppBarColors(),
        )
    }
}
