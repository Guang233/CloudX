package com.guang.cloudx.ui.downloadManager

import com.guang.cloudx.logic.model.DownloadStage
import com.guang.cloudx.logic.model.DownloadTelemetry
import com.guang.cloudx.logic.model.displayName

/** Immutable input for the one notification publisher, never ordered by worker callback arrival. */
data class NotificationDownload(
    val id: Long,
    val name: String,
    val telemetry: DownloadTelemetry?,
)

data class DownloadNotificationSummary(
    val title: String,
    val text: String,
    val expandedText: String,
)

fun buildDownloadNotificationSummary(
    active: List<NotificationDownload>,
    queued: Int,
    completed: Int,
): DownloadNotificationSummary {
    val title = "CloudX 下载任务"
    val counts = "进行中 ${active.size} 首 · 排队 $queued 首 · 本次完成 $completed 首"
    val network = active.mapNotNull { it.telemetry }.filter { it.stage == DownloadStage.DOWNLOADING }
    val rates = network.mapNotNull { it.bytesPerSecond }
    val measuredSpeed = rates.fold(0L) { sum, rate -> sum + rate.coerceIn(0, Long.MAX_VALUE - sum) }
    val speed =
        when {
            active.isEmpty() -> "正在准备任务…"
            network.isEmpty() -> "正在准备或处理音频"
            rates.isEmpty() -> "总下载速度：测速中"
            else -> "总下载速度：${formatTransferBytes(measuredSpeed)}/s" +
                if (rates.size < network.size) "（部分任务测速中）" else ""
        }
    val rows =
        active.sortedBy { it.id }.map { task ->
            val info = task.telemetry ?: DownloadTelemetry()
            val name =
                task.name
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .take(100)
            "$name · ${info.stage.displayName()}${info.stageProgress?.let { " $it%" } ?: ""}"
        }
    return DownloadNotificationSummary(title, "$counts · $speed", (listOf(counts, speed) + rows).joinToString("\n"))
}
