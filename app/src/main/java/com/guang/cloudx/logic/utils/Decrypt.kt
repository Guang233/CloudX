package com.guang.cloudx.logic.utils

import android.annotation.SuppressLint
import com.guang.cloudx.logic.model.*
import com.guang.cloudx.util.ext.d
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
            val lrc = parseMixedLyrics(data.optJSONObject("lrc")?.optString("lyric").orEmpty())
            val tlyric = data.optJSONObject("tlyric")?.optString("lyric").orEmpty()
            val romalrc = data.optJSONObject("romalrc")?.optString("lyric").orEmpty()

            val yrc = convertMultiLineToLrc(parseMixedLyrics(data.optJSONObject("yrc")?.optString("lyric").orEmpty()))
            val ytlrc = convertMultiLineToLrc(data.optJSONObject("ytlrc")?.optString("lyric").orEmpty())
            val yromalrc = convertMultiLineToLrc(data.optJSONObject("yromalrc")?.optString("lyric").orEmpty())

            return Lyric(lrc, tlyric, romalrc, yrc, ytlrc, yromalrc)
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

                            val timeLabel = formatTime(timeMs)

                            val content = buildString {
                                val array = json.getJSONArray("c")
                                for (i in 0 until array.length()) {
                                    val item = array.getJSONObject(i)
                                    append(item.getString("tx"))
                                }
                            }

                            result.append("[$timeLabel]$content\n")
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

        private fun convertMultiLineToLrc(input: String): String =
            input.lines()
                .joinToString("\n") { line -> convertSingleLine(line) }

        private fun convertSingleLine(line: String): String {
            val result = StringBuilder()

            if (line.matches(Regex("""\[(\d+),(\d+)].+"""))) {
                // 1. 提取 [开始,结束]
                val lineTimeRegex = Regex("""\[(\d+),(\d+)]""")
                val timeMatch = lineTimeRegex.find(line)
                val endTimeMs = timeMatch?.groupValues?.get(2)?.toLongOrNull() ?: 0L

                // 2. 提取逐字部分
                val wordRegex = Regex("""\((\d+),\d+,\d+\)(.)""")
                val matches = wordRegex.findAll(line)

                for (match in matches) {
                    val wordTime = match.groupValues[1].toLongOrNull() ?: 0L
                    val word = match.groupValues[2]
                    result.append("[${formatTime(wordTime)}]").append(word)
                }

                // 3. 最后追加结尾时间戳
                if (endTimeMs > 0) {
                    result.append("[${formatTime(endTimeMs)}]")
                }
                return result.toString()
            }
            else
                return line
        }

        @SuppressLint("DefaultLocale")
        private fun formatTime(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = ms % 1000
            return String.format("%02d:%02d.%03d", minutes, seconds, millis)
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
            tracks.length().d()
            val music = with(ArrayList<Music>()) {
                for (i in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(i)
                    add(parseSong(track))
                }
                toList()
            }

            return PlayList(name, coverImgUrl, music, id)
        }

        fun decryptUserDetail(encryptedBody: String): User {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson).getJSONObject("profile")
            return User(data.getString("nickname"), data.getString("avatarUrl"))
        }

        fun decryptSendCaptcha(encryptedBody: String): Boolean {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson)
            return data.getInt("code") == 200 && data.getString("data") == "True"
        }

        fun decryptLogin(encryptedBody: String): UserData {
            val decryptedJson = AESECBHelper.decrypt(encryptedBody)
            val data = JSONObject(decryptedJson)
            return UserData(
                data.getJSONObject("account").getLong("id").toString(),
                data.getString("token"))
        }

    }
}