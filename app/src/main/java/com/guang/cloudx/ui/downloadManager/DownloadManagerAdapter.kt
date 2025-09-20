package com.guang.cloudx.ui.downloadManager

import com.guang.cloudx.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator

class InProgressAdapter(
    private val onRetry: (DownloadItemUi) -> Unit,
    private val onDeleteFailed: (DownloadItemUi) -> Unit
) : ListAdapter<DownloadItemUi, InProgressVH>(diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InProgressVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_in_progress, parent, false)
        return InProgressVH(v)
    }

    override fun onBindViewHolder(holder: InProgressVH, position: Int) {
        holder.bind(getItem(position), onRetry, onDeleteFailed)
    }

    companion object {
        private val diff = object : DiffUtil.ItemCallback<DownloadItemUi>() {
            override fun areItemsTheSame(o: DownloadItemUi, n: DownloadItemUi) = o.id == n.id
            override fun areContentsTheSame(o: DownloadItemUi, n: DownloadItemUi) = o == n
        }
    }
}

class InProgressVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val iv = itemView.findViewById<ShapeableImageView>(R.id.ivCover)
    private val title = itemView.findViewById<TextView>(R.id.tvTitle)
    private val artist = itemView.findViewById<TextView>(R.id.tvArtist)
    private val progress = itemView.findViewById<LinearProgressIndicator>(R.id.progress)
    private val tvError = itemView.findViewById<TextView>(R.id.tvError)
    private val btnDelete = itemView.findViewById<MaterialButton>(R.id.btnDelete)

    fun bind(item: DownloadItemUi, onRetry: (DownloadItemUi) -> Unit, onDeleteFailed: (DownloadItemUi) -> Unit) {
        Glide.with(itemView.context).load(item.coverUrl).into(iv)
        title.text = item.title
        artist.text = item.artist

        when (item.status) {
            TaskStatus.DOWNLOADING -> {
                progress.visibility = View.VISIBLE
                progress.setProgressCompat(item.progress, true)
                tvError.visibility = View.GONE
                btnDelete.visibility = View.GONE
                itemView.setOnClickListener(null) // 不可重试
            }
            TaskStatus.FAILED -> {
                progress.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                btnDelete.visibility = View.VISIBLE
                itemView.setOnClickListener { onRetry(item) }     // 点整卡片重试
                btnDelete.setOnClickListener { onDeleteFailed(item) } // 允许删除失败任务
            }
            else -> Unit
        }
    }
}

class CompletedAdapter(
    private val onDelete: (DownloadItemUi) -> Unit
) : ListAdapter<DownloadItemUi, CompletedVH>(diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_completed, parent, false)
        return CompletedVH(v)
    }

    override fun onBindViewHolder(holder: CompletedVH, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    companion object {
        private val diff = object : DiffUtil.ItemCallback<DownloadItemUi>() {
            override fun areItemsTheSame(o: DownloadItemUi, n: DownloadItemUi) = o.id == n.id
            override fun areContentsTheSame(o: DownloadItemUi, n: DownloadItemUi) = o == n
        }
    }
}

class CompletedVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val iv = itemView.findViewById<ShapeableImageView>(R.id.ivCover)
    private val title = itemView.findViewById<TextView>(R.id.tvTitle)
    private val artist = itemView.findViewById<TextView>(R.id.tvArtist)
    private val btnDelete = itemView.findViewById<MaterialButton>(R.id.btnDelete)

    fun bind(item: DownloadItemUi, onDelete: (DownloadItemUi) -> Unit) {
        Glide.with(itemView.context).load(item.coverUrl).into(iv)
        title.text = item.title
        artist.text = item.artist
        btnDelete.setOnClickListener { onDelete(item) }
    }
}
