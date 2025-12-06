package com.guang.cloudx.logic.database

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.ui.downloadManager.TaskStatus

@Entity
data class DownloadInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val music: Music,
    val progress: Int,
    val status: TaskStatus,
    val timeStamp: Long,
    val failureReason: String? = null
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

    @Query("SELECT * FROM DownloadInfo WHERE status = :status")
    suspend fun getDownloadsByStatus(status: TaskStatus): List<DownloadInfo>

    @Query("SELECT * FROM DownloadInfo")
    suspend fun getAllDownloads(): List<DownloadInfo>
}

@Database(entities = [DownloadInfo::class], version = 1)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
