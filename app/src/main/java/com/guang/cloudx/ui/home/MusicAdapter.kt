package com.guang.cloudx.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.imageview.ShapeableImageView
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.Music

class MusicAdapter(
    val musicList: List<Music>,
    private val onDownloadClick: (Music) -> Unit,
    private val onItemClick: (Music) -> Unit,
    private val onSelect: (Int) -> Unit,
    private val onItemLongClick: () -> Unit
) : RecyclerView.Adapter<MusicAdapter.ViewHolder>() {
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val musicIcon: ShapeableImageView = view.findViewById(R.id.musicIcon)
        val musicName: TextView = view.findViewById(R.id.musicName)
        val musicAuthor: TextView = view.findViewById(R.id.musicAuthor)
        val download: MaterialButton = view.findViewById(R.id.downloadMusicIcon)
        val cardView: CardView = view.findViewById(R.id.musicCardView)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.musicCheckBox)
    }

    var multiSelectMode = false
        private set
    private val selectedItems = mutableSetOf<Music>()

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ViewHolder {
        val view = LayoutInflater.from(p0.context)
            .inflate(R.layout.music_item_layout, p0, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val music = musicList[position]

        holder.checkBox.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
        holder.download.visibility = if (multiSelectMode) View.GONE else View.VISIBLE
        holder.checkBox.isChecked = selectedItems.contains(music)

        Glide.with(holder.itemView.context).load(music.album.picUrl).into(holder.musicIcon)
        holder.musicName.text = music.name
        val authors = music.artists.joinToString("/") { it.name }
        val album = music.album.name
        holder.musicAuthor.text =
            if ("" != album) "$authors - $album"
            else authors
        holder.download.setOnClickListener {
            onDownloadClick(music)
        }
        holder.cardView.setOnClickListener {
            if (multiSelectMode) {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
                toggleSelection(music)
                onSelect(selectedItems.size)
            } else onItemClick(music)
        }
        holder.cardView.setOnLongClickListener {
            if (!multiSelectMode) {
                onItemLongClick()
                holder.download.visibility = View.GONE
                holder.checkBox.visibility = View.VISIBLE
                holder.checkBox.isChecked = true
                toggleSelection(music)
                onSelect(selectedItems.size)
                true
            } else
                false
        }
        holder.checkBox.setOnClickListener {
            toggleSelection(music)
            onSelect(selectedItems.size)
        }
    }

    override fun getItemCount(): Int = musicList.size

    private fun toggleSelection(music: Music) {
        if (selectedItems.contains(music)) {
            selectedItems.remove(music)
        } else {
            selectedItems.add(music)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun enterMultiSelectMode() {
        multiSelectMode = true
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun exitMultiSelectMode() {
        multiSelectMode = false
        selectedItems.clear()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(musicList)
        notifyDataSetChanged()
        onSelect(selectedItems.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelection() {
        val newSelection = mutableSetOf<Music>()
        musicList.forEach { music ->
            if (!selectedItems.contains(music)) {
                newSelection.add(music)
            }
        }
        selectedItems.clear()
        selectedItems.addAll(newSelection)
        notifyDataSetChanged()
        onSelect(selectedItems.size)
    }

    fun getSelectedMusic() = selectedItems.toList()
}