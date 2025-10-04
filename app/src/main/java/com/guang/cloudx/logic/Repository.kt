package com.guang.cloudx.logic

import androidx.lifecycle.liveData
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.network.MusicNetwork
import kotlinx.coroutines.Dispatchers

object Repository {
    fun searchMusic(keyword:String, offset: Int, limit: Int, cookie: String) = liveData(Dispatchers.IO) {
        try {
            val musicList = MusicNetwork.searchMusic(keyword, offset, limit, cookie)
            emit(Result.success(musicList))
        } catch (e: Exception) {
            emit(Result.failure<List<Music>>(e))
        }
    }

    fun getPlayList(id: String, cookie: String) = liveData(Dispatchers.IO) {
        try {
            val playList = MusicNetwork.getPlayList(id,  cookie)
            emit(Result.success(playList))
        } catch (e: Exception) {
            emit(Result.failure<PlayList>(e))
        }
    }

    fun getUserDetail(id: String, cookie: String) = liveData {
        try {
            val userDetail = MusicNetwork.getUserDetail(id, cookie)
            emit(Result.success(userDetail))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}