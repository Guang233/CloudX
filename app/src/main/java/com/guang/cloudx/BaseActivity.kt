package com.guang.cloudx

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.applicationViewModels
import com.guang.cloudx.logic.utils.showSnackBar
import com.guang.cloudx.ui.downloadManager.DownloadViewModel

open class BaseActivity: AppCompatActivity() {
    protected lateinit var prefs: SharedPreferencesUtils
    var dir: DocumentFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = SharedPreferencesUtils(this)
    }

    protected fun applyTopInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            (v.layoutParams as ViewGroup.MarginLayoutParams).topMargin = statusBarHeight
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onResume() {
        super.onResume()
        updateStatusBarIconBySystemTheme()

        dir = prefs.getSafUri()?.let { DocumentFile.fromTreeUri(this, Uri.parse(it)) }
    }

    private fun updateStatusBarIconBySystemTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkMode
    }

    inline fun <reified T> startActivity(block: Intent.() -> Unit = {}) {
        val intent = Intent(this, T::class.java)
        intent.block()
        startActivity(intent)
    }



    protected fun startDownloadMusic(level: String = prefs.getMusicLevel(), musics: List<Music> = listOf(), music: Music? = null, view: View) {
        if (dir == null) {
            view.showSnackBar("未选择下载目录，请前往设置选择")
            return
        }

        val musicList = if (music != null) listOf(music)
        else musics

        val downloadViewModel: DownloadViewModel by applicationViewModels(application)

        downloadViewModel.startDownloads(
            this,
            musicList,
            level,
            prefs.getCookie(),
            dir!!,
            prefs.getIsSaveLrc())

        view.showSnackBar("已加入下载队列")
    }
}