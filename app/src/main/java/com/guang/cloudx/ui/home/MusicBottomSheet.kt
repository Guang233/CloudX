package com.guang.cloudx.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
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
    ): BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view =  inflater.inflate(R.layout.bottom_sheet_layout, container, false)

        val buttonGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.buttonGroupMusicLevel)
        val bsMusicName = view.findViewById<TextView>(R.id.musicNameDetail)
        val bsMusicAuthor = view.findViewById<TextView>(R.id.musicAuthorDetail)
        val bsMusicAlbum = view.findViewById<TextView>(R.id.musicAlbumDetail)
        val bsMusicIcon = view.findViewById<ShapeableImageView>(R.id.musicIconDetail)
        val bsDownloadButton = view.findViewById<MaterialButton>(R.id.btnDownload)
        val bsCancelButton = view.findViewById<MaterialButton>(R.id.btnCancelDownload)

        bsMusicName.text = music.name
        Glide.with(this).load(music.album.picUrl).into(bsMusicIcon)
        bsMusicAlbum.text =
            if("" != music.album.name) music.album.name
            else "无"
        val artists = music.artists.joinToString("/") { it.name }
        bsMusicAuthor.text = artists

        when(SharedPreferencesUtils(requireContext()).getMusicLevel()) {
            "standard" -> buttonGroup.check(R.id.btnStandardQuality)
            "exhigh" -> buttonGroup.check(R.id.btnHQ)
            "lossless" -> buttonGroup.check(R.id.btnSQ)
            "hires" -> buttonGroup.check(R.id.btnHiRes)
            else -> buttonGroup.check(R.id.btnStandardQuality)
        }

        bsMusicName.apply {
            isSelected = true
            setOnClickListener { SystemUtils.copyToClipboard(requireContext(), "musicName", music.name) }
        }
        bsMusicAuthor.apply {
            isSelected = true
            setOnClickListener { SystemUtils.copyToClipboard(requireContext(), "musicArtists", artists) }
        }
        bsMusicAlbum.apply {
            isSelected = true
            setOnClickListener { SystemUtils.copyToClipboard(requireContext(), "musicAlbum", music.album.name) }
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

                        saveImageWithDocumentFile(file, music.name.replace(Regex("[\\\\/:*?\"<>|]"), " "), requireContext() as BaseActivity) {
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

    @SuppressLint("SuspiciousIndentation")
    private fun saveImageWithDocumentFile(sourceFile: File, fileName: String, context: BaseActivity, ifSucceed: () -> Unit) {
        val pickedDir = context.dir ?: return

        val newFile = pickedDir.createFile("image/jpeg", fileName) ?: return

            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        ifSucceed()
    }


}