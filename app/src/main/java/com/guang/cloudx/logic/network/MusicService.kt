package com.guang.cloudx.logic.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface MusicService {
    @FormUrlEncoded
    @POST("eapi/search/song/list/page")
    fun searchMusic(@Field("params") body: String,
                    @Header("Cookie") cookie: String): Call<String>

    @FormUrlEncoded
    @POST("eapi/song/enhance/player/url/v1")
    fun getMusic(@Field("params") body: String,
                @Header("Cookie") cookie: String): Call<String>

    @FormUrlEncoded
    @POST("eapi/song/lyric/v1")
    fun getLyric(@Field("params") body: String,
                 @Header("Cookie") cookie: String): Call<String>

    @FormUrlEncoded
    @POST("eapi/v6/playlist/detail")
    fun getPlayList(@Field("params") body: String,
                    @Header("Cookie") cookie: String): Call<String>
}

