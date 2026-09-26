package com.guang.cloudx.ui.downloadManager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guang.cloudx.logic.database.LocalMusicFile
import com.guang.cloudx.logic.model.DownloadedFileMetadata
import com.guang.cloudx.logic.model.FileAccess
import com.guang.cloudx.logic.utils.DownloadedFileMetadataReader
import com.guang.cloudx.logic.utils.SystemUtils

@Composable
fun DownloadDetailsDialog(
    item: DownloadItemUi,
    indexedFile: LocalMusicFile?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val file = indexedFile?.takeIf { it.musicId == item.music.id && it.uri == item.savedFileUri }
    key(item.id, file) {
        val metadata by produceState<DownloadedFileMetadata?>(initialValue = null) {
            if (file != null && file.state != LocalMusicFile.MISSING) {
                value = DownloadedFileMetadata(FileAccess.LOADING)
                value = DownloadedFileMetadataReader.read(context.applicationContext, file.uri)
            }
        }
        val sections = remember(item, indexedFile, metadata) { buildDownloadDetails(item, indexedFile, metadata) }
        val canOpen = file != null && file.state != LocalMusicFile.MISSING && metadata?.access == FileAccess.AVAILABLE
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("详细信息") },
            text = { DownloadDetailsContent(sections) },
            confirmButton = {
                Row {
                    TextButton(onClick = onOpen, enabled = canOpen) { Text("打开") }
                    TextButton(onClick = onShare, enabled = canOpen) { Text("分享") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            },
            dismissButton = {
                TextButton(onClick = { SystemUtils.copyToClipboard(context, "MusicDetail", sections.asCopyText()) }) {
                    Text("复制全部")
                }
            },
        )
    }
}

@Composable
fun DownloadDetailsContent(sections: List<DownloadDetailSection>) {
    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            sections.forEachIndexed { index, section ->
                if (index > 0) HorizontalDivider()
                Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                section.fields.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
