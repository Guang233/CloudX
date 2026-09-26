package com.guang.cloudx.logic.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.ui.downloadManager.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DownloadTaskStateTest {
    private lateinit var db: AppDatabase
    private val music = Music("test", emptyList(), Album("album", 1, ""), 1)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun close() {
        db.close()
    }

    @Test fun batchPauseAndLateProgressCannotRevivePausedTasks() =
        runBlocking {
            val dao = db.downloadDao()
            val queued = dao.insert(DownloadInfo(music = music, progress = 0, status = TaskStatus.QUEUED, timeStamp = 0))
            val active = dao.insert(DownloadInfo(music = music, progress = 30, status = TaskStatus.DOWNLOADING, timeStamp = 0))
            val failed = dao.insert(DownloadInfo(music = music, progress = 0, status = TaskStatus.FAILED, timeStamp = 0))
            dao.markPausing(listOf(queued, active, failed))
            dao.updateProgress(active, 80, TaskStatus.DOWNLOADING)
            assertEquals(TaskStatus.PAUSING, dao.findById(queued)?.status)
            assertEquals(TaskStatus.PAUSING, dao.findById(active)?.status)
            assertEquals(30, dao.findById(active)?.progress)
            assertEquals(TaskStatus.FAILED, dao.findById(failed)?.status)
        }

    @Test fun committedTaskWinsOverCancellationAndIsNotDeleted() =
        runBlocking {
            val dao = db.downloadDao()
            val id = dao.insert(DownloadInfo(music = music, progress = 99, status = TaskStatus.DOWNLOADING, timeStamp = 0))
            dao.markCancelling(listOf(id))
            dao.complete(id, "saved.mp3", "content://test/saved")
            dao.deleteUnfinished(id)
            dao.markPausing(listOf(id))
            dao.markCancelling(listOf(id))
            dao.setUnfinishedStatus(id, TaskStatus.PAUSED)
            val completed = requireNotNull(dao.findById(id))
            assertEquals(TaskStatus.COMPLETED, completed.status)
            assertEquals("content://test/saved", completed.savedFileUri)
        }

    @Test fun largeBatchesStayWithinSqliteVariableLimits() =
        runBlocking {
            val dao = db.downloadDao()
            val ids =
                (1..1005).map {
                    dao.insert(DownloadInfo(music = music, progress = 0, status = TaskStatus.QUEUED, timeStamp = 0))
                }
            dao.markPausing(ids)
            assertEquals(1005, dao.getDownloadsByStatus(TaskStatus.PAUSING).size)
            dao.markCancelling(ids)
            assertEquals(1005, dao.getDownloadsByStatus(TaskStatus.CANCELLING).size)
        }

    @Test fun cancelDeletesOnlyUnfinishedRecordsAndKeepsLocalIndex() =
        runBlocking {
            val dao = db.downloadDao()
            val id = dao.insert(DownloadInfo(music = music, progress = 0, status = TaskStatus.PAUSED, timeStamp = 0))
            val file =
                LocalMusicFile(
                    uri = "content://test/saved",
                    musicId = 1,
                    treeUri = "content://test/tree",
                    displayName = "saved.mp3",
                    downloadLevel = "standard",
                    downloadedAt = 0,
                )
            db.localMusicDao().upsert(file)
            dao.markCancelling(listOf(id))
            dao.deleteUnfinished(id)
            assertNull(dao.findById(id))
            assertEquals(
                1L,
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM LocalMusicFile").use {
                    it.moveToFirst()
                    it.getLong(0)
                },
            )
        }
}
