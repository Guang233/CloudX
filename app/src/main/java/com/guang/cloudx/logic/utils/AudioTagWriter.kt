package com.guang.cloudx.logic.utils

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

object AudioTagWriter {

    data class TagInfo(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val lyrics: String? = null,
        val coverFile: File? = null
    )

    /**
     * 写入音频标签（支持 M4A / FLAC / MP3 等）
     */
    fun writeTags(file: File, info: TagInfo) {
        val audioFile: AudioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault

        // 标题
        info.title?.let { tag.setField(FieldKey.TITLE, it) }
        // 艺术家
        info.artist?.let { tag.setField(FieldKey.ARTIST, it) }
        // 专辑
        info.album?.let { tag.setField(FieldKey.ALBUM, it) }
        // 歌词
        info.lyrics?.let { tag.setField(FieldKey.LYRICS, it) }
        // 封面
        info.coverFile?.let {
            val artwork = AndroidArtwork.createArtworkFromFile(it)
            tag.deleteArtworkField()
            tag.setField(artwork)
        }

        // 提交保存
        audioFile.commit()
    }
}
