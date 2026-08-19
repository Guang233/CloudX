package com.guang.cloudx.logic.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.ui.downloadManager.TaskStatus

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
    @ColumnInfo(defaultValue = "NULL") val savedFileName: String? = null
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromMusic(music: Music): String {
        return gson.toJson(music)
    }

    @TypeConverter
    fun toMusic(musicJson: String): Music {
        return gson.fromJson(musicJson, Music::class.java)
    }

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
                "WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, progress: Int, status: TaskStatus)

    @Query("SELECT * FROM DownloadInfo WHERE status = :status")
    suspend fun getDownloadsByStatus(status: TaskStatus): List<DownloadInfo>

    @Query("SELECT * FROM DownloadInfo")
    suspend fun getAllDownloads(): List<DownloadInfo>
}

@Database(entities = [DownloadInfo::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "download_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE DownloadInfo ADD COLUMN downloadLevel TEXT NOT NULL DEFAULT 'standard'"
                )
                database.execSQL(
                    "ALTER TABLE DownloadInfo ADD COLUMN rulesJson TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE DownloadInfo ADD COLUMN targetUri TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE DownloadInfo ADD COLUMN savedFileName TEXT DEFAULT NULL"
                )
            }
        }
    }
}
