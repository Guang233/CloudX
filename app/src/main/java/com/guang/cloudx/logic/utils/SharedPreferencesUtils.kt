package com.guang.cloudx.logic.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesUtils(context: Context) {
    val sharedPreferences: SharedPreferences =  context.getSharedPreferences("settings", MODE_PRIVATE)
    fun getMusicLevel() = sharedPreferences.getString("music_level", "standard").toString()
    fun getIsAutoLevel() = sharedPreferences.getBoolean("auto_level", false)
    fun getCookie() =  sharedPreferences.getString("cookie", "").toString()
    fun getIsSaveLrc() = sharedPreferences.getBoolean("is_save_lrc", false)
    fun getUserId() = sharedPreferences.getString("user_id", "").toString()
    fun getSafUri() = sharedPreferences.getString("saf_uri", null).run { this }

    fun putMusicLevel(musicLevel: String) = sharedPreferences.edit { putString("music_level", musicLevel) }
    fun putIsAutoLevel(value: Boolean) = sharedPreferences.edit { putBoolean("auto_level", value) }
    fun putCookie(cookie: String) = sharedPreferences.edit { putString("cookie", cookie) }
    fun putIsSaveLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_lrc", value) }
    fun putUserId(userId: String) = sharedPreferences.edit { putString("user_id", userId) }
    fun putSafUri(value: String) = sharedPreferences.edit { putString("saf_uri", value) }
}