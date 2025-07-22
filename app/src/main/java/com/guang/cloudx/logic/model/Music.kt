package com.guang.cloudx.logic.model

data class Music(val name: String, val artists: List<Artist>, val album: Album, val id: Long)
data class Artist(val name: String, val id: Long)
data class Album(val name: String, val id: Long, val picUrl: String)
data class PlayList(val name: String, val coverImgUrl: String, val musics: List<Music>, val id: Long)

data class Lyric(val lrc: String, val tlyric: String, val romalrc: String)

data class MusicUrl(val url: String, val level: String)

