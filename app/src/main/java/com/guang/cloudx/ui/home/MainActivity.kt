package com.guang.cloudx.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.utils.SystemUtils
import com.guang.cloudx.ui.Screen
import com.guang.cloudx.ui.downloadManager.DownloadManagerScreen
import com.guang.cloudx.ui.login.LoginScreen
import com.guang.cloudx.ui.playList.PlayListScreen
import com.guang.cloudx.ui.settings.SettingsScreen
import com.guang.cloudx.ui.ui.theme.CloudXTheme
import com.guang.cloudx.util.ext.e
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val playerViewModel: MusicPlayerViewModel by viewModels()
    private lateinit var userId: String

    // Compose 状态
    private val searchMusicList = mutableStateListOf<Music>()
    private var isLastPage by mutableStateOf(false)
    private var lastSearchText: String = ""
    private val snackbarChannel = Channel<String>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让内容延伸到系统栏后
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        userId = prefs.getUserId()

        setContent {
            var currentThemeColor by remember { mutableStateOf(prefs.getThemeColor()) }
            var currentDarkMode by remember { mutableStateOf(prefs.getDarkMode()) }
            
            val isDark = when (currentDarkMode) {
                "启用" -> true
                "关闭" -> false
                else -> isSystemInDarkTheme()
            }

            CloudXTheme(
                darkTheme = isDark,
                themeColor = currentThemeColor
            ) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    enterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
                                fadeIn(tween(300))
                    },
                    exitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
                                fadeOut(tween(300))
                    },
                    popEnterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
                                fadeIn(tween(300))
                    },
                    popExitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
                                fadeOut(tween(300))
                    }
                ) {
                    composable(Screen.Home.route) {
                        MainActivityContent(
                            onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                            onNavigateToDownloadManager = { navController.navigate(Screen.DownloadManager.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                            onNavigateToPlaylist = { type, id ->
                                navController.navigate(Screen.Playlist.createRoute(type, id))
                            }
                        )
                    }
                    composable(Screen.Login.route) {
                        LoginScreen(
                            onBackClick = { navController.popBackStack() },
                            onLoginSuccess = {
                                navController.popBackStack()
                                userId = prefs.getUserId()
                                initNavHeader()
                            }
                        )
                    }
                    composable(
                        route = Screen.DownloadManager.route,
                        deepLinks = listOf(navDeepLink { uriPattern = "app://cloudx/download_manager" })
                    ) {
                        DownloadManagerScreen(
                            onBackClick = { navController.popBackStack() },
                            downloadDir = dir
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onThemeChanged = { color, mode ->
                                currentThemeColor = color
                                currentDarkMode = mode
                            }
                        )
                    }
                    composable(
                        route = Screen.Playlist.route,
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("id") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val type = backStackEntry.arguments?.getString("type") ?: ""
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        PlayListScreen(
                            playListId = id,
                            type = type,
                            cookie = prefs.getCookie(),
                            defaultLevel = prefs.getMusicLevel(),
                            isPreviewEnabled = prefs.getIsPreviewMusic(),
                            isAutoLevel = prefs.getIsAutoLevel(),
                            onBackClick = { navController.popBackStack() },
                            onDownloadClick = { music, level ->
                                startDownloadMusic(level = level, music = music) { showSnackbar(it) }
                            },
                            onMusicLongClick = { /* 处理长按 */ },
                            onDownloadSelected = { musics ->
                                startDownloadMusic(musics = musics) { showSnackbar(it) }
                            },
                            onSaveLevel = { level ->
                                prefs.putMusicLevel(level)
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
        }

        // 处理返回键
        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.isMultiSelectionMode -> exitMultiSelectMode()
                viewModel.isSearchMode -> exitSearchMode()
                else -> moveTaskToBack(true)
            }
        }

        // 观察搜索结果
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResultsFlow.collectLatest { result ->
                    if (result == null) return@collectLatest

                    if (viewModel.searchText.isEmpty()) {
                        viewModel.setRefreshing(false)
                        return@collectLatest
                    }

                    viewModel.setRefreshing(false)
                    val musicList = result.getOrNull()
                    if (musicList != null) {
                        searchMusicList.addAll(musicList)
                    } else {
                        if (searchMusicList.isEmpty()) {
                            showSnackbar("未搜到")
                        } else {
                            showSnackbar("没有更多了")
                            isLastPage = true
                        }
                    }
                    // 处理完结果后立即清除，防止重新进入页面时重复触发
                    viewModel.clearSearchResults()
                }
            }
        }

        // 初始化用户信息
        initNavHeader()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainActivityContent(
        onNavigateToLogin: () -> Unit,
        onNavigateToDownloadManager: () -> Unit,
        onNavigateToSettings: () -> Unit,
        onNavigateToPlaylist: (String, String) -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current

        LaunchedEffect(snackbarHostState) {
            snackbarChannel.receiveAsFlow().collect { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }

        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val userDetail by viewModel.userDetailFlow.collectAsState()

        var isSearchMode by remember { mutableStateOf(viewModel.isSearchMode) }
        var isMultiSelectMode by remember { mutableStateOf(viewModel.isMultiSelectionMode) }
        var inputText by remember { mutableStateOf(viewModel.inputText) }
        var selectedItems by remember { mutableStateOf(setOf<Music>()) }

        // Dialog states
        var showPlaylistDialog by remember { mutableStateOf(false) }
        var showAlbumDialog by remember { mutableStateOf(false) }
        var showSupportDialog by remember { mutableStateOf(false) }
        var showLogoutDialog by remember { mutableStateOf(false) }

        // 同步状态
        LaunchedEffect(viewModel.isSearchMode) {
            isSearchMode = viewModel.isSearchMode
        }
        LaunchedEffect(viewModel.isMultiSelectionMode) {
            isMultiSelectMode = viewModel.isMultiSelectionMode
        }

        // 底部弹窗状态
        var showBottomSheet by remember { mutableStateOf(false) }
        var selectedMusic by remember { mutableStateOf<Music?>(null) }
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

        val state = MainScreenState(
            searchMusicList = searchMusicList.toList(),
            isSearchMode = isSearchMode,
            isMultiSelectMode = isMultiSelectMode,
            selectedItems = selectedItems,
            inputText = inputText,
            searchText = viewModel.searchText,
            isRefreshing = isRefreshing,
            isLastPage = isLastPage,
            userInfo = userDetail?.getOrNull(),
            userId = prefs.getUserId(),
            cookie = prefs.getCookie(),
            isLoggedIn = prefs.getCookie().isNotEmpty()
        )

        Box(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                state = state,
                onSearchTextChange = { text ->
                    inputText = text
                    viewModel.inputText = text
                },
                onSearch = { text ->
                    if (text.isEmpty()) {
                        searchMusicList.clear()
                        viewModel.searchText = ""
                        viewModel.inputText = ""
                        isLastPage = false
                        viewModel.clearSearchResults()
                        viewModel.setRefreshing(false)
                        lastSearchText = ""
                        exitSearchMode()
                    } else if (lastSearchText != text) {
                        viewModel.searchText = text
                        viewModel.setRefreshing(true)
                        searchMusicList.clear()
                        isLastPage = false
                        viewModel.searchMusicFlow(text, 0, 20, prefs.getCookie())
                        lastSearchText = text
                    }
                },
                onRefresh = {
                    if (searchMusicList.isEmpty()) {
                        viewModel.setRefreshing(false)
                        return@MainScreen
                    }
                    if (isMultiSelectMode) {
                        viewModel.setRefreshing(false)
                        return@MainScreen
                    }
                    if (viewModel.searchText.isNotEmpty()) {
                        searchMusicList.clear()
                        isLastPage = false
                        viewModel.searchMusicFlow(viewModel.searchText, 0, 20, prefs.getCookie())
                    }
                },
                onLoadMore = {
                    if (!isLastPage && viewModel.searchText.isNotEmpty() && !isRefreshing && !isMultiSelectMode) {
                        viewModel.setRefreshing(true)
                        viewModel.searchMusicFlow(
                            viewModel.searchText,
                            searchMusicList.size,
                            20,
                            prefs.getCookie()
                        )
                    }
                },
                onDownloadClick = { music ->
                    startDownloadMusicWrapper(music)
                },
                onMusicClick = { music ->
                    playerViewModel.reset()
                    selectedMusic = music
                    showBottomSheet = true
                },
                onMusicLongClick = { music ->
                    if (!isMultiSelectMode) {
                        enterMultiSelectMode()
                        isMultiSelectMode = true
                        selectedItems = selectedItems + music
                    }
                },
                onEnterSearchMode = {
                    viewModel.isSearchMode = true
                    isSearchMode = true
                },
                onExitSearchMode = {
                    exitSearchMode()
                    isSearchMode = false
                },
                onEnterMultiSelectMode = {
                    enterMultiSelectMode()
                    isMultiSelectMode = true
                },
                onExitMultiSelectMode = {
                    exitMultiSelectMode()
                    isMultiSelectMode = false
                    selectedItems = emptySet()
                },
                onSelectAll = {
                    selectedItems = searchMusicList.toSet()
                },
                onInvertSelection = {
                    val newSelection = searchMusicList.filter { !selectedItems.contains(it) }.toSet()
                    selectedItems = newSelection
                },
                onDownloadSelected = {
                    if (selectedItems.isNotEmpty()) {
                        startDownloadMusicWrapper(musics = selectedItems.toList())
                        exitMultiSelectMode()
                        isMultiSelectMode = false
                        selectedItems = emptySet()
                    }
                },
                onToggleSelection = { music ->
                    selectedItems = if (selectedItems.contains(music)) {
                        selectedItems - music
                    } else {
                        selectedItems + music
                    }
                },
                onNavItemClick = { navItem ->
                    when (navItem) {
                        NavItem.DOWNLOAD_MANAGER -> {
                            onNavigateToDownloadManager()
                        }
                        NavItem.ADD_PLAYLIST -> {
                            showPlaylistDialog = true
                        }
                        NavItem.ADD_ALBUM -> {
                            showAlbumDialog = true
                        }
                        NavItem.SETTINGS -> {
                            onNavigateToSettings()
                        }
                        NavItem.SUPPORT -> {
                            showSupportDialog = true
                        }
                        NavItem.LOG_OUT -> {
                            showLogoutDialog = true
                        }
                    }
                },
                onHeadImageClick = {
                    onNavigateToLogin()
                }
            )

            // 底部弹窗
            if (showBottomSheet && selectedMusic != null) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                        selectedMusic = null
                    },
                    sheetState = sheetState
                ) {
                    MusicBottomSheetContent(
                        music = selectedMusic!!,
                        defaultLevel = prefs.getMusicLevel(),
                        isPreviewEnabled = prefs.getIsPreviewMusic(),
                        onDismiss = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                    selectedMusic = null
                                }
                            }
                        },
                        onDownload = { music, level ->
                            startDownloadMusicWrapper(music = music, level = level)
                            if (prefs.getIsAutoLevel()) {
                                prefs.putMusicLevel(level)
                            }
                        },
                        onLongClickText = { SystemUtils.copyToClipboard(context, "music", it) },
                        playerViewModel = playerViewModel,
                        cookie = prefs.getCookie()
                    )
                }
            }

            // Dialogs
            if (showPlaylistDialog) {
                PlaylistDialog(
                    onDismiss = { showPlaylistDialog = false },
                    onConfirm = { text ->
                        val id = with(text) {
                            if (this.matches(Regex("[0-9]+"))) this
                            else """music\.163\.com.*?playlist.*?[?&]id=(\d+)""".toRegex().find(this)?.groupValues?.get(1)
                                ?: """music\.163\.com.*?playlist/(\d+)""".toRegex().find(this)?.groupValues?.get(1)
                        }
                        if (id != null)
                            onNavigateToPlaylist("playlist", id)
                        else showSnackbar("请输入正确的歌单ID或链接")
                    }
                )
            }

            if (showAlbumDialog) {
                AlbumDialog(
                    onDismiss = { showAlbumDialog = false },
                    onConfirm = { text ->
                        val id = with(text) {
                            if (this.matches(Regex("[0-9]+"))) this
                            else """music\.163\.com.*?album.*?[?&]id=(\d+)""".toRegex().find(this)?.groupValues?.get(1)
                                ?: """music\.163\.com.*?album/(\d+)""".toRegex().find(this)?.groupValues?.get(1)
                        }
                        if (id != null)
                            onNavigateToPlaylist("album", id)
                        else showSnackbar("请输入正确的专辑ID或链接")
                    }
                )
            }

            if (showSupportDialog) {
                SupportDialog(
                    onDismiss = { showSupportDialog = false }
                )
            }

            if (showLogoutDialog) {
                LogoutDialog(
                    onDismiss = { showLogoutDialog = false },
                    onConfirm = {
                        prefs.putCookie("")
                        prefs.putUserId("")

                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                        try {
                            File(dataDir, "app_webview").deleteRecursively()
                            File(cacheDir, "webviewCache").deleteRecursively()
                        } catch (e: Exception) {
                            e.e()
                        }

                        showSnackbar("已退出登录")
                        userId = ""
                        initNavHeader()
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getUserId() != userId) {
            initNavHeader()
            userId = prefs.getUserId()
        }
    }

    private fun initNavHeader() {
        if (prefs.getUserId().isNotEmpty()) {
            viewModel.getUserDetailFlow(prefs.getUserId(), prefs.getCookie())
        }
    }

    private fun enterMultiSelectMode() {
        viewModel.isMultiSelectionMode = true
    }

    private fun exitMultiSelectMode() {
        viewModel.isMultiSelectionMode = false
    }

    private fun exitSearchMode() {
        viewModel.isSearchMode = false
    }

    private fun showSnackbar(message: String) {
        lifecycleScope.launch {
            snackbarChannel.send(message)
        }
    }

    // 下载音乐的包装方法
    private fun startDownloadMusicWrapper(
        music: Music? = null,
        musics: List<Music> = emptyList(),
        level: String = prefs.getMusicLevel()
    ) {
        startDownloadMusic(level, musics, music) { showSnackbar(it) }
    }

}
