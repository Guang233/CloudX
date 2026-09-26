package com.guang.cloudx.logic.repository

import android.content.Context
import androidx.room.withTransaction
import com.guang.cloudx.logic.database.AppDatabase
import com.guang.cloudx.logic.database.LocalMusicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Tracks only files saved by CloudX; no device-wide media scan or filename-based deletion. */
class LocalMusicRepository internal constructor(
    private val database: AppDatabase,
    private val documents: LocalDocuments,
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext),
        SafLocalDocuments(context.applicationContext),
    )

    private val dao = database.localMusicDao()
    val files = dao.observeAll()

    companion object {
        // Serializes SAF saves, deletion and reconciliation across all screens/services.
        internal val fileMutex = Mutex()
    }

    suspend fun refresh() =
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val legacy = database.downloadDao().getLegacyCompleted()
                val indexed = dao.getAll().filter { it.state != LocalMusicFile.MISSING }
                val trees =
                    (legacy.map { it.targetUri } + indexed.map { it.treeUri })
                        .filter { it.isNotBlank() }
                        .distinct()
                for (tree in trees) {
                    val children =
                        try {
                            documents.children(tree)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            indexed.filter { it.treeUri == tree && it.state != LocalMusicFile.UNAVAILABLE }.forEach {
                                dao.setState(it.uri, LocalMusicFile.UNAVAILABLE)
                            }
                            continue
                        }
                    val byUri = children.associateBy { it.uri }
                    indexed.filter { it.treeUri == tree }.forEach {
                        val state = if (it.uri in byUri) LocalMusicFile.PRESENT else LocalMusicFile.MISSING
                        if (it.state != state) dao.setState(it.uri, state)
                    }
                    // Only import unambiguous legacy names. Never guess a sidecar lyric association.
                    val byName = children.groupBy { it.name }
                    val candidates = legacy.filter { it.targetUri == tree }.groupBy { it.savedFileName }
                    for ((name, tasks) in candidates) {
                        if (tasks.map { it.music.id }.distinct().size != 1) continue
                        val document = byName[name]?.singleOrNull() ?: continue
                        val existing = dao.find(document.uri)
                        if (existing != null && existing.musicId != tasks.first().music.id) continue
                        database.withTransaction {
                            val task = tasks.first()
                            dao.insertLegacy(
                                LocalMusicFile(
                                    uri = document.uri,
                                    musicId = task.music.id,
                                    treeUri = tree,
                                    displayName = document.name,
                                    downloadLevel = task.downloadLevel,
                                    downloadedAt = task.timeStamp,
                                ),
                            )
                            tasks.forEach { database.downloadDao().setSavedFileUri(it.id, document.uri) }
                        }
                    }
                }
            }
        }

    /** Keep history even after deletion; any other valid copy of this song still counts. */
    suspend fun deleteFile(
        uri: String,
        musicId: Long,
        includeLyrics: Boolean,
    ): String =
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val file = dao.find(uri) ?: error("未确认文件位置，无法删除")
                check(file.musicId == musicId) { "该文件已被另一首歌曲覆盖，不能删除" }
                val children = documents.children(file.treeUri).mapTo(mutableSetOf()) { it.uri }
                // Once deletion starts, always persist the result even if the screen is closed.
                withContext(NonCancellable) {
                    if (file.state != LocalMusicFile.MISSING && uri in children) {
                        check(documents.delete(uri)) { "歌曲文件删除失败，请检查目录写入权限" }
                    }
                    dao.setState(uri, LocalMusicFile.MISSING)
                    val lyricUri = file.lyricUri
                    if (includeLyrics && lyricUri != null) {
                        if (dao.otherLyricOwners(lyricUri, uri) > 0) {
                            return@withContext "歌曲文件已删除；歌词仍被其他歌曲文件使用，已保留"
                        }
                        if (lyricUri in children) {
                            val deleted =
                                try {
                                    documents.delete(lyricUri)
                                } catch (_: Exception) {
                                    false
                                }
                            if (!deleted) return@withContext "歌曲文件已删除，但歌词删除失败，可重试"
                        }
                        dao.clearLyric(lyricUri)
                    }
                    "本地歌曲文件已删除，下载记录已保留"
                }
            }
        }
}
