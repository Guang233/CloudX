package com.guang.cloudx.logic.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesUtils(private val context: Context) {
    val sharedPreferences: SharedPreferences =  context.getSharedPreferences("settings", MODE_PRIVATE)
    fun getMusicLevel() = sharedPreferences.getString("music_level", "standard").toString()
    fun getCookie() =  sharedPreferences.getString("cookie", "").toString()

    fun putMusicLevel(musicLevel: String) = sharedPreferences.edit { putString("music_level", musicLevel) }
    fun putCookie(cookie: String) = sharedPreferences.edit { putString("cookie", cookie) }
}