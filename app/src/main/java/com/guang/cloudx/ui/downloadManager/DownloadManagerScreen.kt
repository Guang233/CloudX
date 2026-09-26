package com.guang.cloudx.ui.downloadManager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import com.guang.cloudx.R
import com.guang.cloudx.logic.database.LocalMusicFile
import com.guang.cloudx.logic.utils.SystemUtils
import com.guang.cloudx.logic.utils.applicationViewModels
import com.guang.cloudx.ui.home.TooltipIconButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadManagerScreen(
    onBackClick: () -> Unit,
    downloadDir: DocumentFile?,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = remember(application) { applicationViewModels<DownloadViewModel>(application).value }
    val downloadingList by viewModel.downloading.collectAsState()
    val completedList by viewModel.completed.collectAsState()
    val localMusic = rememberLocalMusicViewModel()
    val localFiles by localMusic.files.collectAsState()
    val filesByUri = remember(localFiles) { localFiles.associateBy { it.uri } }
    val snackbar = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf("下载任务", "已完成")
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var cancelIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(downloadingList.map { it.id }, pagerState.currentPage) {
        selectedIds = selectedIds.intersect(downloadingList.map { it.id }.toSet())
        if (pagerState.currentPage != 0) {
            selectionMode = false
            selectedIds = emptySet()
        }
    }
    BackHandler(selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 弹窗状态
    var showDeleteAllCompletedDialog by remember { mutableStateOf(false) }
    var showDeleteAllFailedDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<DownloadItemUi?>(null) }
    var showDeleteDialog by remember { mutableStateOf<DownloadItemUi?>(null) }
    var deleting by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            DownloadManagerTopBar(
                selectionMode = selectionMode,
                selectedCount = selectedIds.size,
                onBack = onBackClick,
                onCloseSelection = { selectionMode = false; selectedIds = emptySet() },
                actions = { selecting ->
                    if (pagerState.currentPage == 0) {
                        DownloadTaskActions(
                            tasks = downloadingList,
                            selectionMode = selecting,
                            selectedIds = selectedIds,
                            onSelect = { selectionMode = true },
                            onSelectAll = { selectedIds = downloadingList.map { it.id }.toSet() },
                            onInvertSelection = { selectedIds = downloadingList.map { it.id }.toSet() - selectedIds },
                            onPause = { viewModel.pauseTasks(context, it) },
                            onContinue = { viewModel.resumeTasks(context, it) },
                            onCancel = { cancelIds = it },
                            onRetryFailed = { viewModel.retryAllFailed(context) },
                            onClearFailed = { showDeleteAllFailedDialog = true },
                        )
                    }

                    if (pagerState.currentPage == 1) {
                        TooltipIconButton(
                            onClick = { localMusic.refresh() },
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新本地文件状态",
                        )
                    }
                    // 批量清理仅移除记录，绝不顺带删除文件。
                    if (pagerState.currentPage == 1 && completedList.isNotEmpty()) {
                        TooltipIconButton(
                            onClick = { showDeleteAllCompletedDialog = true },
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清空已完成记录",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page == 0) {
                    DownloadingList(
                        list = downloadingList,
                        viewModel = viewModel,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelection = { id ->
                            selectionMode = true
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        onCancel = { cancelIds = listOf(it) },
                    )
                } else {
                    CompletedList(
                        list = completedList,
                        filesByUri = filesByUri,
                        onDelete = { item -> showDeleteDialog = item },
                        onClick = { item ->
                            showDetailDialog = item
                            localMusic.refresh()
                        },
                    )
                }
            }
        }
    }

    if (cancelIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { cancelIds = emptyList() },
            title = { Text("取消 ${cancelIds.size} 个任务？") },
            text = { Text("将停止所选任务并清理对应临时文件，之后需要重新下载。已保存的歌曲和歌词不会删除；保存已完成的任务会保留在已完成列表。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelTasks(context, cancelIds)
                    selectedIds = selectedIds - cancelIds.toSet()
                    cancelIds = emptyList()
                }) { Text("取消任务") }
            },
            dismissButton = { TextButton(onClick = { cancelIds = emptyList() }) { Text("返回") } },
        )
    }

    // 删除所有已完成任务确认弹窗
    if (showDeleteAllCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllCompletedDialog = false },
            title = { Text("提示") },
            text = { Text("清空全部已完成记录？本地歌曲文件和已下载标记都会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllCompleted {}
                        showDeleteAllCompletedDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllCompletedDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 删除所有失败或暂停任务确认弹窗
    if (showDeleteAllFailedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllFailedDialog = false },
            title = { Text("提示") },
            text = { Text("删除全部失败或暂停任务，并清理对应临时文件？已保存的歌曲和歌词不会删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllFailed()
                        showDeleteAllFailedDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllFailedDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    showDeleteDialog?.let { selected ->
        val item = completedList.find { it.id == selected.id } ?: selected
        val file = filesByUri[item.savedFileUri]?.takeIf { it.musicId == item.music.id }
        var deleteSource by remember(item.id) { mutableStateOf(false) }
        var deleteLyrics by remember(item.id) { mutableStateOf(false) }
        val canDeleteFile =
            file != null &&
                (file.state != LocalMusicFile.MISSING || file.lyricUri != null)
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = null },
            title = { Text("删除歌曲") },
            text = {
                Column {
                    Text(item.savedFileName ?: item.music.name)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteSource,
                            onCheckedChange = { deleteSource = it },
                            enabled = canDeleteFile && !deleting,
                        )
                        Text("删除本地歌曲文件（不可恢复）")
                    }
                    if (deleteSource && file?.lyricUri != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteLyrics,
                                onCheckedChange = { deleteLyrics = it },
                                enabled = !deleting,
                            )
                            Text("同时删除关联歌词")
                        }
                    }
                    Text(
                        if (deleteSource) {
                            "保留下载记录；其他副本不会删除。"
                        } else {
                            "仅移除这条记录，保留本地文件和已下载标记。"
                        },
                    )
                    if (!canDeleteFile) Text("${completedFileStatus(item, filesByUri)}，只能移除记录。")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting && (!deleteSource || canDeleteFile),
                    onClick = {
                        if (!deleteSource) {
                            viewModel.deleteCompleted(item) {}
                            showDeleteDialog = null
                        } else {
                            deleting = true
                            scope.launch {
                                try {
                                    val message = localMusic.deleteFile(item, deleteLyrics)
                                    showDeleteDialog = null
                                    snackbar.showSnackbar(message)
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        if (deleting) {
                            "正在删除…"
                        } else if (deleteSource) {
                            "删除文件"
                        } else {
                            "移除记录"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }, enabled = !deleting) { Text("取消") }
            },
        )
    }

    // 详情弹窗
    if (showDetailDialog != null) {
        val item = completedList.find { it.id == showDetailDialog!!.id } ?: showDetailDialog!!
        DownloadDetailsDialog(
            item = item,
            indexedFile = filesByUri[item.savedFileUri],
            onDismiss = { showDetailDialog = null },
            onOpen = { openCompletedFile(context, item) },
            onShare = { shareCompletedFile(context, item) },
        )
    }
}

private fun completedFileStatus(
    item: DownloadItemUi,
    files: Map<String, LocalMusicFile>,
): String {
    val file = files[item.savedFileUri] ?: return "文件未确认"
    if (file.musicId != item.music.id) return "文件已被覆盖"
    return when (file.state) {
        LocalMusicFile.PRESENT -> "已下载"
        LocalMusicFile.MISSING -> "文件已删除"
        else -> "目录不可访问，请检查授权"
    }
}

private fun findCompletedDocument(
    context: Context,
    item: DownloadItemUi,
): DocumentFile? {
    val uri = item.savedFileUri ?: return null
    return DocumentFile.fromSingleUri(context, Uri.parse(uri))
}

private fun openCompletedFile(
    context: Context,
    item: DownloadItemUi,
) {
    val document = findCompletedDocument(context, item) ?: return
    val mimeType =
        when (document.name?.substringAfterLast('.', "")?.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            else -> "audio/*"
        }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(document.uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }
}

private fun shareCompletedFile(
    context: Context,
    item: DownloadItemUi,
) {
    val document = findCompletedDocument(context, item) ?: return
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, document.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "分享音乐",
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadingList(
    list: List<DownloadItemUi>,
    viewModel: DownloadViewModel,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onCancel: (Long) -> Unit,
) {
    val context = LocalContext.current
    Column {
        Text(
            "下载中 ${list.count {
                it.status == TaskStatus.DOWNLOADING
            }} · 排队 ${list.count { it.status == TaskStatus.QUEUED }} · 已暂停 ${list.count { it.status == TaskStatus.PAUSED }}",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无下载任务") }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { item ->
                DownloadingItem(
                    item = item,
                    modifier = Modifier.animateItem(),
                    selectionMode = selectionMode,
                    isSelected = item.id in selectedIds,
                    onToggleSelection = { onToggleSelection(item.id) },
                    onRetry = { viewModel.retryDownload(context, item) },
                    onPause = { viewModel.pauseDownload(context, item) },
                    onResumeDownload = { viewModel.resumeDownload(context, item) },
                    onDelete = { onCancel(item.id) },
                    onLongClick = { if (!selectionMode) onToggleSelection(item.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadingItem(
    item: DownloadItemUi,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResumeDownload: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    val context = LocalContext.current
    val animatedProgress by animateFloatAsState(
        targetValue = (item.telemetry?.stageProgress ?: item.progress).coerceIn(0, 100) / 100f,
        label = "ProgressAnimation",
    )

    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .animateContentSize()
                .clip(RoundedCornerShape(12.dp))
                .semantics { if (selectionMode) selected = isSelected }
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            onToggleSelection()
                        } else {
                            when (item.status) {
                                TaskStatus.QUEUED, TaskStatus.DOWNLOADING -> onPause()
                                TaskStatus.PAUSED -> onResumeDownload()
                                TaskStatus.FAILED -> onRetry()
                                TaskStatus.PAUSING, TaskStatus.CANCELLING, TaskStatus.COMPLETED -> Unit
                            }
                        }
                    },
                    onLongClick = onLongClick,
                ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selectionMode && isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面
            AsyncImage(
                model = item.music.album.picUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 中间内容
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 80.dp)
                        .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 标题
                Text(
                    text = item.music.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 艺术家
                Text(
                    text = item.music.artists.joinToString("/") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (item.status == TaskStatus.FAILED) {
                    Text(
                        text = "下载失败: ${item.failureReason ?: "未知错误"}（点此复制）",
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        onToggleSelection()
                                    } else {
                                        SystemUtils.copyToClipboard(
                                            context,
                                            "DownloadError",
                                            item.failureReason ?: "未知错误",
                                        )
                                    }
                                },
                                onLongClick = onLongClick,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(downloadTaskStatusText(item), style = MaterialTheme.typography.bodySmall)
                    downloadTransferText(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (item.status == TaskStatus.DOWNLOADING) {
                        Spacer(Modifier.height(6.dp))
                        if (item.telemetry?.stageProgress != null) {
                            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !selectionMode && item.status.canCancel(),
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.delete_24px),
                        contentDescription = "取消任务",
                        tint = MaterialTheme.colorScheme.onSurface,
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
    filesByUri: Map<String, LocalMusicFile>,
    onDelete: (DownloadItemUi) -> Unit,
    onClick: (DownloadItemUi) -> Unit,
) {
    // 倒序显示，最新的在上面
    val reversedList = remember(list) { list.asReversed() }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(reversedList, key = { it.id }) { item ->
            CompletedItem(
                item = item,
                fileStatus = completedFileStatus(item, filesByUri),
                modifier = Modifier.animateItem(),
                onClick = { onClick(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
fun CompletedItem(
    item: DownloadItemUi,
    fileStatus: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {},
                ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面
            AsyncImage(
                model = item.music.album.picUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 中间内容
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 标题
                Text(
                    text = item.music.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 艺术家
                Text(
                    text = item.music.artists.joinToString("/") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = fileStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_24px),
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
