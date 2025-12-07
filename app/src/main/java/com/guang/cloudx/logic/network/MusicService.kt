package com.guang.cloudx.logic.network

import retrofit2.Call
import retrofit2.http.*

interface MusicService {
    @FormUrlEncoded
    @POST("eapi/search/song/list/page")
    fun searchMusic(
        @Field("params") body: String,
        @Header("Cookie") cookie: String
    ): Call<String>

    @FormUrlEncoded
    @POST("eapi/song/enhance/player/url/v1")
    fun getMusic(
        @Field("params") body: String,
        @Header("Cookie") cookie: String
    ): Call<String>

    @FormUrlEncoded
    @POST("eapi/song/lyric/v1")
    fun getLyric(
        @Field("params") body: String,
        @Header("Cookie") cookie: String
    ): Call<String>

    @FormUrlEncoded
    @POST("eapi/v6/playlist/detail")
    fun getPlayList(
        @Field("params") body: String,
        @Header("Cookie") cookie: String
    ): Call<String>

    @FormUrlEncoded
    @POST("eapi/w/v1/user/detail")
    fun getUserDetail(
        @Field("params") body: String,
        @Header("Cookie") cookie: String
    ): Call<String>

    @FormUrlEncoded
    @POST("eapi/sms/captcha/sent")
    fun sendCaptcha(@Field("params") body: String): Call<String>

    @FormUrlEncoded
    @POST("eapi/w/login/cellphone")
    @Headers(
        "cookie: os=pc; deviceId=C2BDC8FD92695BA699BDC7C956411CC93655528ADE567B471163; osver=Microsoft-Windows-11-Home-China-build-26100-64bit; channel=netease; clientSign=18:3D:2D:2B:3E:C2@@@354344465F423830355F353041305F314646362E@@@@@@b1273282e030b475e4eade199c0df7f1f52a630fe8ca726441c26e62d232e255; mode=83NN; appver=3.1.23.204750; NMTID=00OVOVh-tLKvM3DZUSCmyRL3C0S14MAAAGXi4UwBA; WEVNSM=1.0.0; MUSIC_U=00EE3A756CF88ACD55E7D04EA21DB9EF20CF07F7031A1120ED289F1BAABEDF568963BA441940E6FAE22230E0ACCED413F375BFBE5F60FFD2C352EFC74909E58F8196115A416A4274D388CD87FCD0D7F68E261DAC071AE88EF7AEAF1699E2A813FF7BEEDDC85E32FB8812E5121503524DA83DC202D2409F10DA1426A96928B377141133B9A6FE379832349ACB179D7E67FED93C532766701FB5BF50EF1A6386DB9BEF4716853BF5930557A0D8EB2DAA58BD195FC515DB64137BA4130FE077726333CC7424AB2675BE488303EA5FFCE5F4534F39151287BF30021A53C9B2610CDE41670DAD4BD87B1FE31DCCADCD1E1BCF745B128157C768DBC95740F9299D778E88CF3CC64C1CF41CC031A0AC85D9CEAED002C1078BA40D146766EE3231F8B4B4FDD809EE1555DB1A3BF0C36855B6447D2F3C5DC85D5CB994925A62DDCD652A390C5B85BA22204555D213E84A42F993C15984DC0C0B13529ED6FFD0D65204F96BDAB4F815D20AD57E2FADC04FDF1E3A42A739914029C1B71A105AAF6AE450A465CA063111080109A3FAF331D8CE8DE3DA659F31B343CAE14C0BA279AB0142F65C0A90089F8C08848BE772483410041FE88746ACAEEC28FFF2EB18256911DC2880D1A1EE3F87305C44FDB465F63AFAEF209D343B345B0E3E13ECF4B11EF7F5A91E9DCBE3EE30C91E55BB70A0880B735AD64E; __csrf=4a9a0ac890edc56f0710fe5e28020d62; __remember_me=true; ntes_kaola_ad=1; WNMCID=jgnowz.1765093780855.01.0"
    )
    fun loginCaptcha(@Field("params") body: String): Call<String>

    @FormUrlEncoded
    @POST("eapi/album/v3/detail")
    fun getAlbum(
        @Field("params") body: String,
        @Header("Cookie") cookie: String
    ): Call<String>
}

