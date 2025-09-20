package com.guang.cloudx.logic.utils

import android.annotation.SuppressLint
import android.util.Log
import com.guang.cloudx.logic.model.Album
import com.guang.cloudx.logic.model.Artist
import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.MusicUrl
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.model.Music
import org.json.JSONObject

class Decrypt {
    companion object {
        fun decryptSearch(encryptedBody: String): List<Music> {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson)

            if (data.optInt("code") != 200) {
                throw Exception("Invalid data: ${data.optString("message", "Unknown error")}")
            }

            return data.optJSONObject("data")
                ?.optJSONArray("resources")
                ?.let { resources ->
                    List(resources.length()) { i ->
                        resources.getJSONObject(i)
                            .optJSONObject("baseInfo")
                            ?.optJSONObject("simpleSongData")
                            ?.let { simpleSongData ->
                                parseSong(simpleSongData)
                            } ?: throw IllegalStateException("Missing song data at index $i")
                    }
                } ?: throw IllegalStateException("No resources found in response")
        }
        private fun parseSong(simpleSongData: JSONObject): Music {
            val albumObj = simpleSongData.optJSONObject("al")
                ?: throw IllegalStateException("Missing album data")

            val album = Album(
                name = albumObj.optString("name", ""),
                id = albumObj.optLong("id", 0),
                picUrl = albumObj.optString("picUrl", "")
            )

            val artists = simpleSongData.optJSONArray("ar")?.let { arArray ->
                List(arArray.length()) { j ->
                    val artist = arArray.getJSONObject(j)
                    Artist(
                        name = artist.optString("name", ""),
                        id = artist.optLong("id", 0)
                    )
                }
            } ?: emptyList()

            return Music(
                name = simpleSongData.optString("name", ""),
                artists = artists,
                album = album,
                id = simpleSongData.optLong("id", 0)
            )
        }

        fun decryptLytic(encryptedBody: String): Lyric {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson)
            val lrc = parseMixedLyrics(data.getJSONObject("lrc").getString("lyric"))
            val tlyric = parseMixedLyrics(data.getJSONObject("tlyric").getString("lyric"))
            val romalrc = parseMixedLyrics(data.getJSONObject("romalrc").getString("lyric"))

            return Lyric(lrc, tlyric, romalrc)
        }
        @SuppressLint("DefaultLocale")
        private fun parseMixedLyrics(input: String): String {
            val lines = input.split("\n")
            val result = StringBuilder()
            lines.forEach { line ->
                when {
                    line.startsWith("{") && line.endsWith("}") -> {
                        try {
                            val json = JSONObject(line)
                            val timeMs = json.getLong("t")

                            // 转换毫秒为 [mm:ss.xx] 格式
                            val minutes = timeMs / 60000
                            val seconds = (timeMs % 60000) / 1000
                            val centiseconds = (timeMs % 1000) / 10

                            val timeLabel = String.format(
                                "[%02d:%02d.%02d]",
                                minutes, seconds, centiseconds
                            )

                            val content = buildString {
                                val array = json.getJSONArray("c")
                                for (i in 0 until array.length()) {
                                    val item = array.getJSONObject(i)
                                    append(item.getString("tx"))
                                }
                            }

                            result.append("$timeLabel$content\n")
                        } catch (e: Exception) {
                            result.append("$line\n")
                        }
                    }
                    line.startsWith("[") && line.contains("]") -> {
                        result.append("$line\n")
                    }
                    else -> {
                        result.append("$line\n")
                    }
                }
            }
            return result.toString().trim()
        }

        fun decryptMusicUrl(encryptedBody: String): MusicUrl {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson).getJSONArray("data").get(0) as JSONObject

            val url = data.getString("url")
            val level = data.getString("level")

            return MusicUrl(url, level)
        }

        fun decryptPlayList(encryptedBody: String): PlayList {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson).getJSONObject("playlist")

            val name = data.getString("name")
            val coverImgUrl = data.getString("coverImgUrl")
            val id = data.getLong("id")

            val tracks = data.getJSONArray("tracks")
            val music = with(ArrayList<Music>()) {
                for (i in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(i)
                    add(parseSong(track))
                }
                toList()
            }

            return PlayList(name, coverImgUrl, music, id)
        }
    }
}