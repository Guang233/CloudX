package com.guang.cloudx.logic.network

import com.guang.cloudx.logic.model.Lyric
import com.guang.cloudx.logic.model.MusicUrl
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.User
import com.guang.cloudx.logic.utils.AESECBHelper
import com.guang.cloudx.logic.utils.Decrypt
import com.guang.cloudx.logic.utils.MD5Helper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object MusicNetwork {
    private val musicService = ServiceCreator.createService<MusicService>()

    suspend fun searchMusic(keyword: String, offset: Int, limit: Int, cookie: String): List<Music> {
        val bodyJson = "{\"keyword\":\"$keyword\",\"scene\":\"NORMAL\",\"limit\":\"$limit\",\"offset\":\"$offset\",\"needCorrect\":\"true\",\"e_r\":true,\"checkToken\":\"\",\"header\":\"\"}"
        val query = "/api/search/song/list/page-36cd479b6b5-$bodyJson-36cd479b6b5-${MD5Helper.md5("nobody/api/search/song/list/pageuse${bodyJson}md5forencrypt")}"
        val encryptedBody = musicService.searchMusic(AESECBHelper.encrypt(query), cookie)
            .await()
        return Decrypt.decryptSearch(
            encryptedBody
        )
    }

    suspend fun getLyrics(id: String, cookie: String): Lyric {
        val bodyJSON = "{\"id\":\"$id\",\"lv\":\"-1\",\"tv\":\"-1\",\"rv\":\"-1\",\"yv\":\"-1\",\"e_r\":true,\"header\":\"\"}"
        val  query = "/api/song/lyric/v1-36cd479b6b5-$bodyJSON-36cd479b6b5-${MD5Helper.md5("nobody/api/song/lyric/v1use${bodyJSON}md5forencrypt")}"
        return Decrypt.decryptLytic(
            musicService
                .getLyric(AESECBHelper.encrypt(query), cookie)
                .await()
        )
    }

    suspend fun getMusicUrl(id: String, level: String, cookie: String): MusicUrl {
        val bodyJSON = "{\"ids\":\"[\\\"$id\\\"]\",\"level\":\"$level\",\"immerseType\":\"c51\",\"encodeType\":\"aac\",\"trialMode\":\"-1\",\"e_r\":true,\"header\":\"\"}"
        val query = "/api/song/enhance/player/url/v1-36cd479b6b5-$bodyJSON-36cd479b6b5-${MD5Helper.md5("nobody/api/song/enhance/player/url/v1use${bodyJSON}md5forencrypt")}"
        return Decrypt.decryptMusicUrl(
            musicService
                .getMusic(AESECBHelper.encrypt(query), cookie)
                .await()
        )
    }

    // 后经过测试发现，如要获取完整歌单，必须传 cookie
    suspend fun getPlayList(id: String, cookie: String): PlayList {
        val bodyJSON = "{\"id\":\"$id\",\"n\":\"10000\",\"s\":\"0\",\"newStyle\":\"true\",\"e_r\":true,\"checkToken\":\"\",\"header\":\"\"}"
        val query = "/api/v6/playlist/detail-36cd479b6b5-$bodyJSON-36cd479b6b5-${MD5Helper.md5("nobody/api/v6/playlist/detailuse${bodyJSON}md5forencrypt")}"
        return Decrypt.decryptPlayList(
            musicService
                .getPlayList(AESECBHelper.encrypt(query), cookie)
                .await()
        )
    }

    suspend fun getUserDetail(id: String, cookie: String): User {
        val bodyJSON = "{\"all\":\"true\",\"userId\":\"$id\",\"e_r\":true,\"header\":\"\"}"
        val query = "/api/w/v1/user/detail/$id-36cd479b6b5-$bodyJSON-36cd479b6b5-${MD5Helper.md5("nobody/api/w/v1/user/detail/${id}use${bodyJSON}md5forencrypt")}"
        return Decrypt.decryptUserDetail(
            musicService.getUserDetail(
                AESECBHelper.encrypt(query), cookie)
                .await()
        )
    }

    private suspend fun <T> Call<T>.await(): T {

        return suspendCoroutine { continuation ->
            enqueue(object : Callback<T> {
                override fun onResponse(p0: Call<T?>, p1: Response<T?>) {
                    val body = p1.body()
                    if (body != null) {
                        continuation.resume(body)
                    } else continuation.resumeWithException(
                        RuntimeException("response body is null")
                    )
                }

                override fun onFailure(p0: Call<T?>, p1: Throwable) {
                    continuation.resumeWithException(p1)
                }
            })
        }
    }
}