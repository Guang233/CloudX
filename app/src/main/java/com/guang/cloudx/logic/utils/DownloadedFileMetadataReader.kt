package com.guang.cloudx.logic.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import com.guang.cloudx.logic.model.DownloadedFileMetadata
import com.guang.cloudx.logic.model.FileAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** On-demand only; never query a provider or parse audio from the composition/main thread. */
object DownloadedFileMetadataReader {
    suspend fun read(
        context: Context,
        fileUri: String,
    ): DownloadedFileMetadata =
        withContext(Dispatchers.IO) {
            val uri = fileUri.toUri()
            val file =
                try {
                    val projection =
                        arrayOf(
                            Document.COLUMN_DISPLAY_NAME,
                            Document.COLUMN_SIZE,
                            Document.COLUMN_MIME_TYPE,
                            Document.COLUMN_LAST_MODIFIED,
                        )
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) ||
                            !cursor.extras.getString(DocumentsContract.EXTRA_ERROR).isNullOrBlank()
                        ) return@withContext DownloadedFileMetadata(FileAccess.UNAVAILABLE)
                        if (!cursor.moveToFirst()) return@withContext DownloadedFileMetadata(FileAccess.MISSING)
                        DownloadedFileMetadata(
                            access = FileAccess.AVAILABLE,
                            displayName = cursor.getString(0),
                            sizeBytes = if (cursor.isNull(1)) null else cursor.getLong(1).takeIf { it >= 0 },
                            mimeType = cursor.getString(2),
                            modifiedAt = if (cursor.isNull(3)) null else cursor.getLong(3).takeIf { it > 0 },
                        )
                    } ?: return@withContext DownloadedFileMetadata(FileAccess.UNAVAILABLE)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@withContext DownloadedFileMetadata(FileAccess.UNAVAILABLE)
                }
            ensureActive()
            val retriever = try {
                MediaMetadataRetriever()
            } catch (_: Exception) {
                return@withContext file
            }
            try {
                retriever.setDataSource(context, uri)
                ensureActive()
                file.copy(
                    durationMs =
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?.takeIf { it >= 0 },
                    bitrate =
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            ?.toLongOrNull()
                            ?.takeIf { it > 0 },
                    sampleRate =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            retriever
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                                ?.toLongOrNull()
                                ?.takeIf { it > 0 }
                        } else {
                            null
                        },
                    bitsPerSample =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            retriever
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                                ?.toIntOrNull()
                                ?.takeIf { it > 0 }
                        } else {
                            null
                        },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Some providers/formats cannot expose audio parameters; keep basic file information.
                file
            } finally {
                runCatching { retriever.release() }
            }
        }
}
