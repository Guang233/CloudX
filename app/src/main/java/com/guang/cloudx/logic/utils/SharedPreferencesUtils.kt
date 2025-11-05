package com.guang.cloudx.logic.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesUtils(context: Context) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", MODE_PRIVATE)
    fun getMusicLevel() = sharedPreferences.getString("music_level", "standard").toString()
    fun getIsAutoLevel() = sharedPreferences.getBoolean("auto_level", true)
    fun getCookie() = sharedPreferences.getString("cookie", "").toString()
    fun getIsSaveLrc() = sharedPreferences.getBoolean("is_save_lrc", false)
    fun getIsSaveTlLrc() = sharedPreferences.getBoolean("is_save_translate_lrc", true)
    fun getIsSaveRomaLrc() = sharedPreferences.getBoolean("is_save_roma_lrc", false)
    fun getIsSaveYrc() = sharedPreferences.getBoolean("is_save_yrc", false)
    fun getUserId() = sharedPreferences.getString("user_id", "").toString()
    fun getSafUri() = sharedPreferences.getString("saf_uri", null).run { this }
    fun getCompletedMusic() = sharedPreferences.getString("completed_music", "")
    fun getDownloadFileName() = sharedPreferences.getString("download_file_name", $$"${level}${name}-${artists}")
    fun getArtistsDelimiter() = sharedPreferences.getString("artists_delimiter", "、")
    fun getLrcEncoding() = sharedPreferences.getString("lrc_encoding", "UTF-8")
    fun getIsPreviewMusic() = sharedPreferences.getBoolean("is_preview_music", false)

    fun putMusicLevel(musicLevel: String) = sharedPreferences.edit { putString("music_level", musicLevel) }
    fun putIsAutoLevel(value: Boolean) = sharedPreferences.edit { putBoolean("auto_level", value) }
    fun putCookie(cookie: String) = sharedPreferences.edit { putString("cookie", cookie) }
    fun putIsSaveLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_lrc", value) }
    fun putIsSaveTlLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_translate_lrc", value) }
    fun putIsSaveRomaLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_roma_lrc", value) }
    fun putIsSaveYrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_yrc", value) }
    fun putUserId(userId: String) = sharedPreferences.edit { putString("user_id", userId) }
    fun putSafUri(value: String) = sharedPreferences.edit { putString("saf_uri", value) }
    fun putCompletedMusic(value: String) = sharedPreferences.edit { putString("completed_music", value) }
    fun putDownloadFileName(value: String) = sharedPreferences.edit { putString("download_file_name", value) }
    fun putArtistsDelimiter(value: String) = sharedPreferences.edit { putString("artists_delimiter", value) }
    fun putLrcEncoding(value: String) = sharedPreferences.edit { putString("lrc_encoding", value) }
    fun putIsPreviewMusic(value: Boolean) = sharedPreferences.edit { putBoolean("is_preview_music", value) }
}