package com.guang.cloudx.logic

import androidx.lifecycle.liveData
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.network.MusicNetwork
import kotlinx.coroutines.Dispatchers

object Repository {
    fun searchMusic(keyword:String, offset: Int, limit: Int) = liveData(Dispatchers.IO) {
        try {
            val musicList = MusicNetwork.searchMusic(keyword, offset, limit, "")
            emit(Result.success(musicList))
        } catch (e: Exception) {
            emit(Result.failure<List<Music>>(e))
        }
    }
}