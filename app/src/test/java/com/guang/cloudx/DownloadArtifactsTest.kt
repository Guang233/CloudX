package com.guang.cloudx

import com.guang.cloudx.logic.utils.clearDownloadArtifacts
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadArtifactsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancellationOnlyRemovesThisSongsTemporaryArtifacts() {
        val cache = temporary.newFolder()
        val owned =
            listOf(
                "download_temp/42",
                "download_temp/42.download.json",
                "download_temp/42.download.json.tmp",
                "download_temp/42.jpg",
                "download_temp/42.processing/source.m4a",
                "download_temp/42.processing/transcoded.mp3",
                "42",
                "42.download.json",
                "42.download.json.tmp",
            )
        val preserved =
            listOf(
                "download_temp/43",
                "download_temp/43.download.json",
                "download_temp/43.processing/source.mp3",
                "saved/song.mp3",
                "saved/song.lrc",
                "other-cache.jpg",
            )
        (owned + preserved).forEach { name ->
            File(cache, name).apply {
                parentFile!!.mkdirs()
                writeText(name)
            }
        }
        clearDownloadArtifacts(cache, 42)
        owned.forEach { assertFalse(it, File(cache, it).exists()) }
        preserved.forEach { assertEquals(it, File(cache, it).readText()) }
        assertFalse(File(cache, "download_temp/42.processing").exists())
    }

    @Test fun repeatedCleanupAndMissingArtifactsAreSafe() {
        val cache = temporary.newFolder()
        clearDownloadArtifacts(cache, 42)
        clearDownloadArtifacts(cache, 42)
        assertEquals(0, cache.listFiles()!!.size)
    }
}
