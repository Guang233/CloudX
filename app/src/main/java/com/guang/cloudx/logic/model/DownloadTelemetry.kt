package com.guang.cloudx.logic.model

/** Live measurements are deliberately not persisted as historical speed/ETA. */
data class DownloadTelemetry(
    val stage: DownloadStage = DownloadStage.PREPARING,
    val stageProgress: Int? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
)

/** One meter per attempt. The initial byte count is a checkpoint, not newly transferred data. */
class DownloadTransferMeter(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var value = DownloadTelemetry()
    private var sampledAt = clock()
    private var sampledBytes = 0L
    private var initialized = false

    @Synchronized
    fun bytes(
        downloaded: Long,
        total: Long?,
    ) {
        val count = downloaded.coerceAtLeast(0)
        if (!initialized || count < value.downloadedBytes) {
            initialized = true
            sampledAt = clock()
            sampledBytes = count
            value = value.copy(bytesPerSecond = null, etaSeconds = null)
        }
        val knownTotal = total?.takeIf { it > 0 }
        value =
            value.copy(
                stage = DownloadStage.DOWNLOADING,
                downloadedBytes = count,
                totalBytes = knownTotal,
                stageProgress = knownTotal?.let { (count.toDouble() / it * 100).toInt().coerceIn(0, 100) },
            )
    }

    @Synchronized
    fun stage(
        stage: DownloadStage,
        progress: Int,
    ) {
        value =
            value.copy(
                stage = stage,
                stageProgress =
                    when (stage) {
                        DownloadStage.DOWNLOADING -> value.stageProgress
                        DownloadStage.TRANSCODING, DownloadStage.COMPLETED -> progress.coerceIn(0, 100)
                        else -> null
                    },
                bytesPerSecond = if (stage == DownloadStage.DOWNLOADING) value.bytesPerSecond else null,
                etaSeconds = if (stage == DownloadStage.DOWNLOADING) value.etaSeconds else null,
            )
    }

    @Synchronized
    fun sample(): DownloadTelemetry {
        if (value.stage != DownloadStage.DOWNLOADING || !initialized) return value
        val now = clock()
        val elapsed = now - sampledAt
        if (elapsed >= 500) {
            val speed = ((value.downloadedBytes - sampledBytes).coerceAtLeast(0).toDouble() * 1000 / elapsed).toLong()
            val remaining = value.totalBytes?.let { (it - value.downloadedBytes).coerceAtLeast(0) }
            value =
                value.copy(
                    bytesPerSecond = speed,
                    etaSeconds =
                        if (speed > 0 && remaining != null) {
                            kotlin.math.ceil(remaining.toDouble() / speed).toLong()
                        } else {
                            null
                        },
                )
            sampledBytes = value.downloadedBytes
            sampledAt = now
        }
        return value
    }
}

fun DownloadStage.displayName(): String =
    when (this) {
        DownloadStage.PREPARING -> "正在获取下载信息"
        DownloadStage.DOWNLOADING -> "正在下载"
        DownloadStage.PROCESSING -> "正在检查音频"
        DownloadStage.WAITING_TRANSCODE -> "等待转码"
        DownloadStage.TRANSCODING -> "正在转码"
        DownloadStage.WRITING_TAGS -> "正在写入标签"
        DownloadStage.SAVING -> "正在保存文件"
        DownloadStage.COMPLETED -> "下载完成"
    }
