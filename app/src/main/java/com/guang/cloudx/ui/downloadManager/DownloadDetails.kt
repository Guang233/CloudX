package com.guang.cloudx.ui.downloadManager

import com.guang.cloudx.logic.database.LocalMusicFile
import com.guang.cloudx.logic.model.DownloadedFileMetadata
import com.guang.cloudx.logic.model.FileAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadDetailSection(
    val title: String,
    val fields: List<Pair<String, String>>,
)

fun downloadQualityLabel(level: String?): String =
    when (level) {
        "standard" -> "标准"
        "higher" -> "较高"
        "exhigh" -> "极高（HQ）"
        "lossless" -> "无损（SQ）"
        "hires" -> "Hi-Res（HR）"
        "jyeffect" -> "高清环绕声"
        "sky" -> "沉浸环绕声"
        "jymaster" -> "超清母带"
        null, "" -> "未记录"
        else -> level
    }

fun formatDownloadedFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "未知"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.ROOT, "%.2f %s (%d 字节)", value, units[unit], bytes)
}

fun formatDownloadedDuration(milliseconds: Long?): String {
    if (milliseconds == null || milliseconds < 0) return "未知"
    val seconds = milliseconds / 1000
    return if (seconds >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}

/** Shared by the scrollable UI and copy action so copied details never omit displayed fields. */
fun buildDownloadDetails(
    item: DownloadItemUi,
    indexedFile: LocalMusicFile?,
    metadata: DownloadedFileMetadata?,
): List<DownloadDetailSection> {
    val file = indexedFile?.takeIf { it.musicId == item.music.id && it.uri == item.savedFileUri }
    val live = metadata?.takeIf { file != null && file.state != LocalMusicFile.MISSING }
    val available = live?.takeIf { it.access == FileAccess.AVAILABLE }
    val filename = available?.displayName ?: file?.displayName ?: item.savedFileName ?: "未记录"
    val extension = filename.substringAfterLast('.', "").uppercase(Locale.ROOT).ifBlank { "未知" }
    val status =
        when {
            indexedFile != null && file == null -> "文件已被覆盖或记录不匹配"
            file == null -> "文件位置未确认"
            file.state == LocalMusicFile.MISSING || live?.access == FileAccess.MISSING -> "文件已删除或移动"
            live?.access == FileAccess.UNAVAILABLE -> "文件不可访问，请检查目录权限"
            live?.access == FileAccess.AVAILABLE -> "文件存在"
            file.state == LocalMusicFile.UNAVAILABLE -> "目录不可访问，请检查授权"
            else -> "正在读取文件信息…"
        }

    fun date(time: Long?): String =
        time?.takeIf { it > 0 }?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "未记录"
    val unknown = if (live?.access == FileAccess.LOADING) "读取中…" else "未知"
    return listOf(
        DownloadDetailSection(
            "歌曲信息",
            listOf(
                "标题" to item.music.name,
                "艺术家" to item.music.artists.joinToString("、") { "${it.name} (${it.id})" },
                "专辑" to item.music.album.name,
                "歌曲 ID" to item.music.id.toString(),
                "专辑 ID" to
                    item.music.album.id
                        .toString(),
            ),
        ),
        DownloadDetailSection(
            "下载记录",
            listOf(
                "请求音质" to downloadQualityLabel(item.downloadLevel),
                "记录音质" to downloadQualityLabel(file?.downloadLevel),
                "加入队列时间" to date(item.timeStamp),
                "文件记录时间" to date(file?.downloadedAt),
            ),
        ),
        DownloadDetailSection(
            "本地文件",
            buildList {
                add("文件状态" to status)
                add("文件名" to filename)
                add("文件格式" to extension)
                add("MIME 类型" to (available?.mimeType ?: unknown))
                add("文件大小" to (available?.let { formatDownloadedFileSize(it.sizeBytes) } ?: unknown))
                add("音频时长" to (available?.let { formatDownloadedDuration(it.durationMs) } ?: unknown))
                add("文件码率" to (available?.bitrate?.let { String.format(Locale.ROOT, "%.1f kbps", it / 1000.0) } ?: unknown))
                available?.sampleRate?.let { add("采样率" to "$it Hz") }
                available?.bitsPerSample?.let { add("位深" to "$it bit") }
                add("文件修改时间" to date(available?.modifiedAt))
                add("保存目录 URI" to item.targetUri.ifBlank { "未记录" })
                add("歌曲文件 URI" to (item.savedFileUri ?: "未记录"))
                add("关联歌词 URI" to (file?.lyricUri ?: "未记录独立歌词文件"))
            },
        ),
        DownloadDetailSection(
            "说明",
            listOf(
                "音质与格式" to "记录音质是下载时保存的音质信息；旧记录可能只保留请求音质。转码后的实际参数以文件读取结果为准，MP3 文件不会因来源是无损就成为无损。",
                "文件信息" to "无法读取的参数显示为未知；关联歌词仅展示记录的 URI，不代表文件一定存在。",
                "封面链接" to
                    item.music.album.picUrl
                        .ifBlank { "未记录" },
            ),
        ),
    )
}

fun List<DownloadDetailSection>.asCopyText(): String =
    joinToString("\n\n") { section ->
        section.title + "\n" + section.fields.joinToString("\n") { (label, value) -> "$label：$value" }
    }
