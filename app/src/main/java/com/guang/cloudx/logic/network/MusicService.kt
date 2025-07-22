package com.guang.cloudx.logic.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MusicService {
    @POST("eapi/search/song/list/page")
    fun searchMusic(@Body body: String,
                    @Header("Cookie") cookie: String): Call<String>

    @POST("eapi/song/enhance/player/url/v1")
    fun getMusic(@Body body: String,
                @Header("Cookie") cookie: String): Call<String>

    @POST("eapi/song/lyric/v1")
    fun getLyric(@Body body: String,
                 @Header("Cookie") cookie: String): Call<String>

    @POST("eapi/v6/playlist/detail")
    fun getPlayList(@Body body: String,
                    @Header("Cookie") cookie: String): Call<String>
}

