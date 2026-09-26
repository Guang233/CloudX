package com.guang.cloudx

import com.guang.cloudx.logic.database.LocalMusicFile
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.DownloadedFileMetadata
import com.guang.cloudx.logic.model.FileAccess
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.ui.downloadManager.*
import org.junit.Assert.*
import org.junit.Test

class DownloadDetailsTest {
    private val item =
        DownloadItemUi(
            id = 1,
            music = Music("song", emptyList(), Album("album", 2, ""), 3),
            progress = 100,
            status = TaskStatus.COMPLETED,
            timeStamp = 1000,
            downloadLevel = "hires",
            targetUri = "tree",
            savedFileName = "song.mp3",
            savedFileUri = "audio",
        )
    private val file =
        LocalMusicFile(
            uri = "audio",
            musicId = 3,
            treeUri = "tree",
            displayName = "song.mp3",
            lyricUri = "lyrics",
            downloadLevel = "lossless",
            downloadedAt = 2000,
        )

    private fun fields(
        metadata: DownloadedFileMetadata?,
        source: LocalMusicFile? = file,
    ) = buildDownloadDetails(item, source, metadata).flatMap { it.fields }.toMap()

    @Test fun sizeDistinguishesUnknownZeroAndBinaryUnits() {
        assertEquals("未知", formatDownloadedFileSize(null))
        assertEquals("未知", formatDownloadedFileSize(-1))
        assertEquals("0 B", formatDownloadedFileSize(0))
        assertEquals("1023 B", formatDownloadedFileSize(1023))
        assertEquals("1.00 KiB (1024 字节)", formatDownloadedFileSize(1024))
        assertEquals("1.50 MiB (1572864 字节)", formatDownloadedFileSize(1572864))
    }

    @Test fun durationHandlesUnknownZeroAndHours() {
        assertEquals("未知", formatDownloadedDuration(null))
        assertEquals("未知", formatDownloadedDuration(-1))
        assertEquals("0:00", formatDownloadedDuration(0))
        assertEquals("3:05", formatDownloadedDuration(185999))
        assertEquals("1:01:01", formatDownloadedDuration(3661000))
    }

    @Test fun qualityNamesDoNotInventBitratesForLosslessOrUnknownLevels() {
        assertEquals("无损（SQ）", downloadQualityLabel("lossless"))
        assertEquals("Hi-Res（HR）", downloadQualityLabel("hires"))
        assertEquals("future-level", downloadQualityLabel("future-level"))
        assertEquals("未记录", downloadQualityLabel(null))
    }

    @Test fun requestedQualityRecordedQualityAndConvertedFileParametersStaySeparate() {
        val details =
            fields(
                DownloadedFileMetadata(
                    FileAccess.AVAILABLE,
                    displayName = "actual-name.mp3",
                    sizeBytes = 1024,
                    mimeType = "audio/mpeg",
                    durationMs = 185000,
                    bitrate = 320000,
                    sampleRate = 44100,
                ),
            )
        assertEquals("Hi-Res（HR）", details["请求音质"])
        assertEquals("无损（SQ）", details["记录音质"])
        assertEquals("actual-name.mp3", details["文件名"])
        assertEquals("MP3", details["文件格式"])
        assertEquals("320.0 kbps", details["文件码率"])
        assertEquals("44100 Hz", details["采样率"])
        assertEquals("3:05", details["音频时长"])
        assertNotEquals(details["加入队列时间"], details["文件记录时间"])
    }

    @Test fun missingOrUnavailableFilesDoNotShowMadeUpMetadata() {
        val missing = fields(DownloadedFileMetadata(FileAccess.MISSING))
        assertEquals("文件已删除或移动", missing["文件状态"])
        assertEquals("未知", missing["文件大小"])
        assertEquals("song.mp3", missing["文件名"])
        val unavailable = fields(DownloadedFileMetadata(FileAccess.UNAVAILABLE))
        assertTrue(unavailable.getValue("文件状态").contains("权限"))
        assertEquals("未知", unavailable["文件码率"])
    }

    @Test fun staleOrUnmatchedIndexCannotShowAnotherSongsMetadata() {
        val data = DownloadedFileMetadata(FileAccess.AVAILABLE, displayName = "wrong.flac", sizeBytes = 123)
        val overwritten = fields(data, file.copy(musicId = 99))
        assertEquals("文件已被覆盖或记录不匹配", overwritten["文件状态"])
        assertEquals("song.mp3", overwritten["文件名"])
        assertEquals("未知", overwritten["文件大小"])
        val deleted = fields(data, file.copy(state = LocalMusicFile.MISSING))
        assertEquals("文件已删除或移动", deleted["文件状态"])
        assertEquals("未知", deleted["文件大小"])
        assertEquals("未记录", fields(null, null)["记录音质"])
    }

    @Test fun copyIncludesEveryDisplayedFieldAndLocation() {
        val sections = buildDownloadDetails(item, file, DownloadedFileMetadata(FileAccess.AVAILABLE))
        val text = sections.asCopyText()
        sections.forEach { section ->
            assertTrue(text.contains(section.title))
            section.fields.forEach { (label, value) -> assertTrue(text.contains("$label：$value")) }
        }
        assertTrue(text.contains("歌曲文件 URI：audio"))
        assertTrue(text.contains("关联歌词 URI：lyrics"))
    }
}
