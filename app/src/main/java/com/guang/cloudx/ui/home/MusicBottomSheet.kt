package com.guang.cloudx.ui.home

import AudioPlayer
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.slider.Slider
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.SystemUtils
import com.guang.cloudx.logic.utils.showSnackBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MusicBottomSheet(
    private val music: Music,
    private val onDownload: (Music, String) -> Unit
) : BottomSheetDialogFragment() {
    private val viewModel: MusicViewModel by viewModels()
    private lateinit var pauseButton: MaterialButton
    private lateinit var playButton: MaterialButton
    private lateinit var progress: CircularProgressIndicator
    private lateinit var progressSlider: Slider

    private var musicFile: File? = null
    private var player: AudioPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_layout, container, false)

        val buttonGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.buttonGroupMusicLevel)
        val bsMusicName = view.findViewById<TextView>(R.id.musicNameDetail)
        val bsMusicAuthor = view.findViewById<TextView>(R.id.musicAuthorDetail)
        val bsMusicAlbum = view.findViewById<TextView>(R.id.musicAlbumDetail)
        val bsMusicIcon = view.findViewById<ShapeableImageView>(R.id.musicIconDetail)
        val bsDownloadButton = view.findViewById<MaterialButton>(R.id.btnDownload)
        val bsCancelButton = view.findViewById<MaterialButton>(R.id.btnCancelDownload)

        if (SharedPreferencesUtils(requireContext()).getIsPreviewMusic()) {
            pauseButton = view.findViewById(R.id.pauseButton)
            playButton = view.findViewById(R.id.playButton)
            progress = view.findViewById(R.id.circularProgress)
            progressSlider = view.findViewById(R.id.progressSlider)
            initPlayer()
        } else
            view.findViewById<LinearLayout>(R.id.playerLayout).visibility = View.GONE

        bsMusicName.text = music.name
        Glide.with(this).load(music.album.picUrl).into(bsMusicIcon)
        bsMusicAlbum.text =
            if ("" != music.album.name) music.album.name
            else "无"
        val artists = music.artists.joinToString("/") { it.name }
        bsMusicAuthor.text = artists

        when (SharedPreferencesUtils(requireContext()).getMusicLevel()) {
            "standard" -> buttonGroup.check(R.id.btnStandardQuality)
            "exhigh" -> buttonGroup.check(R.id.btnHQ)
            "lossless" -> buttonGroup.check(R.id.btnSQ)
            "hires" -> buttonGroup.check(R.id.btnHiRes)
            else -> buttonGroup.check(R.id.btnStandardQuality)
        }

        bsMusicName.apply {
            isSelected = true
            setOnLongClickListener {
                SystemUtils.copyToClipboard(requireContext(), "musicName", music.name)
                true
            }
        }
        bsMusicAuthor.apply {
            isSelected = true
            setOnLongClickListener {
                SystemUtils.copyToClipboard(requireContext(), "musicArtists", artists)
                true
            }
        }
        bsMusicAlbum.apply {
            isSelected = true
            setOnLongClickListener {
                SystemUtils.copyToClipboard(requireContext(), "musicAlbum", music.album.name)
                true
            }
        }
        bsMusicIcon.setOnClickListener {
            val shapeableImageView = ShapeableImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val container = FrameLayout(requireContext()).apply {
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, 0)
                addView(shapeableImageView)
            }
            Glide.with(this).load(music.album.picUrl).into(shapeableImageView)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("封面查看")
                .setView(container)
                .setPositiveButton("保存") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val file = Glide.with(requireContext())
                            .asFile()
                            .load(music.album.picUrl)
                            .submit()
                            .get()

                        saveImageWithDocumentFile(
                            file,
                            music.name.replace(Regex("[\\\\/:*?\"<>|]"), " "),
                            requireContext() as BaseActivity
                        ) {
                            bsMusicIcon.showSnackBar("已保存封面至下载目录")
                        }

                    }
                }
                .setNegativeButton("关闭", null)
                .show()
        }

        bsCancelButton.setOnClickListener {
            dismiss()
        }
        bsDownloadButton.setOnClickListener {
            dismiss()
            val level = when (buttonGroup.checkedButtonId) {
                R.id.btnStandardQuality -> "standard"
                R.id.btnHQ -> "exhigh"
                R.id.btnSQ -> "lossless"
                R.id.btnHiRes -> "hires"
                else -> "standard"
            }
            if (SharedPreferencesUtils(requireContext()).getIsAutoLevel())
                SharedPreferencesUtils(requireContext()).putMusicLevel(level)
            onDownload(music, level)
        }
        return view
    }

    @SuppressLint("DefaultLocale")
    private fun initPlayer() {
        progressSlider.setLabelFormatter { value ->
            val totalSeconds = value.toInt()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            if (hours > 0)
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            else
                String.format("%02d:%02d", minutes, seconds)
        }

        progressSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player?.seekTo((value * 1000).toInt())
            }
        }

        playButton.setOnClickListener {
            if (musicFile == null) {
                viewModel.cacheMusic(music, requireContext().externalCacheDir!!)
                playButton.visibility = View.GONE
                progress.visibility = View.VISIBLE
            } else {
                player?.let {
                    it.play()
                    playButton.visibility = View.GONE
                    progress.visibility = View.GONE
                    pauseButton.visibility = View.VISIBLE
                }
            }
        }

        pauseButton.setOnClickListener {
            player?.let {
                it.pause()
                pauseButton.visibility = View.GONE
                playButton.visibility = View.VISIBLE
            }
        }

        viewModel.musicFile.observe(viewLifecycleOwner) { file ->
            if (file != null) {
                musicFile = file
                player?.release()
                player = AudioPlayer(file).apply {
                    prepare()
                }
                progress.visibility = View.GONE
                pauseButton.visibility = View.VISIBLE
                playButton.visibility = View.GONE

                player?.setOnProgressUpdateListener { currentMs, totalMs ->
                    progressSlider.value = currentMs / 1000f
                    progressSlider.valueFrom = 0f
                    progressSlider.valueTo = totalMs / 1000f
                }
                player?.setOnCompletionListener {
                    pauseButton.visibility = View.GONE
                    playButton.visibility = View.VISIBLE
                    progressSlider.value = 0f
                }
            } else {
                playButton.visibility = View.VISIBLE
                progress.visibility = View.GONE
                pauseButton.showSnackBar("缓存音乐失败")
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun saveImageWithDocumentFile(
        sourceFile: File,
        fileName: String,
        context: BaseActivity,
        ifSucceed: () -> Unit
    ) {
        val pickedDir = context.dir ?: return

        val newFile = pickedDir.createFile("image/jpeg", fileName) ?: return

        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        ifSucceed()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        player?.release()
    }
}