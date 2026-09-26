package com.guang.cloudx.logic.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Music
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadMigrationTest {
    @Test fun versionThreeKeepsHistoryAndCreatesValidLocalFileSchema() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "download-migration-test.db"
            context.deleteDatabase(name)
            val music = Music("song", emptyList(), Album("album", 1, ""), 42)
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL(
                    """
                    CREATE TABLE DownloadInfo (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        music TEXT NOT NULL,
                        progress INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        timeStamp INTEGER NOT NULL,
                        failureReason TEXT,
                        downloadLevel TEXT NOT NULL DEFAULT 'standard',
                        rulesJson TEXT NOT NULL DEFAULT '',
                        targetUri TEXT NOT NULL DEFAULT '',
                        savedFileName TEXT DEFAULT NULL
                    )
                    """.trimIndent(),
                )
                old.execSQL(
                    "INSERT INTO DownloadInfo (music, progress, status, timeStamp, savedFileName) VALUES (?, 100, 'COMPLETED', 1, 'song.flac')",
                    arrayOf(Gson().toJson(music)),
                )
                old.version = 3
            }
            val upgraded =
                Room
                    .databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(AppDatabase.MIGRATION_3_4)
                    .build()
            try {
                val record = upgraded.downloadDao().getAllDownloads().single()
                assertEquals(music, record.music)
                assertEquals("song.flac", record.savedFileName)
                assertNull(record.savedFileUri)
                assertTrue(upgraded.localMusicDao().getAll().isEmpty())
                upgraded.localMusicDao().upsert(
                    LocalMusicFile(
                        uri = "audio",
                        musicId = 42,
                        treeUri = "tree",
                        displayName = "song.flac",
                        downloadLevel = "lossless",
                        downloadedAt = 2L,
                    ),
                )
                assertEquals(42L, upgraded.localMusicDao().find("audio")!!.musicId)
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }
}
