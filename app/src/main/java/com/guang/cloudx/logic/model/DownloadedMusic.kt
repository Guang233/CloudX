package com.guang.cloudx.logic.model

import com.guang.cloudx.logic.database.LocalMusicFile

/** Any confirmed local copy counts, independent of name, quality or download-task history. */
fun Iterable<LocalMusicFile>.downloadedMusicIds(): Set<Long> =
    filter { it.state == LocalMusicFile.PRESENT }.mapTo(mutableSetOf()) { it.musicId }

/** Explicit user action only: downloading/select-all never silently filters these songs. */
fun Set<Music>.withoutDownloaded(downloadedIds: Set<Long>): Set<Music> = filterNotTo(linkedSetOf()) { it.id in downloadedIds }
