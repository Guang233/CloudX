package com.guang.cloudx.logic.model

/** Independent limits: songs in flight vs HTTP ranges within each song. */
object DownloadConcurrency {
    const val DEFAULT_PARTS = 2
    const val MAX_PARTS = 8
    const val DEFAULT_SONGS = 2
    const val MAX_SONGS = 4
}
