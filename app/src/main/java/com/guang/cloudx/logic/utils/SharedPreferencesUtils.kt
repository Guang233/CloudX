package com.guang.cloudx.logic.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SharedPreferencesUtils(context: Context) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val COOKIE_KEY_ALIAS = "CloudX.Cookie"
        private const val COOKIE_KEY = "cookie"
        private const val ENCRYPTED_COOKIE_KEY = "cookie_encrypted"
    }

    val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", MODE_PRIVATE)
    fun getMusicLevel() = sharedPreferences.getString("music_level", "standard").toString()
    fun getIsAutoLevel() = sharedPreferences.getBoolean("auto_level", true)
    fun getCookie(): String {
        val encryptedCookie = sharedPreferences.getString(ENCRYPTED_COOKIE_KEY, null)
        if (!encryptedCookie.isNullOrBlank()) {
            return runCatching { decrypt(encryptedCookie) }
                .onFailure { sharedPreferences.edit { remove(ENCRYPTED_COOKIE_KEY) } }
                .getOrDefault("")
        }

        val legacyCookie = sharedPreferences.getString(COOKIE_KEY, "").orEmpty()
        if (legacyCookie.isNotEmpty()) putCookie(legacyCookie)
        return legacyCookie
    }
    fun getIsSaveLrc() = sharedPreferences.getBoolean("is_save_lrc", false)
    fun getIsSaveTlLrc() = sharedPreferences.getBoolean("is_save_translate_lrc", true)
    fun getIsSaveRomaLrc() = sharedPreferences.getBoolean("is_save_roma_lrc", false)
    fun getIsSaveYrc() = sharedPreferences.getBoolean("is_save_yrc", false)
    fun getUserId() = sharedPreferences.getString("user_id", "").toString()
    fun getSafUri() = sharedPreferences.getString("saf_uri", null).run { this }
    fun getDownloadFileName() = sharedPreferences.getString("download_file_name", $$"${level}${name}-${artists}")
    fun getArtistsDelimiter() = sharedPreferences.getString("artists_delimiter", "、")
    fun getLrcEncoding() = sharedPreferences.getString("lrc_encoding", "UTF-8")
    fun getIsPreviewMusic() = sharedPreferences.getBoolean("is_preview_music", false)
    fun getConcurrentDownloads() = sharedPreferences.getInt("concurrent_downloads", 4).coerceIn(1, 8)
    fun getIsConvertM4aToMp3() = sharedPreferences.getBoolean("is_convert_m4a_to_mp3", false)
    fun getFileConflictStrategy() = sharedPreferences.getString("file_conflict_strategy", "覆盖").toString()
    fun getThemeColor() = sharedPreferences.getString("theme_color", "跟随系统").toString()
    fun getDarkMode() = sharedPreferences.getString("dark_mode", "跟随系统").toString()
    fun getIsFirstLaunch() = sharedPreferences.getBoolean("is_first_launch", true)

    fun putMusicLevel(musicLevel: String) = sharedPreferences.edit { putString("music_level", musicLevel) }
    fun putIsAutoLevel(value: Boolean) = sharedPreferences.edit { putBoolean("auto_level", value) }
    fun putCookie(cookie: String) {
        runCatching { encrypt(cookie) }
            .onSuccess { encrypted ->
                sharedPreferences.edit {
                    putString(ENCRYPTED_COOKIE_KEY, encrypted)
                    remove(COOKIE_KEY)
                }
            }
            .onFailure {
                sharedPreferences.edit { putString(COOKIE_KEY, cookie) }
            }
    }
    fun putIsSaveLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_lrc", value) }
    fun putIsSaveTlLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_translate_lrc", value) }
    fun putIsSaveRomaLrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_roma_lrc", value) }
    fun putIsSaveYrc(value: Boolean) = sharedPreferences.edit { putBoolean("is_save_yrc", value) }
    fun putUserId(userId: String) = sharedPreferences.edit { putString("user_id", userId) }
    fun putSafUri(value: String) = sharedPreferences.edit { putString("saf_uri", value) }
    fun putDownloadFileName(value: String) = sharedPreferences.edit { putString("download_file_name", value) }
    fun putArtistsDelimiter(value: String) = sharedPreferences.edit { putString("artists_delimiter", value) }
    fun putLrcEncoding(value: String) = sharedPreferences.edit { putString("lrc_encoding", value) }
    fun putIsPreviewMusic(value: Boolean) = sharedPreferences.edit { putBoolean("is_preview_music", value) }
    fun putConcurrentDownloads(value: Int) = sharedPreferences.edit { putInt("concurrent_downloads", value.coerceIn(1, 8)) }
    fun putIsConvertM4aToMp3(value: Boolean) = sharedPreferences.edit { putBoolean("is_convert_m4a_to_mp3", value) }
    fun putFileConflictStrategy(value: String) = sharedPreferences.edit { putString("file_conflict_strategy", value) }
    fun putThemeColor(value: String) = sharedPreferences.edit { putString("theme_color", value) }
    fun putDarkMode(value: String) = sharedPreferences.edit { putString("dark_mode", value) }
    fun putIsFirstLaunch(value: Boolean) = sharedPreferences.edit { putBoolean("is_first_launch", value) }

    private fun getOrCreateCookieKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(COOKIE_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    COOKIE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateCookieKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$data"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted cookie" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateCookieKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT))
        )
        return String(
            cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT)),
            StandardCharsets.UTF_8
        )
    }
}
