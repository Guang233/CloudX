package com.guang.cloudx.ui.update

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.guang.cloudx.BuildConfig
import com.guang.cloudx.logic.utils.toast

@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    UpdateDialog(
        state = state,
        currentVersion = BuildConfig.VERSION_NAME,
        onDismiss = viewModel::dismiss,
        onIgnoreVersion = viewModel::ignoreThisVersion,
        onOpenRelease = {
            state.release?.let { release ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, release.pageUrl.toUri()))
                    viewModel.dismiss()
                } catch (_: Exception) {
                    "无法打开浏览器，请稍后重试".toast(context)
                }
            }
        },
    )
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    onDismiss: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val release = state.release
    when {
        release != null -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("发现新版本 ${release.tag}") },
                text = {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("当前版本：$currentVersion")
                            Text(release.name, style = MaterialTheme.typography.titleSmall)
                            release.publishedAt?.let { Text("发布时间：$it") }
                            HorizontalDivider()
                            Text(release.notes)
                            Text("来源：GitHub · Guang233/CloudX", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onOpenRelease) { Text("前往 GitHub") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = onIgnoreVersion) { Text("此版本不再提示") }
                        TextButton(onClick = onDismiss) { Text("稍后") }
                    }
                },
            )
        }

        state.checking && state.manual -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("检查更新") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("正在连接 GitHub…")
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            )
        }

        state.message != null -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("检查更新") },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } },
            )
        }
    }
}
