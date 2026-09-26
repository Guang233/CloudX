package com.guang.cloudx.logic.model

/** Unknown values stay null: a missing size/bitrate is not zero. */
data class DownloadedFileMetadata(
    val access: FileAccess,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val modifiedAt: Long? = null,
    val durationMs: Long? = null,
    val bitrate: Long? = null,
    val sampleRate: Long? = null,
    val bitsPerSample: Int? = null,
)

enum class FileAccess { LOADING, AVAILABLE, MISSING, UNAVAILABLE }
