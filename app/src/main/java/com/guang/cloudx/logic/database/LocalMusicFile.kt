package com.guang.cloudx.logic.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Independent of the task history: clearing completed tasks must not forget local files. */
@Entity(indices = [Index("musicId")])
data class LocalMusicFile(
    @PrimaryKey val uri: String,
    val musicId: Long,
    val treeUri: String,
    val displayName: String,
    val lyricUri: String? = null,
    val downloadLevel: String,
    val downloadedAt: Long,
    val state: String = PRESENT,
) {
    companion object {
        const val PRESENT = "PRESENT"
        const val MISSING = "MISSING"
        const val UNAVAILABLE = "UNAVAILABLE"
    }
}

@Dao
interface LocalMusicDao {
    @Upsert
    suspend fun upsert(file: LocalMusicFile)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacy(file: LocalMusicFile)

    @Query("SELECT * FROM LocalMusicFile")
    fun observeAll(): Flow<List<LocalMusicFile>>

    @Query("SELECT * FROM LocalMusicFile")
    suspend fun getAll(): List<LocalMusicFile>

    @Query("SELECT * FROM LocalMusicFile WHERE uri = :uri")
    suspend fun find(uri: String): LocalMusicFile?

    @Query("UPDATE LocalMusicFile SET state = :state WHERE uri = :uri")
    suspend fun setState(
        uri: String,
        state: String,
    )

    @Query("UPDATE LocalMusicFile SET lyricUri = NULL WHERE lyricUri = :uri")
    suspend fun clearLyric(uri: String)

    @Query("SELECT COUNT(*) FROM LocalMusicFile WHERE lyricUri = :uri AND uri != :audioUri AND state != 'MISSING'")
    suspend fun otherLyricOwners(
        uri: String,
        audioUri: String,
    ): Int
}
