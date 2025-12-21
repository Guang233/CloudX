package com.guang.cloudx.logic.utils

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

class AudioPlayer(private val file: File) {

    private var mediaPlayer: MediaPlayer? = null
    private var handler: Handler? = null
    private var updateListener: ((currentMs: Int, totalMs: Int) -> Unit)? = null
    private var onCompletion: (() -> Unit)? = null

    // 初始化
    fun prepare(onPrepared: (() -> Unit)? = null, onError: ((Exception) -> Unit)? = null) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepareAsync()
                setOnPreparedListener {
                    onPrepared?.invoke()
                    start()
                    startUpdatingProgress()
                }
                setOnErrorListener { _, what, extra ->
                    onError?.invoke(Exception("MediaPlayer error: $what, $extra"))
                    true
                }
                setOnCompletionListener {
                    onCompletion?.invoke()
                }
            }
        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }

    fun play() {
        mediaPlayer?.start()
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletion = listener
    }

    // 拖动（单位：毫秒）
    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    // 当前播放时间（毫秒）
    fun currentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    // 音频总时长（毫秒）
    fun duration(): Int = mediaPlayer?.duration ?: 0

    fun setOnProgressUpdateListener(listener: (currentMs: Int, totalMs: Int) -> Unit) {
        updateListener = listener
    }

    private fun startUpdatingProgress() {
        handler = Handler(Looper.getMainLooper())
        handler?.post(object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    updateListener?.invoke(mp.currentPosition, mp.duration)
                }
                handler?.postDelayed(this, 500L)
            }
        })
    }

    fun release() {
        handler?.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
