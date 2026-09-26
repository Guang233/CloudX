package com.guang.cloudx

import com.guang.cloudx.logic.model.*
import com.guang.cloudx.ui.downloadManager.*
import org.junit.Assert.*
import org.junit.Test

class DownloadTransferMeterTest {
    @Test fun resumedBytesAreNotCountedAsNewTraffic() {
        var time = 0L
        val meter = DownloadTransferMeter { time }
        meter.bytes(8_000, 10_000)
        time = 1000
        meter.bytes(9_000, 10_000)
        val result = meter.sample()
        assertEquals(1_000L, result.bytesPerSecond)
        assertEquals(1L, result.etaSeconds)
        assertEquals(90, result.stageProgress)
    }

    @Test fun unknownLengthHasSpeedButNoPercentOrEta() {
        var time = 0L
        val meter = DownloadTransferMeter { time }
        meter.bytes(0, null)
        time = 1000
        meter.bytes(2048, -1)
        val result = meter.sample()
        assertEquals(2048L, result.bytesPerSecond)
        assertNull(result.totalBytes)
        assertNull(result.stageProgress)
        assertNull(result.etaSeconds)
        assertTrue(transferSummary(result, true).contains("总大小未知"))
    }

    @Test fun stallClearsOldSpeedAndEta() {
        var time = 0L
        val meter = DownloadTransferMeter { time }
        meter.bytes(0, 10_000)
        time = 1000
        meter.bytes(5000, 10_000)
        assertEquals(5000L, meter.sample().bytesPerSecond)
        time = 2000
        assertEquals(0L, meter.sample().bytesPerSecond)
        assertNull(meter.sample().etaSeconds)
    }

    @Test fun rangeFallbackResetsBaseline() {
        var time = 0L
        val meter = DownloadTransferMeter { time }
        meter.bytes(8000, 10000)
        time = 1000
        meter.bytes(0, 10000)
        assertNull(meter.sample().bytesPerSecond)
        time = 2000
        meter.bytes(1000, 10000)
        assertEquals(1000L, meter.sample().bytesPerSecond)
    }

    @Test fun processingStagesHaveNoNetworkEtaAndOwnProgress() {
        var time = 0L
        val meter = DownloadTransferMeter { time }
        meter.bytes(0, 1000)
        time = 1000
        meter.bytes(1000, 1000)
        meter.sample()
        meter.stage(DownloadStage.TRANSCODING, 25)
        assertEquals(25, meter.sample().stageProgress)
        assertNull(meter.sample().bytesPerSecond)
        assertNull(meter.sample().etaSeconds)
        assertFalse(transferSummary(meter.sample(), true).contains("剩余"))
        meter.stage(DownloadStage.WRITING_TAGS, 0)
        assertNull(meter.sample().stageProgress)
        meter.stage(DownloadStage.SAVING, 0)
        assertNull(meter.sample().stageProgress)
    }

    @Test fun pausedTransferDoesNotDisplayStaleSpeed() {
        val text = transferSummary(DownloadTelemetry(DownloadStage.DOWNLOADING, 50, 512, 1024, 128, 4), false)
        assertEquals("512 B / 1.0 KiB", text)
        assertFalse(text.contains("/s"))
    }

    @Test fun totalAndProgressStayBounded() {
        val meter = DownloadTransferMeter { 0 }
        meter.bytes(-20, 0)
        assertEquals(0L, meter.sample().downloadedBytes)
        assertNull(meter.sample().stageProgress)
        meter.bytes(200, 100)
        assertEquals(100, meter.sample().stageProgress)
    }

    @Test fun statusAndActionEligibilityStayDistinct() {
        val music = Music("song", emptyList(), Album("album", 1, ""), 1)
        val item = DownloadItemUi(music = music, progress = 0, status = TaskStatus.QUEUED)
        assertEquals("排队中", downloadTaskStatusText(item))
        assertEquals("正在获取下载信息", downloadTaskStatusText(item.copy(status = TaskStatus.DOWNLOADING)))
        assertTrue(TaskStatus.QUEUED.canPause())
        assertTrue(TaskStatus.DOWNLOADING.canPause())
        assertFalse(TaskStatus.PAUSED.canPause())
        assertTrue(TaskStatus.PAUSED.canContinue())
        assertFalse(TaskStatus.FAILED.canContinue())
        assertFalse(TaskStatus.PAUSING.canContinue())
        assertFalse(TaskStatus.COMPLETED.canCancel())
        assertFalse(TaskStatus.CANCELLING.canCancel())
        assertTrue(TaskStatus.FAILED.canCancel())
    }
}
