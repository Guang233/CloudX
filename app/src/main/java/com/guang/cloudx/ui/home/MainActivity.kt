package com.guang.cloudx.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.util.ext.d

class MainActivity : BaseActivity() {
    private val searchMusicList = mutableListOf<Music>()
    private val adapter by lazy { MusicAdapter(searchMusicList) }

    private val navButton by lazy { findViewById<MaterialButton>(R.id.navButton) }
    private val drawerLayout by lazy { findViewById<DrawerLayout>(R.id.drawer_layout) }
    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.mainRecyclerView) }

    private val searchInput by lazy { findViewById<EditText>(R.id.searchInput) }

    private val bottomSheet by lazy { findViewById<FrameLayout>(R.id.bottomSheet) }
    private val bsMusicName by lazy { findViewById<TextView>(R.id.musicNameDetail) }
    private val bsMusicAuthor by lazy { findViewById<TextView>(R.id.musicAuthorDetail) }
    private val bsMusicAlbum by lazy { findViewById<TextView>(R.id.musicAlbumDetail) }
    private val bsMusicIcon by lazy { findViewById<ShapeableImageView>(R.id.musicIconDetail) }
    private val bsDownloadButton by lazy { findViewById<MaterialButton>(R.id.btnDownload) }
    private val bsCancelButton by lazy { findViewById<MaterialButton>(R.id.btnCancelDownload) }
    private val swipeRefresh by lazy { findViewById<SwipeRefreshLayout>(R.id.swipeRefresh) }

    lateinit var viewModel: MainViewModel

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        swipeRefresh.setOnRefreshListener {
            if (adapter.itemCount == 0) swipeRefresh.isRefreshing = false
        }

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.close()
            else finish()
        }

        initRecyclerView()
        initEditText()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        searchInput.addTextChangedListener { editable ->
            viewModel.searchText = editable.toString()
            searchMusicList.clear()
        }
        searchInput.setText(viewModel.searchText)

        searchInput.setOnEditorActionListener { _, _, _ ->
            if (!TextUtils.isEmpty(searchInput.text)) {
                swipeRefresh.isRefreshing = true
                viewModel.searchMusic(searchInput.text.toString(), 0, 20)
            }
            true
        }

        viewModel.searchResults.observe(this) { result ->
            swipeRefresh.isRefreshing = false
            // 清空数据并通知Adapter

            val musicList = result.getOrNull()
            if (musicList != null) {
                searchMusicList.addAll(musicList)
                // adapter.notifyItemInserted(searchMusicList.size - musicList.size)
            } else {
                TODO("searchMusicList is null")
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun showBottomSheet(position: Int) {
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        bsCancelButton.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        bsDownloadButton.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            TODO("download")
        }
    }

    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (layoutManager.findFirstVisibleItemPosition() >= layoutManager.itemCount / 3 && !TextUtils.isEmpty(
                        searchInput.text
                    ) && !swipeRefresh.isRefreshing
                ) {
                    "musicList size = ${layoutManager.itemCount}".d()
                    swipeRefresh.isRefreshing = true
                    viewModel.searchMusic(
                        searchInput.text.toString(),
                        layoutManager.itemCount,
                        20
                    )

                }

            }
        })
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun initEditText() {
        // 初始隐藏清除图标
        searchInput.setCompoundDrawablesWithIntrinsicBounds(
            searchInput.compoundDrawables[0],
            null,
            null, // 右侧图标初始为 null
            null
        )

        // 文本变化监听器
        searchInput.doAfterTextChanged { s ->
            val clearIcon = if (s.isNullOrEmpty()) null
            else ContextCompat.getDrawable(this@MainActivity, R.drawable.close_24px)

            searchInput.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(this@MainActivity, R.drawable.search_24px),
                null,
                clearIcon,
                null
            )
        }

        // 清除图标点击事件
        searchInput.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawables = searchInput.compoundDrawables
                val clearDrawable = drawables[2] // 右侧drawable

                if (clearDrawable != null) {
                    // 计算点击位置是否在清除图标区域内
                    val touchX = event.x
                    val clearIconStart =
                        searchInput.width - searchInput.paddingEnd - clearDrawable.intrinsicWidth

                    if (touchX > clearIconStart) {
                        searchInput.text.clear()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
}