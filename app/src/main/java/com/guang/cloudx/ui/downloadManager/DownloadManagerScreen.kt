package com.guang.cloudx.ui.downloadManager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.SystemUtils
import com.guang.cloudx.ui.home.TooltipIconButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadManagerScreen(
    onBackClick: () -> Unit
) {
    val viewModel: DownloadViewModel = viewModel()
    val downloadingList by viewModel.downloading.collectAsState()
    val completedList by viewModel.completed.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf("正在下载", "已完成")

    // 弹窗状态
    var showDeleteAllCompletedDialog by remember { mutableStateOf(false) }
    var showDeleteAllFailedDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<DownloadItemUi?>(null) }

    val context = LocalContext.current
    val prefs = remember { SharedPreferencesUtils(context) }

    // 注册广播接收器以更新进度
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.updateProgressById(intent) {
                    // 下载完成时的回调，如果需要可以在这里处理
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("DOWNLOAD_PROGRESS")
            addAction("DOWNLOAD_COMPLETED")
            addAction("DOWNLOAD_FAILED")
            addAction("DOWNLOAD_FINISHED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBackClick,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                },
                actions = {
                    // 正在下载页：如果有失败任务，显示全部重试和全部删除
                    if (pagerState.currentPage == 0) {
                        val hasFailedTasks = downloadingList.any { it.status == TaskStatus.FAILED }
                        if (hasFailedTasks) {
                            TooltipIconButton(
                                onClick = {
                                    val baseActivity = context as? BaseActivity
                                    baseActivity?.dir?.let { dir ->
                                        viewModel.retryAllFailed(
                                            context,
                                            prefs.getMusicLevel(),
                                            prefs.getCookie(),
                                            dir,
                                            MusicDownloadRules(
                                                isSaveLrc = prefs.getIsSaveLrc(),
                                                isSaveTlLrc = prefs.getIsSaveTlLrc(),
                                                isSaveRomaLrc = prefs.getIsSaveRomaLrc(),
                                                isSaveYrc = prefs.getIsSaveYrc(),
                                                fileName = prefs.getDownloadFileName()!!,
                                                delimiter = prefs.getArtistsDelimiter()!!,
                                                encoding = prefs.getLrcEncoding()!!,
                                                concurrentDownloads = prefs.getConcurrentDownloads()
                                            )
                                        )
                                    }
                                },
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "全部重试"
                            )
                            TooltipIconButton(
                                onClick = { showDeleteAllFailedDialog = true },
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "删除所有失败任务"
                            )
                        }
                    }

                    // 已完成页：如果有已完成任务，显示全部删除
                    if (pagerState.currentPage == 1 && completedList.isNotEmpty()) {
                        TooltipIconButton(
                            onClick = { showDeleteAllCompletedDialog = true },
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "全部删除"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    DownloadingList(
                        list = downloadingList,
                        viewModel = viewModel
                    )
                } else {
                    CompletedList(
                        list = completedList,
                        onDelete = { item -> viewModel.deleteCompleted(item) {} },
                        onClick = { item -> showDetailDialog = item }
                    )
                }
            }
        }
    }

    // 删除所有已完成任务确认弹窗
    if (showDeleteAllCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllCompletedDialog = false },
            title = { Text("提示") },
            text = { Text("真的要删除全部已完成记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllCompleted {}
                        showDeleteAllCompletedDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllCompletedDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除所有失败任务确认弹窗
    if (showDeleteAllFailedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllFailedDialog = false },
            title = { Text("提示") },
            text = { Text("真的要删除全部失败记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllFailed()
                        showDeleteAllFailedDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllFailedDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 详情弹窗
    if (showDetailDialog != null) {
        val item = showDetailDialog!!
        val message = remember(item) {
            with(item) {
                """
                标题：${music.name}
                音乐ID：${music.id}
                艺术家：${music.artists.joinToString("、") { "${it.name}(${it.id})" }}
                专辑：${music.album.name}
                专辑ID：${music.album.id}
                封面：${music.album.picUrl}
                
                保存时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeStamp))}
                """.trimIndent()
            }
        }

        AlertDialog(
            onDismissRequest = { showDetailDialog = null },
            title = { Text("详细信息") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = null }) {
                    Text("关闭")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        SystemUtils.copyToClipboard(context, "MusicDetail", message)
                    }
                ) {
                    Text("复制")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadingList(
    list: List<DownloadItemUi>,
    viewModel: DownloadViewModel
) {
    val context = LocalContext.current
    val prefs = remember { SharedPreferencesUtils(context) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(list, key = { it.id }) { item ->
            DownloadingItem(
                item = item,
                modifier = Modifier.animateItem(),
                onRetry = {
                    val baseActivity = context as? BaseActivity
                    baseActivity?.dir?.let { dir ->
                        viewModel.retryDownload(
                            context,
                            item,
                            prefs.getMusicLevel(),
                            prefs.getCookie(),
                            dir,
                            MusicDownloadRules(
                                isSaveLrc = prefs.getIsSaveLrc(),
                                isSaveTlLrc = prefs.getIsSaveTlLrc(),
                                isSaveRomaLrc = prefs.getIsSaveRomaLrc(),
                                isSaveYrc = prefs.getIsSaveYrc(),
                                fileName = prefs.getDownloadFileName()!!,
                                delimiter = prefs.getArtistsDelimiter()!!,
                                encoding = prefs.getLrcEncoding()!!,
                                concurrentDownloads = prefs.getConcurrentDownloads()
                            )
                        )
                    }
                },
                onDelete = { viewModel.deleteFailed(item) },
                onLongClick = {
                    if (item.status == TaskStatus.FAILED) {
                        SystemUtils.copyToClipboard(context, "DownloadError", item.failureReason ?: "无错误信息")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadingItem(
    item: DownloadItemUi,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = item.progress / 100f,
        label = "ProgressAnimation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { if (item.status == TaskStatus.FAILED) onRetry() },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            AsyncImage(
                model = item.music.album.picUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)), // ShapeAppearance.Material3.LargeComponent 约为 12-16dp
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 中间内容
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp) // 匹配封面高度
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 标题
                Text(
                    text = item.music.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 艺术家
                Text(
                    text = item.music.artists.joinToString("/") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                if (item.status == TaskStatus.FAILED) {
                    Text(
                        text = "下载失败: ${item.failureReason ?: "未知错误"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }

            // 右侧按钮 (仅失败时显示删除)
            if (item.status == TaskStatus.FAILED) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.delete_24px),
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletedList(
    list: List<DownloadItemUi>,
    onDelete: (DownloadItemUi) -> Unit,
    onClick: (DownloadItemUi) -> Unit
) {
    // 倒序显示，最新的在上面
    val reversedList = remember(list) { list.asReversed() }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(reversedList, key = { it.id }) { item ->
            CompletedItem(
                item = item,
                modifier = Modifier.animateItem(),
                onClick = { onClick(item) },
                onDelete = { onDelete(item) }
            )
        }
    }
}

@Composable
fun CompletedItem(
    item: DownloadItemUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {}
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            AsyncImage(
                model = item.music.album.picUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 中间内容
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 标题
                Text(
                    text = item.music.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 艺术家
                Text(
                    text = item.music.artists.joinToString("/") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "已完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_24px),
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
