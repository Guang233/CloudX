package com.guang.cloudx.ui.playList

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.utils.showSnackBar
import com.guang.cloudx.ui.home.MusicAdapter
import com.guang.cloudx.ui.home.MusicBottomSheet
import com.guang.cloudx.util.ext.d
import com.guang.cloudx.util.ext.e

// 此界面布局部分借助 AI 完成，因为我实在不会写（
class PlayListActivity : BaseActivity() {
    private lateinit var playList: PlayList
    private val musicList = mutableListOf<Music>()
    private lateinit var playListId: String
    private val viewModel by viewModels<PlayListViewModel>()

    private val rootLayout by lazy { findViewById<CoordinatorLayout>(R.id.rootLayout) }
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.playListRecyclerView) }
    private val swipeRefresh by lazy { findViewById<SwipeRefreshLayout>(R.id.swipeRefresh) }
    private val multiSelectToolbar by lazy { findViewById<MaterialToolbar>(R.id.selectToolbar) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val playListCoverView by lazy { findViewById<ShapeableImageView>(R.id.playListCoverImageView) }
    private val collapsingToolbar by lazy { findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar) }
    private val adapter by lazy { MusicAdapter(musicList,
        { music -> startDownloadMusic(music = music, view = recyclerView) },
        { music -> showBottomSheet(music = music) },
        { num ->
            multiSelectToolbar.title = "已选 $num 项"
            multiSelectToolbar.menu.findItem(R.id.action_download)?.isEnabled =
                !(num == 0 && multiSelectToolbar.menu.findItem(R.id.action_download)?.isEnabled == true)
        },
        { enterMultiSelectMode() }) }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        playListId = intent.getStringExtra("id")!!

        if (prefs.getCookie() == "") {
            MaterialAlertDialogBuilder(this)
                .setTitle("提示")
                .setMessage("此接口未登录只能获取前十首歌曲")
                .setPositiveButton("确定", null)
                .show()
        }

        val actionBarSizePx = TypedValue().let { tv ->
            theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }

        // Insets：只为 toolbars 设置高度 + top padding；**不要**为 RecyclerView 设置顶 padding（让 Coordinator 帮忙）
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarH = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val toolbarHeight = actionBarSizePx + statusBarH

            toolbar.updateLayoutParams { height = toolbarHeight }
            toolbar.updatePadding(top = statusBarH)

            multiSelectToolbar.updateLayoutParams { height = toolbarHeight }
            multiSelectToolbar.updatePadding(top = statusBarH)

            insets
        }

        swipeRefresh.isRefreshing = true
        viewModel.getPlayList(playListId, prefs.getCookie())

        initRecyclerView()

        if (viewModel.isMultiSelectionMode) {
            toolbar.visibility = View.GONE
            multiSelectToolbar.visibility = View.VISIBLE
            adapter.enterMultiSelectMode()
        }

        viewModel.playList.observe(this) { result ->
            swipeRefresh.isRefreshing = false

            val result = result.getOrNull()
            if (result != null) {
                playList = result
                musicList.clear()
                musicList.addAll(playList.musics)
                "playList.musics size = ${musicList.size}".d()
                initToolbar()
            } else {
                recyclerView.showSnackBar("获取失败")
            }

            adapter.notifyDataSetChanged()
        }

        swipeRefresh.setOnRefreshListener {
            if (adapter.itemCount == 0) swipeRefresh.isRefreshing = false
            else if (viewModel.isMultiSelectionMode) swipeRefresh.isRefreshing = false
            else {
                musicList.clear()
                viewModel.getPlayList(playListId, prefs.getCookie())
            }
        }

        // 折叠监听：控制 toolbar 背景 alpha、title 与 icon 颜色
        appBarLayout.addOnOffsetChangedListener { layout, verticalOffset ->
            swipeRefresh.isEnabled = verticalOffset == 0

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

            val total = layout.totalScrollRange
            val fraction = if (total != 0) kotlin.math.abs(verticalOffset).toFloat() / total else 0f
            // 计算一个背景透明度（0 -> transparent, 1 -> opaque）
            val surfaceColor = fetchColorAttr(android.R.attr.colorBackground) // fallback to the theme background
            val bg = ColorUtils.setAlphaComponent(surfaceColor, (fraction * 255).toInt())
            toolbar.setBackgroundColor(bg)
            multiSelectToolbar.setBackgroundColor(bg)

            // 文字/图标切换阈值
            val threshold = 0.7f
            if (fraction > threshold) {
                // 显示标题（深色，用主题 onSurface）
                if (!isDarkMode) controller.isAppearanceLightStatusBars = true
                collapsingToolbar.isTitleEnabled = false
                try { toolbar.title = playList.name } catch (e: Exception) { "歌单获取失败：$e".e() }
                toolbar.setTitleTextColor(fetchColorAttr(android.R.attr.textColorPrimary))
                multiSelectToolbar.setTitleTextColor(fetchColorAttr(android.R.attr.textColorSecondary))
                toolbar.navigationIcon?.setTint(fetchColorAttr(android.R.attr.textColorPrimary))
                multiSelectToolbar.navigationIcon?.setTint(fetchColorAttr(android.R.attr.textColorSecondary))
                multiSelectToolbar.menu.forEach { it.icon?.setTint(fetchColorAttr(android.R.attr.textColorPrimary)) }
            } else {
                controller.isAppearanceLightStatusBars = false
                toolbar.title = ""
                collapsingToolbar.isTitleEnabled = true
                toolbar.navigationIcon?.setTint(Color.WHITE)
                toolbar.setTitleTextColor(Color.WHITE)
                multiSelectToolbar.navigationIcon?.setTint(Color.WHITE)
                multiSelectToolbar.setTitleTextColor(Color.WHITE)
                multiSelectToolbar.menu.forEach { it.icon?.setTint(Color.WHITE) }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.isMultiSelectionMode) exitMultiSelectMode()
            else finish()
        }
    }

    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
    }

    private fun initToolbar() {
        toolbar.title = playList.name
        collapsingToolbar.title = playList.name
        collapsingToolbar.subtitle = "ID: ${playList.id}"
        Glide.with(this).load(playList.coverImgUrl).into(playListCoverView)

        toolbar.setNavigationOnClickListener { finish() }

        multiSelectToolbar.setNavigationOnClickListener { exitMultiSelectMode() }

        multiSelectToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_select_all -> {
                    adapter.selectAll()
                    true
                }
                R.id.action_invert_selection -> {
                    adapter.invertSelection()
                    true
                }
                R.id.action_download -> {
                    if (adapter.getSelectedMusic().isNotEmpty()) {
                        startDownloadMusic(musics = adapter.getSelectedMusic(), view = recyclerView)
                        exitMultiSelectMode()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showBottomSheet(music: Music) {
        val bottomSheet = MusicBottomSheet(music) { music, level ->
            startDownloadMusic(level= level, music = music, view = recyclerView) }
        bottomSheet.show(supportFragmentManager, "MusicBottomSheet")
    }

    // 这里就不用动画了，如果还使用渐入渐出会有bug
    private fun enterMultiSelectMode() {
        multiSelectToolbar.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        adapter.enterMultiSelectMode()
        viewModel.isMultiSelectionMode = true
    }

    private fun exitMultiSelectMode() {
        adapter.exitMultiSelectMode()
        toolbar.visibility = View.VISIBLE
        multiSelectToolbar.visibility = View.GONE
        viewModel.isMultiSelectionMode = false
    }

    private fun fetchColorAttr(attrRes: Int, defaultColor: Int = Color.WHITE): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(attrRes, tv, true)) {
            // 如果 attribute 指向了一个 color resource -> resourceId != 0
            if (tv.resourceId != 0) {
                // ContextCompat 更兼容，且会考虑 theme（API 兼容）
                ContextCompat.getColor(this, tv.resourceId)
            } else {
                // 否则直接用 tv.data（比如 #FF0000 这类内联色）
                tv.data
            }
        } else {
            defaultColor
        }
    }
}