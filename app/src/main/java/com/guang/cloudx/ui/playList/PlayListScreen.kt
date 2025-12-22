package com.guang.cloudx.ui.playList

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.guang.cloudx.logic.model.Music
import com.guang.cloudx.logic.utils.SystemUtils
import com.guang.cloudx.ui.home.MusicBottomSheetContent
import com.guang.cloudx.ui.home.MusicItem
import com.guang.cloudx.ui.home.MusicPlayerViewModel
import com.guang.cloudx.ui.home.TooltipIconButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListScreen(
    playListId: String,
    type: String,
    cookie: String,
    defaultLevel: String,
    isPreviewEnabled: Boolean,
    isAutoLevel: Boolean,
    onBackClick: () -> Unit,
    onDownloadClick: (Music, String) -> Unit,
    onMusicLongClick: (Music) -> Unit,
    onDownloadSelected: (List<Music>) -> Unit,
    onSaveLevel: (String) -> Unit,
    playerViewModel: MusicPlayerViewModel
) {
    val viewModel: PlayListViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // BottomSheet State
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedMusic by remember { mutableStateOf<Music?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.getPlayList(playListId, cookie, type)
    }

    BackHandler(enabled = state.isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val topBarHeight = 64.dp
    val topBarHeightPx = with(density) { topBarHeight.toPx() }

    // Calculate scroll offset for fading effect
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

    val scrollOffset by remember {
        derivedStateOf {
            if (firstVisibleItemIndex > 0) topBarHeightPx
            else firstVisibleItemScrollOffset.toFloat().coerceAtMost(topBarHeightPx)
        }
    }

    val alpha by remember {
        derivedStateOf {
            (scrollOffset / topBarHeightPx).coerceIn(0f, 1f)
        }
    }

    Scaffold(
        topBar = {
            PlayListTopBar(
                title = state.playList?.name ?: "歌单",
                isMultiSelectMode = state.isMultiSelectMode,
                selectedCount = state.selectedItems.size,
                alpha = alpha,
                onBackClick = {
                    if (state.isMultiSelectMode) viewModel.exitMultiSelectMode()
                    else onBackClick()
                },
                onSelectAll = { viewModel.selectAll() },
                onInvertSelection = { viewModel.invertSelection() },
                onDownloadSelected = {
                    onDownloadSelected(state.selectedItems.toList())
                    viewModel.exitMultiSelectMode()
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding()) // Only apply bottom padding
            ) {
                if (state.playList != null) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PlayListHeader(state.playList!!)
                        }

                        itemsIndexed(state.musicList, key = { _, music -> music.id }) { index, music ->
                            MusicItem(
                                music = music,
                                displayIndex = if (type == "album") index + 1 else null,
                                isMultiSelectMode = state.isMultiSelectMode,
                                isSelected = state.selectedItems.contains(music),
                                onDownloadClick = { onDownloadClick(music, defaultLevel) },
                                onClick = {
                                    if (state.isMultiSelectMode) {
                                        viewModel.toggleSelection(music)
                                    } else {
                                        playerViewModel.reset()
                                        selectedMusic = music
                                        showBottomSheet = true
                                    }
                                },
                                onLongClick = {
                                    if (!state.isMultiSelectMode) {
                                        viewModel.enterMultiSelectMode()
                                        viewModel.toggleSelection(music)
                                    } else {
                                        onMusicLongClick(music)
                                    }
                                }
                            )
                        }
                    }
                } else if (!state.isLoading && state.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.error ?: "未知错误")
                    }
                }
            }

            // Bottom Sheet
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
                        defaultLevel = defaultLevel,
                        isPreviewEnabled = isPreviewEnabled,
                        onDismiss = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                    selectedMusic = null
                                }
                            }
                        },
                        onDownload = { music, level ->
                            onDownloadClick(music, level)
                            if (isAutoLevel) {
                                onSaveLevel(level)
                            }
                        },
                        onLongClickText = { SystemUtils.copyToClipboard(context, "music", it) },
                        playerViewModel = playerViewModel,
                        cookie = cookie
                    )
                }
            }
        }
    }
}

@Composable
fun PlayListHeader(playList: com.guang.cloudx.logic.model.PlayList) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp) // Increased height for better visual
    ) {
        // Background Image with Blur/Dim
        AsyncImage(
            model = playList.coverImgUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.size(120.dp)
                ) {
                    AsyncImage(
                        model = playList.coverImgUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = playList.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ID: ${playList.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListTopBar(
    title: String,
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    alpha: Float,
    onBackClick: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDownloadSelected: () -> Unit
) {
    AnimatedContent(
        targetState = isMultiSelectMode,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "TopBarState"
    ) { isMultiSelect ->
        if (isMultiSelect) {
            TopAppBar(
                title = { Text("已选 $selectedCount 项") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBackClick,
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭"
                    )
                },
                actions = {
                    TooltipIconButton(
                        onClick = onSelectAll,
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "全选"
                    )
                    TooltipIconButton(
                        onClick = onInvertSelection,
                        imageVector = Icons.Default.FlipToFront,
                        contentDescription = "反选"
                    )
                    TooltipIconButton(
                        onClick = onDownloadSelected,
                        imageVector = Icons.Default.Download,
                        contentDescription = "下载",
                        enabled = selectedCount > 0
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        } else {
            val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
            val contentColor = if (alpha > 0.5f) MaterialTheme.colorScheme.onSurface else Color.White

            TopAppBar(
                title = {
                    if (alpha > 0.8f) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = contentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor,
                    actionIconContentColor = contentColor
                )
            )
        }
    }
}
