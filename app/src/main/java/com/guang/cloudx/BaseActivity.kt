package com.guang.cloudx

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.applicationViewModels
import com.guang.cloudx.ui.downloadManager.DownloadViewModel

open class BaseActivity : ComponentActivity() {
    protected lateinit var prefs: SharedPreferencesUtils
    var dir: DocumentFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = SharedPreferencesUtils(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatusBarIconBySystemTheme()

        dir = prefs.getSafUri()?.let { DocumentFile.fromTreeUri(this, it.toUri()) }
    }

    private fun updateStatusBarIconBySystemTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkMode
    }

    // 新的 Compose 友好方法
    fun startDownloadMusic(
        level: String = prefs.getMusicLevel(),
        musics: List<Music> = listOf(),
        music: Music? = null,
        onShowSnackbar: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onShowSnackbar("请给予通知权限(用于后台下载)")
                return
            }
        }

        if (dir == null) {
            onShowSnackbar("未选择下载目录，请前往设置选择")
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
            MusicDownloadRules(
                prefs.getIsSaveLrc(),
                prefs.getIsSaveTlLrc(),
                prefs.getIsSaveRomaLrc(),
                prefs.getIsSaveYrc(),
                prefs.getDownloadFileName()!!,
                prefs.getArtistsDelimiter()!!,
                prefs.getLrcEncoding()!!
            )
        )

        onShowSnackbar("已加入下载队列")
    }
}