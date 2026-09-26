package com.guang.cloudx

import com.guang.cloudx.logic.model.*
import com.guang.cloudx.ui.downloadManager.*
import org.junit.Assert.*
import org.junit.Test

class DownloadNotificationSummaryTest {
    private fun song(
        id: Long,
        speed: Long = 1024,
    ) = NotificationDownload(
        id,
        "song $id",
        DownloadTelemetry(DownloadStage.DOWNLOADING, 50, 512, 1024, speed, 1),
    )

    @Test fun titleNeverAlternatesBetweenSongNames() {
        val one = buildDownloadNotificationSummary(listOf(song(1)), 2, 0)
        val two = buildDownloadNotificationSummary(listOf(song(2), song(1)), 1, 0)
        val next = buildDownloadNotificationSummary(listOf(song(2), song(3)), 0, 1)
        assertEquals("CloudX 下载任务", one.title)
        assertEquals(one.title, two.title)
        assertEquals(two.title, next.title)
        assertTrue(two.text.contains("进行中 2 首 · 排队 1 首"))
        assertTrue(next.text.contains("本次完成 1 首"))
    }

    @Test fun reorderedProgressCallbacksDoNotReorderNotificationRows() {
        val forward = buildDownloadNotificationSummary(listOf(song(1), song(2)), 0, 0)
        val reverse = buildDownloadNotificationSummary(listOf(song(2), song(1)), 0, 0)
        assertEquals(forward, reverse)
        assertTrue(forward.expandedText.indexOf("song 1") < forward.expandedText.indexOf("song 2"))
    }

    @Test fun combinedSpeedOnlyIncludesActiveNetworkTransfers() {
        val converting = NotificationDownload(3, "convert", DownloadTelemetry(DownloadStage.TRANSCODING, 25, 1024, 1024, 99999, 1))
        val text = buildDownloadNotificationSummary(listOf(song(1), song(2, 2048), converting), 2, 4)
        assertTrue(text.text.contains("总下载速度：3.0 KiB/s"))
        assertTrue(text.expandedText.contains("convert · 正在转码 25%"))
        assertFalse(text.expandedText.contains("剩余")) // No misleading shared ETA for mixed stages.
    }

    @Test fun missingSizeAndSpeedAreNotFabricated() {
        val unknown = NotificationDownload(1, "unknown", DownloadTelemetry(DownloadStage.DOWNLOADING, downloadedBytes = 10))
        val summary = buildDownloadNotificationSummary(listOf(unknown), 0, 0)
        assertTrue(summary.text.contains("测速中"))
        assertFalse(summary.expandedText.contains("%"))
        assertFalse(summary.expandedText.contains("/s"))
    }

    @Test fun partiallyMeasuredTotalsAreLabelled() {
        val waiting = NotificationDownload(2, "new song", DownloadTelemetry(DownloadStage.DOWNLOADING))
        val summary = buildDownloadNotificationSummary(listOf(song(1), waiting), 0, 0)
        assertTrue(summary.text.contains("1.0 KiB/s"))
        assertTrue(summary.text.contains("部分任务测速中"))
    }

    @Test fun speedAggregationCannotOverflowOrBecomeNegative() {
        val summary = buildDownloadNotificationSummary(listOf(song(1, Long.MAX_VALUE), song(2, Long.MAX_VALUE)), 0, 0)
        assertFalse(summary.text.contains("0 B/s"))
        assertFalse(summary.text.contains("：-"))
    }

    @Test fun defaultsAreIndependentAndBothTwo() {
        assertEquals(2, DownloadConcurrency.DEFAULT_PARTS)
        assertEquals(2, DownloadConcurrency.DEFAULT_SONGS)
        val rules = MusicDownloadRules(false, false, false, false, "name", ",", "UTF-8")
        assertEquals(2, rules.concurrentDownloads)
    }

    @Test fun waitingForEncoderHasNoFakeProgress() {
        val meter = DownloadTransferMeter { 0 }
        meter.stage(DownloadStage.WAITING_TRANSCODE, 0)
        val summary = buildDownloadNotificationSummary(listOf(NotificationDownload(1, "song", meter.sample())), 0, 0)
        assertTrue(summary.expandedText.contains("等待转码"))
        assertFalse(summary.expandedText.contains("%"))
    }
}
