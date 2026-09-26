package com.guang.cloudx.ui.downloadManager

import com.guang.cloudx.logic.model.DownloadStage
import com.guang.cloudx.logic.model.DownloadTelemetry
import com.guang.cloudx.logic.model.displayName
import java.util.Locale

fun downloadTaskStatusText(item: DownloadItemUi): String =
    when (item.status) {
        TaskStatus.QUEUED -> {
            "排队中"
        }

        TaskStatus.PAUSING -> {
            "正在暂停…"
        }

        TaskStatus.CANCELLING -> {
            "正在取消并清理临时文件…"
        }

        TaskStatus.PAUSED -> {
            "已暂停"
        }

        TaskStatus.FAILED -> {
            "下载失败"
        }

        TaskStatus.COMPLETED -> {
            "下载完成"
        }

        TaskStatus.DOWNLOADING -> {
            val info = item.telemetry ?: DownloadTelemetry()
            info.stage.displayName() + (info.stageProgress?.let { " · $it%" } ?: "")
        }
    }

fun formatTransferBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0)
    return when {
        value >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GiB", value / (1024.0 * 1024 * 1024))
        value >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024))
        value >= 1024 -> String.format(Locale.ROOT, "%.1f KiB", value / 1024.0)
        else -> "$value B"
    }
}

/** ETA refers only to network transfer, never to transcoding/tag writing/SAF saving. */
fun transferSummary(
    info: DownloadTelemetry,
    active: Boolean,
): String {
    val size = "${formatTransferBytes(info.downloadedBytes)} / ${info.totalBytes?.let(::formatTransferBytes) ?: "总大小未知"}"
    if (!active || info.stage != DownloadStage.DOWNLOADING) return size
    val speed = info.bytesPerSecond?.let { "${formatTransferBytes(it)}/s" } ?: "测速中"
    val eta =
        info.etaSeconds?.let {
            val duration =
                when {
                    it >= 3600 -> "${it / 3600}小时${it % 3600 / 60}分"
                    it >= 60 -> "${it / 60}分${it % 60}秒"
                    else -> "${it}秒"
                }
            "网络下载预计剩余 $duration"
        } ?: "剩余时间未知"
    return "$size\n$speed · $eta"
}

fun downloadTransferText(item: DownloadItemUi): String? {
    if (item.status == TaskStatus.QUEUED || item.status == TaskStatus.COMPLETED) return null
    val info = item.telemetry ?: return null
    if (info.stage == DownloadStage.PREPARING) return null
    return transferSummary(info, item.status == TaskStatus.DOWNLOADING)
}
