package com.guang.cloudx.ui.downloadManager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.ui.ui.theme.CloudXTheme

class DownloadManagerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            CloudXTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DownloadManagerScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}
