package com.guang.cloudx.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.utils.showSnackBar
import com.guang.cloudx.ui.downloadManager.DownloadManagerActivity
import com.guang.cloudx.ui.playList.PlayListActivity
import com.guang.cloudx.util.ext.d

class MainActivity : BaseActivity() {
    private val searchMusicList = mutableListOf<Music>()
    private var isLastPage = false
    private var lastSearchText: String = ""
    private val adapter by lazy { MusicAdapter(searchMusicList,
        { music -> startDownloadMusic(music = music, view = recyclerView) },
        { music -> showBottomSheet(music = music) },
        { num ->
            multiSelectToolbar.title = "已选 $num 项"
            multiSelectToolbar.menu.findItem(R.id.action_download)?.isEnabled =
                !(num == 0 && multiSelectToolbar.menu.findItem(R.id.action_download)?.isEnabled == true)
            },
        { enterMultiSelectMode() }) }

    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.topAppBar) }
    private val drawerLayout by lazy { findViewById<DrawerLayout>(R.id.drawer_layout) }
    private val navigationView by lazy { findViewById<NavigationView>(R.id.navigation_view) }
    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.mainRecyclerView) }

    private val searchToolbar by lazy { findViewById<MaterialToolbar>(R.id.searchToolbar) }
    private val searchEditText by lazy { findViewById<EditText>(R.id.searchEditText) }

    private val multiSelectToolbar by lazy { findViewById<MaterialToolbar>(R.id.selectToolbar) }

    private val swipeRefresh by lazy { findViewById<SwipeRefreshLayout>(R.id.swipeRefresh) }

    private val viewModel: MainViewModel by viewModels()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyTopInsets(appBarLayout)
        applyTopInsets(navigationView)

        swipeRefresh.setOnRefreshListener {
            if (adapter.itemCount == 0) swipeRefresh.isRefreshing = false
            if (viewModel.isMultiSelectionMode) swipeRefresh.isRefreshing = false
            else if (!TextUtils.isEmpty(viewModel.searchText)) {
                searchMusicList.clear()
                isLastPage = false
                viewModel.searchMusic(viewModel.searchText, 0, 20, prefs.getCookie())
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.close()
            else if (viewModel.isMultiSelectionMode) exitMultiSelectMode()
            else if (viewModel.isSearchMode) exitSearchMode()
                else moveTaskToBack(true)
        }

        setupToolbar()
        initRecyclerView()
        initNavigationView()
        setupSearchToolbar()

        if (viewModel.isMultiSelectionMode) {
            toolbar.visibility = View.GONE
            searchToolbar.visibility = View.GONE
            multiSelectToolbar.visibility = View.VISIBLE
            adapter.enterMultiSelectMode()
        } else if (viewModel.isSearchMode) {
            toolbar.visibility = View.GONE
            searchToolbar.visibility = View.VISIBLE
        }

        viewModel.searchResults.observe(this) { result ->
            swipeRefresh.isRefreshing = false

                val musicList = result.getOrNull()
                if (musicList != null) {
                    searchMusicList.addAll(musicList)
                    // adapter.notifyItemInserted(searchMusicList.size - musicList.size)
                } else {
                    if (searchMusicList.isEmpty()) {
                        searchMusicList.clear()
                        recyclerView.showSnackBar("未搜到")
                    } else {
                        recyclerView.showSnackBar("没有更多了")
                        isLastPage = true
                    }
                }
                adapter.notifyDataSetChanged()
        }
    }

    private fun showBottomSheet(music: Music) {
        val bottomSheet = MusicBottomSheet(music) { music, level ->
            startDownloadMusic(level= level, music = music, view = recyclerView) }
        bottomSheet.show(supportFragmentManager, "MusicBottomSheet")

    }

    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!isLastPage
                    && layoutManager.findFirstVisibleItemPosition() >= layoutManager.itemCount / 3 && !TextUtils.isEmpty(
                        searchEditText.text
                    ) && !swipeRefresh.isRefreshing
                    && !viewModel.isMultiSelectionMode
                ) {
                    "musicList size = ${layoutManager.itemCount}".d()
                    swipeRefresh.isRefreshing = true
                    viewModel.searchMusic(
                        searchEditText.text.toString(),
                        layoutManager.itemCount,
                        20,
                        prefs.getCookie()
                    )

                }

            }
        })
    }

    private fun initNavigationView(){
        navigationView.setNavigationItemSelectedListener { menuItem ->
             when(menuItem.itemId) {
                R.id.nav_download_manager -> {
                    startActivity<DownloadManagerActivity>{}
                    true
                }
                 R.id.nav_add_playlist -> {
                     val view = layoutInflater.inflate(R.layout.dialog_playlist, null)
                     MaterialAlertDialogBuilder(this)
                         .setTitle("解析歌单")
                         .setView(view)
                         .setNegativeButton("取消", null)
                         .setPositiveButton("确定") { _, _ ->
                             val editText = view.findViewById<EditText>(R.id.playListIdEditText)
                             if(!editText.text.isEmpty())
                                startActivity<PlayListActivity> { putExtra("id", editText.text.toString()) }
                         }
                         .show()
                     true
                 }
                else -> false
            }
        }
    }

    private fun setupToolbar()  {
        setSupportActionBar(toolbar)

        // 导航图标点击事件（打开侧边栏）
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        multiSelectToolbar.setNavigationOnClickListener {
            exitMultiSelectMode()
        }

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

    @SuppressLint("NotifyDataSetChanged")
    private fun setupSearchToolbar() {

        searchEditText.setText(viewModel.inputText)
        // 搜索Toolbar的返回按钮
        searchToolbar.setNavigationOnClickListener {
            exitSearchMode()
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (!TextUtils.isEmpty(searchEditText.text)) {
                    if (lastSearchText != searchEditText.text.toString()) {
                        viewModel.searchText = searchEditText.text.toString()
                        swipeRefresh.isRefreshing = true
                        searchMusicList.clear()
                        isLastPage = false
                        viewModel.searchMusic(searchEditText.text.toString(), 0, 20, prefs.getCookie())
                        lastSearchText = searchEditText.text.toString()
                        recyclerView.scrollToPosition(0)
                    }
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                    searchEditText.clearFocus()
                } else {
                    exitSearchMode()
                    searchMusicList.clear()
                    viewModel.searchText = ""
                    lastSearchText = ""
                    adapter.notifyDataSetChanged()
                }
                true
            } else {
                false
            }
        }

        searchEditText.addTextChangedListener { editable ->
            viewModel.inputText = editable.toString()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                enterSearchMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enterSearchMode() {
        if (!viewModel.isSearchMode) {
            viewModel.isSearchMode = true

            searchEditText.setText(viewModel.inputText)
            // 使用淡入淡出动画切换Toolbar
            toolbar.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    toolbar.visibility = View.GONE
                    searchToolbar.visibility = View.VISIBLE
                    searchToolbar.alpha = 0f
                    searchToolbar.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()

                    // 自动聚焦并显示键盘
                    searchEditText.requestFocus()
                    searchEditText.setSelection(searchEditText.text?.length ?: 0)
                    searchEditText.postDelayed({
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                    }, 200)
                }
                .start()
        }
    }

    private fun exitSearchMode() {
        if (viewModel.isSearchMode) {
            viewModel.isSearchMode = false

            // 隐藏键盘
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)

            // 使用淡入淡出动画切换回正常Toolbar
            searchToolbar.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    searchToolbar.visibility = View.GONE
                    toolbar.visibility = View.VISIBLE
                    toolbar.alpha = 0f
                    toolbar.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }

    private fun enterMultiSelectMode() {
        if (viewModel.isSearchMode) {
            searchToolbar.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    searchToolbar.visibility = View.GONE
                    multiSelectToolbar.visibility = View.VISIBLE
                    multiSelectToolbar.alpha = 0f
                    multiSelectToolbar.animate()
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                }.start()
        } else {
            toolbar.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    toolbar.visibility = View.GONE
                    multiSelectToolbar.visibility = View.VISIBLE
                    multiSelectToolbar.alpha = 0f
                    multiSelectToolbar.animate()
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                }.start()
        }
        adapter.enterMultiSelectMode()
        viewModel.isMultiSelectionMode = true
    }

    private fun exitMultiSelectMode() {
        adapter.exitMultiSelectMode()
        multiSelectToolbar.animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                multiSelectToolbar.visibility = View.GONE
                if (viewModel.isSearchMode) {
                    searchToolbar.visibility = View.VISIBLE
                    searchToolbar.alpha = 0f
                    searchToolbar.animate()
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                } else {
                    toolbar.visibility = View.VISIBLE
                    toolbar.alpha = 0f
                    toolbar.animate()
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                }
            }.start()
        viewModel.isMultiSelectionMode = false
    }
}