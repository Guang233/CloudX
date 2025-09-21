package com.guang.cloudx.ui.home

import com.guang.cloudx.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.imageview.ShapeableImageView
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.SystemUtils

class MusicBottomSheet(
    private val music: Music,
    private val onDownload: (Music, String) -> Unit
    ): BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view =  inflater.inflate(R.layout.bottom_sheet_layout, container, false)

        val buttonGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.buttonGroupMusicLevel)
        val bsMusicName =  view.findViewById<TextView>(R.id.musicNameDetail)
        val bsMusicAuthor =  view.findViewById<TextView>(R.id.musicAuthorDetail)
        val bsMusicAlbum =  view.findViewById<TextView>(R.id.musicAlbumDetail)
        val bsMusicIcon =  view.findViewById<ShapeableImageView>(R.id.musicIconDetail)
        val bsDownloadButton =  view.findViewById<MaterialButton>(R.id.btnDownload)
        val bsCancelButton =  view.findViewById<MaterialButton>(R.id.btnCancelDownload)

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

        bsMusicName.setOnClickListener { SystemUtils.copyToClipboard(this.requireContext(), "musicName", music.name) }
        bsMusicAuthor.setOnClickListener { SystemUtils.copyToClipboard(this.requireContext(), "musicArtists", artists) }
        bsMusicAlbum.setOnClickListener { SystemUtils.copyToClipboard(this.requireContext(), "musicAlbum", music.album.name) }

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
            onDownload(music, level)
        }
        return view
    }

}