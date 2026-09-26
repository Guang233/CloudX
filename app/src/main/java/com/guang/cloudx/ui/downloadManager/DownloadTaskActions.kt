package com.guang.cloudx.ui.downloadManager

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.guang.cloudx.ui.home.TooltipIconButton

@Composable
fun DownloadTaskActions(
    tasks: List<DownloadItemUi>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onPause: (List<Long>) -> Unit,
    onContinue: (List<Long>) -> Unit,
    onCancel: (List<Long>) -> Unit,
    onRetryFailed: () -> Unit,
    onClearFailed: () -> Unit,
) {
    val targets = if (selectionMode) tasks.filter { it.id in selectedIds } else tasks
    val pauseIds = targets.filter { it.status.canPause() }.map { it.id }
    val continueIds = targets.filter { it.status.canContinue() }.map { it.id }
    val cancelIds = targets.filter { it.status.canCancel() }.map { it.id }
    if (selectionMode) {
        TooltipIconButton(
            onClick = onSelectAll,
            imageVector = Icons.Default.SelectAll,
            contentDescription = "全选",
            enabled = tasks.isNotEmpty(),
        )
        TooltipIconButton(
            onClick = onInvertSelection,
            imageVector = Icons.Default.FlipToFront,
            contentDescription = "反选",
            enabled = tasks.isNotEmpty(),
        )
        // Keep the same selection affordances as songs without squeezing five actions beside the count.
        var expanded by remember { mutableStateOf(false) }
        Box {
            TooltipIconButton(
                onClick = { expanded = true },
                imageVector = Icons.Default.MoreVert,
                contentDescription = "所选任务操作",
                enabled = cancelIds.isNotEmpty(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("暂停所选") },
                    leadingIcon = { Icon(Icons.Default.Pause, null) },
                    enabled = pauseIds.isNotEmpty(),
                    onClick = {
                        expanded = false
                        onPause(pauseIds)
                    },
                )
                DropdownMenuItem(
                    text = { Text("继续所选") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                    enabled = continueIds.isNotEmpty(),
                    onClick = {
                        expanded = false
                        onContinue(continueIds)
                    },
                )
                DropdownMenuItem(
                    text = { Text("取消所选任务") },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                    enabled = cancelIds.isNotEmpty(),
                    onClick = {
                        expanded = false
                        onCancel(cancelIds)
                    },
                )
            }
        }
    } else {
        TooltipIconButton(
            onClick = { onPause(pauseIds) },
            imageVector = Icons.Default.Pause,
            contentDescription = "全部暂停",
            enabled = pauseIds.isNotEmpty(),
        )
        TooltipIconButton(
            onClick = { onContinue(continueIds) },
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "全部继续",
            enabled = continueIds.isNotEmpty(),
        )
        var expanded by remember { mutableStateOf(false) }
        Box {
            TooltipIconButton(onClick = { expanded = true }, imageVector = Icons.Default.MoreVert, contentDescription = "更多任务操作")
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("多选任务") }, enabled = tasks.isNotEmpty(), onClick = {
                    expanded = false
                    onSelect()
                })
                DropdownMenuItem(
                    text = { Text("全部重试失败任务") },
                    enabled = tasks.any { it.status == TaskStatus.FAILED },
                    onClick = {
                        expanded = false
                        onRetryFailed()
                    },
                )
                DropdownMenuItem(
                    text = { Text("清理失败或暂停任务") },
                    enabled =
                        tasks.any {
                            it.status == TaskStatus.FAILED ||
                                it.status == TaskStatus.PAUSED
                        },
                    onClick = {
                        expanded = false
                        onClearFailed()
                    },
                )
            }
        }
    }
}
