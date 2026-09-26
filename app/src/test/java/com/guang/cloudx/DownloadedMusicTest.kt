package com.guang.cloudx

import com.guang.cloudx.logic.database.LocalMusicFile
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.downloadedMusicIds
import com.guang.cloudx.logic.model.withoutDownloaded
import org.junit.Assert.*
import org.junit.Test

class DownloadedMusicTest {
    private fun file(
        uri: String,
        id: Long,
        state: String = LocalMusicFile.PRESENT,
    ) = LocalMusicFile(
        uri = uri,
        musicId = id,
        treeUri = "tree",
        displayName = "same-name.flac",
        downloadLevel = "lossless",
        downloadedAt = 1L,
        state = state,
    )

    private fun song(
        id: Long,
        name: String = "same-name",
    ) = Music(name, emptyList(), Album("album", 1, ""), id)

    @Test fun anyRemainingCopyKeepsDownloadedMarker() {
        val files = listOf(file("a", 1, LocalMusicFile.MISSING), file("b", 1))
        assertEquals(setOf(1L), files.downloadedMusicIds())
    }

    @Test fun missingAndInaccessibleFilesDoNotCountAsConfirmedDownloads() {
        val files = listOf(file("a", 1, LocalMusicFile.MISSING), file("b", 2, LocalMusicFile.UNAVAILABLE))
        assertTrue(files.downloadedMusicIds().isEmpty())
    }

    @Test fun copiesAndQualityVariantsProduceOneSongId() {
        val files = listOf(file("a", 1), file("b", 1).copy(downloadLevel = "standard"), file("c", 2))
        assertEquals(setOf(1L, 2L), files.downloadedMusicIds())
    }

    @Test fun deselectUsesSongIdsNotTitlesOrObjectEquality() {
        val downloaded = song(1, "renamed title")
        val sameTitleDifferentSong = song(2, "renamed title")
        val selected = linkedSetOf(downloaded, sameTitleDifferentSong)
        assertEquals(setOf(sameTitleDifferentSong), selected.withoutDownloaded(setOf(1L)))
        assertEquals(2, selected.size) // No implicit mutation/automatic selection filtering.
    }

    @Test fun deselectKeepsUnselectedSongsUnselectedAndRetainsOrder() {
        val selected = linkedSetOf(song(3), song(1), song(2))
        assertEquals(listOf(3L, 2L), selected.withoutDownloaded(setOf(1L, 4L)).map { it.id })
    }

    @Test fun deselectCanClearAllOrLeaveSelectionUnchanged() {
        val selected = setOf(song(1), song(2))
        assertTrue(selected.withoutDownloaded(setOf(1L, 2L)).isEmpty())
        assertEquals(selected, selected.withoutDownloaded(emptySet()))
        assertTrue(emptySet<Music>().withoutDownloaded(setOf(1L)).isEmpty())
    }
}
