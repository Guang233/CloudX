package com.guang.cloudx.logic.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.ui.downloadManager.TaskStatus
import kotlinx.coroutines.flow.Flow

@Entity
data class DownloadInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val music: Music,
    val progress: Int,
    var status: TaskStatus,
    val timeStamp: Long,
    var failureReason: String? = null,
    @ColumnInfo(defaultValue = "'standard'") val downloadLevel: String = "standard",
    @ColumnInfo(defaultValue = "''") val rulesJson: String = "",
    @ColumnInfo(defaultValue = "''") val targetUri: String = "",
    @ColumnInfo(defaultValue = "NULL") val savedFileName: String? = null,
    @ColumnInfo(defaultValue = "NULL") val savedFileUri: String? = null,
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromMusic(music: Music): String = gson.toJson(music)

    @TypeConverter
    fun toMusic(musicJson: String): Music = gson.fromJson(musicJson, Music::class.java)

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(status: String): TaskStatus = TaskStatus.valueOf(status)
}

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(downloadInfo: DownloadInfo): Long

    @Update
    suspend fun update(downloadInfo: DownloadInfo)

    @Delete
    suspend fun delete(downloadInfo: DownloadInfo)

    @Query("DELETE FROM DownloadInfo")
    suspend fun deleteAll()

    @Query("DELETE FROM DownloadInfo WHERE status = :status")
    suspend fun deleteAllByStatus(status: TaskStatus)

    @Query(
        "UPDATE DownloadInfo SET progress = :progress, status = :status, failureReason = NULL " +
            "WHERE id = :id AND status = 'DOWNLOADING'",
    )
    suspend fun updateProgress(
        id: Long,
        progress: Int,
        status: TaskStatus,
    )

    @Query("SELECT * FROM DownloadInfo WHERE id = :id")
    suspend fun findById(id: Long): DownloadInfo?

    @Query("UPDATE DownloadInfo SET status = :status, failureReason = :reason WHERE id = :id AND status != 'COMPLETED'")
    suspend fun setUnfinishedStatus(
        id: Long,
        status: TaskStatus,
        reason: String? = null,
    )

    @Query("UPDATE DownloadInfo SET status = 'PAUSING', failureReason = NULL WHERE id IN (:ids) AND status IN ('DOWNLOADING', 'QUEUED')")
    suspend fun markPausingChunk(ids: List<Long>)

    @Query("UPDATE DownloadInfo SET status = 'CANCELLING', failureReason = NULL WHERE id IN (:ids) AND status != 'COMPLETED'")
    suspend fun markCancellingChunk(ids: List<Long>)

    // Stay below older Android SQLite's 999-variable limit; the entire batch is atomic.
    @Transaction
    suspend fun markPausing(ids: List<Long>) {
        ids.chunked(900).forEach { markPausingChunk(it) }
    }

    @Transaction
    suspend fun markCancelling(ids: List<Long>) {
        ids.chunked(900).forEach { markCancellingChunk(it) }
    }

    @Query("DELETE FROM DownloadInfo WHERE id = :id AND status != 'COMPLETED'")
    suspend fun deleteUnfinished(id: Long)

    @Query("SELECT * FROM DownloadInfo WHERE status = :status")
    suspend fun getDownloadsByStatus(status: TaskStatus): List<DownloadInfo>

    @Query("SELECT * FROM DownloadInfo")
    suspend fun getAllDownloads(): List<DownloadInfo>

    @Query("SELECT * FROM DownloadInfo ORDER BY timeStamp, id")
    fun observeAll(): Flow<List<DownloadInfo>>

    @Query(
        "SELECT * FROM DownloadInfo WHERE status = 'COMPLETED' AND savedFileUri IS NULL AND savedFileName IS NOT NULL ORDER BY timeStamp DESC, id DESC",
    )
    suspend fun getLegacyCompleted(): List<DownloadInfo>

    @Query("UPDATE DownloadInfo SET savedFileUri = :uri WHERE id = :id")
    suspend fun setSavedFileUri(
        id: Long,
        uri: String,
    )

    @Query("UPDATE DownloadInfo SET status = :status, failureReason = :reason WHERE id = :id")
    suspend fun setStatus(
        id: Long,
        status: TaskStatus,
        reason: String? = null,
    )

    @Query(
        "UPDATE DownloadInfo SET status = 'COMPLETED', progress = 100, failureReason = NULL, savedFileName = :name, savedFileUri = :uri WHERE id = :id",
    )
    suspend fun complete(
        id: Long,
        name: String?,
        uri: String?,
    )
}

@Database(entities = [DownloadInfo::class, LocalMusicFile::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    abstract fun localMusicDao(): LocalMusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "download_database",
                        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .build()
                INSTANCE = instance
                instance
            }

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE DownloadInfo ADD COLUMN downloadLevel TEXT NOT NULL DEFAULT 'standard'",
                    )
                    database.execSQL(
                        "ALTER TABLE DownloadInfo ADD COLUMN rulesJson TEXT NOT NULL DEFAULT ''",
                    )
                    database.execSQL(
                        "ALTER TABLE DownloadInfo ADD COLUMN targetUri TEXT NOT NULL DEFAULT ''",
                    )
                }
            }

        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE DownloadInfo ADD COLUMN savedFileUri TEXT DEFAULT NULL")
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS LocalMusicFile (
                            uri TEXT NOT NULL PRIMARY KEY,
                            musicId INTEGER NOT NULL,
                            treeUri TEXT NOT NULL,
                            displayName TEXT NOT NULL,
                            lyricUri TEXT,
                            downloadLevel TEXT NOT NULL,
                            downloadedAt INTEGER NOT NULL,
                            state TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_LocalMusicFile_musicId ON LocalMusicFile (musicId)")
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE DownloadInfo ADD COLUMN savedFileName TEXT DEFAULT NULL",
                    )
                }
            }
    }
}
