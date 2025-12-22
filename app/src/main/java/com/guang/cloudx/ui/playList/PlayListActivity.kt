package com.guang.cloudx.ui.playList

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.ui.home.MusicPlayerViewModel
import com.guang.cloudx.ui.ui.theme.CloudXTheme

class PlayListActivity : BaseActivity() {
    private val viewModel by viewModels<PlayListViewModel>()
    private val playerViewModel by viewModels<MusicPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val type = intent.getStringExtra("type")!!
        val playListId = intent.getStringExtra("id")!!

        if (prefs.getCookie() == "" && type == "playlist") {
            MaterialAlertDialogBuilder(this)
                .setTitle("提示")
                .setMessage("此接口未登录只能获取前十首歌曲")
                .setPositiveButton("确定", null)
                .show()
        }

        setContent {
            CloudXTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PlayListScreen(
                        playListId = playListId,
                        type = type,
                        cookie = prefs.getCookie(),
                        defaultLevel = prefs.getMusicLevel(),
                        isPreviewEnabled = prefs.getIsPreviewMusic(),
                        isAutoLevel = prefs.getIsAutoLevel(),
                        onBackClick = { finish() },
                        onDownloadClick = { music, level ->
                            startDownloadMusic(level = level, music = music, view = window.decorView)
                        },
                        onMusicLongClick = { music ->
                            viewModel.enterMultiSelectMode()
                            viewModel.toggleSelection(music)
                        },
                        onDownloadSelected = { musics ->
                            startDownloadMusic(musics = musics, view = window.decorView)
                        },
                        onSaveLevel = { level ->
                            prefs.putMusicLevel(level)
                        },
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}