package com.guang.cloudx.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.model.User
import com.guang.cloudx.logic.repository.MusicDownloadRepository
import com.guang.cloudx.logic.utils.AudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

// 播放器状态
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val isBuffering: Boolean = false,
    val musicFile: File? = null
)

// 音乐播放器 ViewModel
class MusicPlayerViewModel : ViewModel() {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

    private var audioPlayer: AudioPlayer? = null

    fun cacheMusic(music: Music, parent: File, cookie: String) {
        if (audioPlayer != null) {
            play()
            return
        }
        _playerState.value = _playerState.value.copy(isBuffering = true)
        viewModelScope.launch {
            runCatching {
                MusicDownloadRepository().cacheMusic(music, parent, cookie)
            }.onSuccess { file ->
                _playerState.value = _playerState.value.copy(musicFile = file, isBuffering = false)
                playAudio(file)
            }.onFailure {
                _playerState.value = _playerState.value.copy(isBuffering = false)
            }
        }
    }

    private fun playAudio(file: File) {
        audioPlayer?.release()
        audioPlayer = AudioPlayer(file).apply {
            prepare(
                onPrepared = {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        totalDuration = duration().toLong()
                    )
                },
                onError = {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                }
            )
            setOnProgressUpdateListener { currentMs, totalMs ->
                _playerState.value = _playerState.value.copy(
                    currentPosition = currentMs.toLong(),
                    totalDuration = totalMs.toLong()
                )
            }
            setOnCompletionListener {
                _playerState.value = _playerState.value.copy(isPlaying = false, currentPosition = 0)
            }
        }
    }

    fun play() {
        audioPlayer?.play()
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }

    fun pause() {
        audioPlayer?.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun seekTo(position: Long) {
        audioPlayer?.seekTo(position.toInt())
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun reset() {
        audioPlayer?.release()
        audioPlayer = null
        _playerState.value = PlayerState()
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer?.release()
    }
}

// 主屏幕状态
data class MainScreenState(
    val searchMusicList: List<Music> = emptyList(),
    val isSearchMode: Boolean = false,
    val isMultiSelectMode: Boolean = false,
    val selectedItems: Set<Music> = emptySet(),
    val inputText: String = "",
    val searchText: String = "",
    val isRefreshing: Boolean = false,
    val isLastPage: Boolean = false,
    val userInfo: User? = null,
    val userId: String = "",
    val cookie: String = "",
    val isLoggedIn: Boolean = false
)

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainScreenState,
    onSearchTextChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDownloadClick: (Music) -> Unit,
    onMusicClick: (Music) -> Unit,
    onMusicLongClick: (Music) -> Unit,
    onEnterSearchMode: () -> Unit,
    onExitSearchMode: () -> Unit,
    onEnterMultiSelectMode: () -> Unit,
    onExitMultiSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onToggleSelection: (Music) -> Unit,
    onNavItemClick: (NavItem) -> Unit,
    onHeadImageClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = (LocalContext.current as? Activity)

    BackHandler {
        when {
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
            state.isMultiSelectMode -> {
                onExitMultiSelectMode()
            }
            state.isSearchMode -> {
                onExitSearchMode()
            }
            else -> {
                activity?.moveTaskToBack(true)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                userInfo = state.userInfo,
                userId = state.userId,
                isLoggedIn = state.isLoggedIn,
                onHeadImageClick = onHeadImageClick,
                onNavItemClick = onNavItemClick
            )
        }
    ) {
        Scaffold(
            topBar = {
                MainTopBar(
                    isSearchMode = state.isSearchMode,
                    isMultiSelectMode = state.isMultiSelectMode,
                    inputText = state.inputText,
                    selectedCount = state.selectedItems.size,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = onEnterSearchMode,
                    onSearchTextChange = onSearchTextChange,
                    onSearch = onSearch,
                    onBackClick = {
                        if (state.isMultiSelectMode) onExitMultiSelectMode()
                        else if (state.isSearchMode) onExitSearchMode()
                    },
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    onDownloadSelected = onDownloadSelected
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (state.searchMusicList.isEmpty() && !state.isRefreshing) {
                    // 空状态提示
                    Text(
                        text = "搜点什么吧 ο(=•ω＜=)ρ⌒☆",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MusicList(
                        musicList = state.searchMusicList,
                        isRefreshing = state.isRefreshing,
                        isLastPage = state.isLastPage,
                        isMultiSelectMode = state.isMultiSelectMode,
                        selectedItems = state.selectedItems,
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        onDownloadClick = onDownloadClick,
                        onMusicClick = { music ->
                            if (state.isMultiSelectMode) {
                                onToggleSelection(music)
                            } else {
                                onMusicClick(music)
                            }
                        },
                        onMusicLongClick = onMusicLongClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    isSearchMode: Boolean,
    isMultiSelectMode: Boolean,
    inputText: String,
    selectedCount: Int,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBackClick: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDownloadSelected: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }

    LaunchedEffect(inputText) {
        if (textFieldValue.text != inputText) {
            textFieldValue = textFieldValue.copy(text = inputText)
        }
    }

    val topBarState = when {
        isMultiSelectMode -> TopBarState.MultiSelect
        isSearchMode -> TopBarState.Search
        else -> TopBarState.Normal
    }

    AnimatedContent(
        targetState = topBarState,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "TopBarState"
    ) { targetState ->
        when (targetState) {
            TopBarState.MultiSelect -> {
                TopAppBar(
                    title = { Text("已选 $selectedCount 项") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选")
                        }
                        IconButton(onClick = onInvertSelection) {
                            Icon(Icons.Default.FlipToFront, contentDescription = "反选")
                        }
                        IconButton(
                            onClick = onDownloadSelected,
                            enabled = selectedCount > 0
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "下载")
                        }
                    }
                )
            }
            TopBarState.Search -> {
                // 进入搜索模式时请求焦点并移动光标到末尾
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                }

                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                onSearchTextChange(it.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .padding(vertical = 4.dp)
                                .focusRequester(focusRequester),
                            placeholder = { Text("搜索音乐") },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (inputText.isNotEmpty()) {
                                    IconButton(onClick = { onSearchTextChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "清除")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (inputText.isNotEmpty()) {
                                        onSearch(inputText)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    } else {
                                        onBackClick()
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {}
                )
            }
            TopBarState.Normal -> {
                TopAppBar(
                    title = { Text("CloudX") },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "打开侧边栏")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                )
            }
        }
    }
}

private enum class TopBarState {
    Normal, Search, MultiSelect
}

// 导航项枚举
enum class NavItem {
    ADD_PLAYLIST,
    ADD_ALBUM,
    DOWNLOAD_MANAGER,
    SETTINGS,
    SUPPORT,
    LOG_OUT
}

@Composable
fun DrawerContent(
    userInfo: User?,
    userId: String,
    isLoggedIn: Boolean,
    onHeadImageClick: () -> Unit,
    onNavItemClick: (NavItem) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 300.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            AsyncImage(
                model = userInfo?.avatarUrl ?: "",
                contentDescription = "头像",
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable { onHeadImageClick() },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    userInfo != null -> userInfo.name
                    isLoggedIn -> "已登录"
                    else -> "未登录"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = when {
                    userId.isNotEmpty() -> userId
                    isLoggedIn -> "输入用户ID以获取头像昵称"
                    else -> "点按头像以登录"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, contentDescription = null) },
            label = { Text("解析歌单", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { onNavItemClick(NavItem.ADD_PLAYLIST) },
            modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Album, contentDescription = null) },
            label = { Text("解析专辑", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { onNavItemClick(NavItem.ADD_ALBUM) },
            modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            label = { Text("下载管理", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { onNavItemClick(NavItem.DOWNLOAD_MANAGER) },
            modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("设置", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { onNavItemClick(NavItem.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.VolunteerActivism, contentDescription = null) },
            label = { Text("赞助作者", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { onNavItemClick(NavItem.SUPPORT) },
            modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            label = { Text("退出登录", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { onNavItemClick(NavItem.LOG_OUT) },
            modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicList(
    musicList: List<Music>,
    isRefreshing: Boolean,
    isLastPage: Boolean,
    isMultiSelectMode: Boolean,
    selectedItems: Set<Music>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDownloadClick: (Music) -> Unit,
    onMusicClick: (Music) -> Unit,
    onMusicLongClick: (Music) -> Unit
) {
    val listState = rememberLazyListState()

    // 监听滚动到底部，触发加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                (lastVisibleItem?.index ?: 0) >= (layoutInfo.totalItemsCount - 5)
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isRefreshing && !isLastPage) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(musicList, key = { it.id }) { music ->
                MusicItem(
                    music = music,
                    isMultiSelectMode = isMultiSelectMode,
                    isSelected = selectedItems.contains(music),
                    onDownloadClick = { onDownloadClick(music) },
                    onClick = { onMusicClick(music) },
                    onLongClick = { onMusicLongClick(music) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicItem(
    music: Music,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .animateContentSize()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面
            AsyncImage(
                model = music.album.picUrl,
                contentDescription = "专辑封面",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = music.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val authors = music.artists.joinToString("/") { it.name }
                val albumName = music.album.name
                Text(
                    text = if (albumName.isNotEmpty()) "$authors - $albumName" else authors,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 下载按钮
            AnimatedVisibility(
                visible = !isMultiSelectMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// 底部弹窗内容 - 用于选择音质并下载
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun MusicBottomSheetContent(
    music: Music,
    defaultLevel: String,
    isPreviewEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownload: (Music, String) -> Unit,
    onLongClickText: (String) -> Unit,
    playerViewModel: MusicPlayerViewModel,
    cookie: String
) {
    val context = LocalContext.current
    val playerState by playerViewModel.playerState.collectAsState()
    var selectedLevel by remember { mutableStateOf(defaultLevel) }
    var showCoverDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(music.id) {
        playerViewModel.reset()
        onDispose {
            playerViewModel.pause()
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 专辑封面
        AsyncImage(
            model = music.album.picUrl,
            contentDescription = "专辑封面",
            modifier = Modifier
                .size(120.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = true
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showCoverDialog = true },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 歌曲名称
        Text(
            text = music.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .basicMarquee()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { onLongClickText(music.name) }
                ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 艺术家
        Text(
            text = music.artists.joinToString("/") { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .basicMarquee()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { onLongClickText(music.artists.joinToString("/") { it.name }) }
                )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = music.album.name.ifEmpty { "未知专辑" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .basicMarquee()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { onLongClickText(music.album.name) }
                )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isPreviewEnabled) {
            // 播放控制区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放按钮
                    FilledIconButton(
                        onClick = {
                            if (playerState.isPlaying) {
                                playerViewModel.pause()
                            } else {
                                playerViewModel.cacheMusic(music, context.externalCacheDir!!, cookie)
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 进度条
                    Slider(
                        value = playerState.currentPosition.toFloat(),
                        onValueChange = { playerViewModel.seekTo(it.toLong()) },
                        valueRange = 0f..(playerState.totalDuration.toFloat().takeIf { it > 0 } ?: 1f),
                        modifier = Modifier.weight(1f),
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(16.dp, 16.dp)
                            )
                        },
                        track = {
                            SliderDefaults.Track(
                                sliderState = it,
                                modifier = Modifier.height(6.dp).clip(RoundedCornerShape(3.dp)),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 48.dp (按钮) + 12.dp (间距) = 60.dp，确保时间文本与 Slider 对齐
                    Spacer(modifier = Modifier.width(60.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playerState.currentPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(playerState.totalDuration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 音质选择
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
                val levels = listOf("standard" to "标准", "exhigh" to "极高", "lossless" to "无损", "hires" to "Hi-Res")
                levels.forEach { (level, label) ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = level },
                        label = { Text(label) },
                        leadingIcon = if (selectedLevel == level) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                onDownload(music, selectedLevel)
                onDismiss()
            }) {
                Text("下载")
            }
        }

        if (showCoverDialog) {
            Dialog(onDismissRequest = { showCoverDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        AsyncImage(
                            model = music.album.picUrl,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCoverDialog = false }) {
                                Text("取消")
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    saveCoverToDir(context, music.album.picUrl, "${music.name}_cover") {
                                        scope.launch { snackbarHostState.showSnackbar(it) }
                                    }
                                }
                                showCoverDialog = false
                            }) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private suspend fun saveCoverToDir(context: android.content.Context, url: String, fileName: String, onShowSnackbar: (String) -> Unit) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val activity = context.findBaseActivity()
            val pickedDir = activity?.dir

            if (pickedDir == null) {
                onShowSnackbar("未设置下载目录")
                return@withContext
            }

            val safeFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), " ")
            val newFile = pickedDir.createFile("image/jpeg", safeFileName)

            if (newFile != null) {
                context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                    java.net.URL(url).openStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                onShowSnackbar("已保存封面至下载目录")
            } else {
                onShowSnackbar("创建文件失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onShowSnackbar("保存失败: ${e.message}")
        }
    }
}

private tailrec fun android.content.Context.findBaseActivity(): BaseActivity? {
    return when (this) {
        is BaseActivity -> this
        is android.content.ContextWrapper -> baseContext.findBaseActivity()
        else -> null
    }
}
