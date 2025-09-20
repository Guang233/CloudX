package com.guang.cloudx.ui.home

import android.view.LayoutInflater
import com.guang.cloudx.R
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.guang.cloudx.logic.model.Music

class MusicAdapter(val musicList: List<Music>,
                   private val onDownloadClick: (Music) -> Unit,
                   private val onItemClick: (Music) -> Unit): RecyclerView.Adapter<MusicAdapter.ViewHolder>() {
    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val musicIcon: ShapeableImageView = view.findViewById(R.id.musicIcon)
        val musicName: TextView = view.findViewById(R.id.musicName)
        val musicAuthor: TextView = view.findViewById(R.id.musicAuthor)
        val download: MaterialButton = view.findViewById(R.id.downloadMusicIcon)
        val cardView: CardView = view.findViewById(R.id.musicCardView)
    }

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ViewHolder {
        val view = LayoutInflater.from(p0.context)
            .inflate(R.layout.music_item_layout, p0, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val music = musicList[position]
        Glide.with(holder.itemView.context).load(music.album.picUrl).into(holder.musicIcon)
        holder.musicName.text = music.name
        val authors = music.artists.joinToString("/") { it.name }
        val album =  music.album.name
        holder.musicAuthor.text =
            if ("" != album) "$authors - $album"
            else authors
        holder.download.setOnClickListener {
            onDownloadClick(music)
        }
        holder.cardView.setOnClickListener {
            onItemClick(music)
        }
    }

    override fun getItemCount(): Int = musicList.size
}