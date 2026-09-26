package com.guang.cloudx.logic.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.database.DownloadInfo
import com.guang.cloudx.logic.database.LocalMusicFile
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.downloadedMusicIds
import com.guang.cloudx.ui.downloadManager.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMusicRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var documents: FakeDocuments
    private lateinit var repository: LocalMusicRepository

    private class FakeDocuments : LocalDocuments {
        val files = mutableListOf<LocalDocument>()
        val deleted = mutableListOf<String>()
        val failDelete = mutableSetOf<String>()
        var inaccessible = false

        override fun children(tree: String): List<LocalDocument> {
            if (inaccessible) throw SecurityException("permission revoked")
            return files.toList()
        }

        override fun delete(uri: String): Boolean {
            if (uri in failDelete) return false
            deleted += uri
            files.removeAll { it.uri == uri }
            return true
        }
    }

    @Before fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java,
                ).build()
        documents = FakeDocuments()
        repository = LocalMusicRepository(db, documents)
    }

    @After fun close() {
        db.close()
    }

    private fun file(
        uri: String = "audio",
        id: Long = 1,
        lyric: String? = null,
    ) = LocalMusicFile(
        uri = uri,
        musicId = id,
        treeUri = "tree",
        displayName = "$uri.flac",
        lyricUri = lyric,
        downloadLevel = "lossless",
        downloadedAt = 1L,
    )

    private fun task(
        id: Long = 1,
        name: String = "audio.flac",
    ) = DownloadInfo(
        music = Music("song", emptyList(), Album("album", 1, ""), id),
        progress = 100,
        status = TaskStatus.COMPLETED,
        timeStamp = 1L,
        targetUri = "tree",
        savedFileName = name,
    )

    @Test fun clearingHistoryKeepsIndependentIndex() =
        runBlocking {
            db.localMusicDao().upsert(file())
            db.downloadDao().insert(task())
            db.downloadDao().deleteAllByStatus(TaskStatus.COMPLETED)
            assertTrue(
                db
                    .downloadDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            assertEquals(
                setOf(1L),
                db
                    .localMusicDao()
                    .observeAll()
                    .first()
                    .downloadedMusicIds(),
            )
        }

    @Test fun deletingOneCopyRetainsOtherCopyAndUnrequestedLyrics() =
        runBlocking {
            db.localMusicDao().upsert(file(lyric = "lyric"))
            db.localMusicDao().upsert(file("copy"))
            documents.files += listOf(LocalDocument("audio", "audio.flac"), LocalDocument("lyric", "audio.lrc"))
            repository.deleteFile("audio", 1, false)
            assertEquals(listOf("audio"), documents.deleted)
            assertEquals(LocalMusicFile.MISSING, db.localMusicDao().find("audio")!!.state)
            assertEquals(setOf(1L), db.localMusicDao().getAll().downloadedMusicIds())
        }

    @Test fun failedDeletionPreservesFileIndex() =
        runBlocking {
            db.localMusicDao().upsert(file())
            documents.files += LocalDocument("audio", "audio.flac")
            documents.failDelete += "audio"
            assertTrue(runCatching { repository.deleteFile("audio", 1, false) }.isFailure)
            assertEquals(LocalMusicFile.PRESENT, db.localMusicDao().find("audio")!!.state)
        }

    @Test fun onlyRecordedLyricsAreDeletedAndFailuresCanBeRetried() =
        runBlocking {
            db.localMusicDao().upsert(file(lyric = "lyric"))
            documents.files +=
                listOf(
                    LocalDocument("audio", "audio.flac"),
                    LocalDocument("lyric", "audio.lrc"),
                    LocalDocument("unrelated", "audio.txt"),
                )
            documents.failDelete += "lyric"
            assertTrue(repository.deleteFile("audio", 1, true).contains("歌词删除失败"))
            assertEquals(LocalMusicFile.MISSING, db.localMusicDao().find("audio")!!.state)
            assertEquals("lyric", db.localMusicDao().find("audio")!!.lyricUri)
            documents.failDelete.clear()
            repository.deleteFile("audio", 1, true)
            assertEquals(listOf("audio", "lyric"), documents.deleted)
            assertNull(db.localMusicDao().find("audio")!!.lyricUri)
            assertEquals(listOf("unrelated"), documents.files.map { it.uri })
        }

    @Test fun sharedLyricsAreNotDeleted() =
        runBlocking {
            db.localMusicDao().upsert(file(lyric = "lyric"))
            db.localMusicDao().upsert(file("copy", lyric = "lyric"))
            documents.files += listOf(LocalDocument("audio", "audio.flac"), LocalDocument("lyric", "audio.lrc"))
            assertTrue(repository.deleteFile("audio", 1, true).contains("已保留"))
            assertEquals(listOf("audio"), documents.deleted)
        }

    @Test fun staleTaskCannotDeleteAnotherSongAtSameUri() =
        runBlocking {
            db.localMusicDao().upsert(file(id = 2))
            documents.files += LocalDocument("audio", "audio.flac")
            assertTrue(runCatching { repository.deleteFile("audio", 1, true) }.isFailure)
            assertTrue(documents.deleted.isEmpty())
        }

    @Test fun permissionLossIsNotDeletionAndRestoringAccessRestoresMarker() =
        runBlocking {
            db.localMusicDao().upsert(file())
            documents.files += LocalDocument("audio", "audio.flac")
            documents.inaccessible = true
            repository.refresh()
            assertEquals(LocalMusicFile.UNAVAILABLE, db.localMusicDao().find("audio")!!.state)
            assertTrue(runCatching { repository.deleteFile("audio", 1, false) }.isFailure)
            documents.inaccessible = false
            repository.refresh()
            assertEquals(LocalMusicFile.PRESENT, db.localMusicDao().find("audio")!!.state)
            documents.files.clear()
            repository.refresh()
            assertEquals(LocalMusicFile.MISSING, db.localMusicDao().find("audio")!!.state)
        }

    @Test fun legacyBackfillIsIdempotentAndNeverGuessesLyricUri() =
        runBlocking {
            db.downloadDao().insert(task())
            documents.files += LocalDocument("audio", "audio.flac")
            repository.refresh()
            repository.refresh()
            assertEquals(1, db.localMusicDao().getAll().size)
            assertNull(db.localMusicDao().find("audio")!!.lyricUri)
            assertEquals(
                "audio",
                db
                    .downloadDao()
                    .getAllDownloads()
                    .single()
                    .savedFileUri,
            )
            repository.deleteFile("audio", 1, false)
            repository.refresh()
            assertTrue(
                db
                    .localMusicDao()
                    .getAll()
                    .downloadedMusicIds()
                    .isEmpty(),
            )
        }

    @Test fun ambiguousLegacyNamesAreNotAssociatedWithWrongSong() =
        runBlocking {
            db.downloadDao().insert(task(1))
            db.downloadDao().insert(task(2))
            documents.files += LocalDocument("audio", "audio.flac")
            repository.refresh()
            assertTrue(db.localMusicDao().getAll().isEmpty())
        }

    @Test fun lateProgressCannotUndoCompletionOrLoseFileUri() =
        runBlocking {
            val id = db.downloadDao().insert(task().copy(status = TaskStatus.DOWNLOADING, progress = 0))
            db.downloadDao().complete(id, "audio.flac", "audio")
            db.downloadDao().updateProgress(id, 80, TaskStatus.DOWNLOADING)
            val saved = db.downloadDao().getAllDownloads().single()
            assertEquals(TaskStatus.COMPLETED, saved.status)
            assertEquals(100, saved.progress)
            assertEquals("audio", saved.savedFileUri)
        }
}
