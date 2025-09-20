package com.guang.cloudx.logic.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences

class SharedPreferencesUtils(private val context: Context) {
    val sharedPreferences: SharedPreferences =  context.getSharedPreferences("settings", MODE_PRIVATE)
    fun getMusicLevel() = sharedPreferences.getString("music_level", "standard").toString()
    fun getCookie() =  sharedPreferences.getString("cookie", "").toString()
}