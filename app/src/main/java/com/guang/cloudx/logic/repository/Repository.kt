package com.guang.cloudx.logic.repository

import androidx.lifecycle.liveData
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.network.MusicNetwork
import com.guang.cloudx.util.ext.e
import kotlinx.coroutines.Dispatchers

object Repository {
    fun searchMusic(keyword:String, offset: Int, limit: Int, cookie: String) = liveData(Dispatchers.IO) {
        try {
            val musicList = MusicNetwork.searchMusic(keyword, offset, limit, cookie)
            emit(Result.success(musicList))
        } catch (e: Exception) {
            emit(Result.failure<List<Music>>(e))
            e.e()
        }
    }

    fun getPlayList(id: String, cookie: String) = liveData(Dispatchers.IO) {
        try {
            val playList = MusicNetwork.getPlayList(id, cookie)
            emit(Result.success(playList))
        } catch (e: Exception) {
            emit(Result.failure<PlayList>(e))
            e.e()
        }
    }

    fun getAlbum(id: String, cookie: String) = liveData(Dispatchers.IO) {
        try {
            val album = MusicNetwork.getAlbum(id, cookie)
            emit(Result.success(album))
        } catch (e: Exception) {
            emit(Result.failure(e))
            e.e()
        }
    }

    fun getUserDetail(id: String, cookie: String) = liveData {
        try {
            val userDetail = MusicNetwork.getUserDetail(id, cookie)
            emit(Result.success(userDetail))
        } catch (e: Exception) {
            emit(Result.failure(e))
            e.e()
        }
    }

    suspend fun sendCaptcha(phone: String) = MusicNetwork.sendCaptcha(phone)

    suspend fun getLoginData(phone: String, captcha: String) = MusicNetwork.getLoginData(phone, captcha)
}