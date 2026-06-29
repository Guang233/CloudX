package com.guang.cloudx.ui.myPlayLists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.guang.cloudx.logic.model.PlayList
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.ui.home.TooltipIconButton

/**
 * 我的歌单页面
 *
 * @param onBackClick 返回按钮点击回调
 * @param onPlayListClick 歌单项点击回调，可用于后续跳转到歌单详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPlayListsScreen(
    onBackClick: () -> Unit,
    onPlayListClick: (PlayList) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { SharedPreferencesUtils(context) }
    val viewModel: MyPlayListsViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMyPlayLists(prefs.getUserId(), prefs.getCookie())
    }

    MyPlayListsScreenContent(
        state = state,
        onBackClick = onBackClick,
        onRefresh = { viewModel.refresh(prefs.getUserId(), prefs.getCookie()) },
        onPlayListClick = onPlayListClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyPlayListsScreenContent(
    state: MyPlayListsUiState,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onPlayListClick: (PlayList) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的歌单") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBackClick,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.playLists,
                        key = { it.id }
                    ) { playList ->
                        MyPlayListItem(
                            playList = playList,
                            onClick = { onPlayListClick(playList) },
                            onLongClick = { /* 预留长按操作 */ }
                        )
                    }
                }
            }

            // 空状态
            if (state.playLists.isEmpty() && !state.isLoading && !state.isRefreshing && state.error == null) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 错误状态
            if (!state.isLoading && state.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 单条歌单展示项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyPlayListItem(
    playList: PlayList,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图
            AsyncImage(
                model = playList.coverImgUrl,
                contentDescription = playList.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 歌单信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playList.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${playList.musics.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "还没有创建歌单 ο(=•ω＜=)ρ⌒☆",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
