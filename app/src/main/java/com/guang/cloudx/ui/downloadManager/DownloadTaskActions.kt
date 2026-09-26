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
    TooltipIconButton(
        onClick = { onPause(pauseIds) },
        imageVector = Icons.Default.Pause,
        contentDescription = if (selectionMode) "暂停所选" else "全部暂停",
        enabled = pauseIds.isNotEmpty(),
    )
    TooltipIconButton(
        onClick = { onContinue(continueIds) },
        imageVector = Icons.Default.PlayArrow,
        contentDescription = if (selectionMode) "继续所选" else "全部继续",
        enabled = continueIds.isNotEmpty(),
    )
    if (selectionMode) {
        TooltipIconButton(
            onClick = { onCancel(cancelIds) },
            imageVector = Icons.Default.Close,
            contentDescription = "取消所选任务",
            enabled = cancelIds.isNotEmpty(),
        )
        TooltipIconButton(
            onClick = onSelectAll,
            imageVector = Icons.Default.SelectAll,
            contentDescription = if (selectedIds.size == tasks.size && tasks.isNotEmpty()) "取消全选" else "全选任务",
            enabled = tasks.isNotEmpty(),
        )
    } else {
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
