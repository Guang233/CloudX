package com.guang.cloudx.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.BuildConfig
import com.guang.cloudx.logic.utils.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyAppTheme {
                SettingsScreen()
            }
        }
    }

    @Composable
    fun MyAppTheme(
        dynamicColor: Boolean = true,
        darkTheme: Boolean = isSystemInDarkTheme(),
        content: @Composable () -> Unit
    ) {
        val colorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            }

            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }

        MaterialTheme(
            colorScheme = colorScheme,
//            typography = Typography,
//            shapes = Shapes,
            content = content
        )
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen() {
        var lrcEnabled by rememberSaveable { mutableStateOf(prefs.getIsSaveLrc()) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item {
                    Text(
                        "应用",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp, 6.dp, 16.dp, 6.dp),
                    color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    MenuListItem(
                        icon = Icons.Outlined.Palette,
                        title = "主题颜色",
                        options = listOf("跟随系统", "青色", "粉色"),
                        selectedOption = "跟随系统",
                        onOptionSelected = {
                            scope.launch {
                                snackbarHostState.showSnackbar("前面的区域，以后再来探索吧")
                            }
                        }
                    )
                }

                item {
                    MenuListItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "深色模式",
                        options = listOf("跟随系统", "启用", "关闭"),
                        selectedOption = "跟随系统",
                        onOptionSelected = {
                            scope.launch {
                                snackbarHostState.showSnackbar("前面的区域，以后再来探索吧")
                            }
                        }
                    )
                }

                item {
                    Text(
                        "下载",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 6.dp, 16.dp, 6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    var pickedUri by remember { mutableStateOf<Uri?>(null) }

                    LaunchedEffect(Unit) {
                        prefs.getSafUri()?.let {
                            pickedUri = Uri.parse(it)
                        }
                    }

                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        if (uri != null) {
                            val takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                            prefs.putSafUri(uri.toString())
                            pickedUri = uri
                        }
                    }

                    val path = pickedUri?.let { tryResolveAbsolutePathFromTreeUri(context, it) } ?: "未选择"
                    ActionListItem(
                        icon = Icons.Outlined.Folder,
                        title = "音乐文件保存路径",
                        description = path,
                        onClick = {
                            launcher.launch(null)
                        },
                        onLongClick = {
                            scope.launch {
                                SystemUtils.copyToClipboard(
                                    context,
                                    "downloadPath",
                                    path
                                )
                            }
                        }
                    )
                }

                item {
                    SwitchListItem(
                        icon = Icons.Outlined.Lyrics,
                        title = "额外导出歌词",
                        checked = lrcEnabled,
                        description = "在下载目录额外导出.lrc歌词文件",
                        onCheckedChange = { checked ->
                            lrcEnabled = checked
                            prefs.putIsSaveLrc(checked)
                        }
                    )
                }

                item {
                    MenuListItem(
                        icon = Icons.Outlined.Equalizer,
                        title = "默认下载音质",
                        options = listOf("标准", "极高", "无损", "Hi-res", "上次选择"),
                        selectedOption = if (prefs.getIsAutoLevel())
                            "上次选择"
                        else when (prefs.getMusicLevel()) {
                            "standard" -> "标准"
                            "exhigh" -> "极高"
                            "lossless" -> "无损"
                            "hires" -> "标准"
                            else -> "标准"
                        },
                        onOptionSelected = { selectedOption ->
                            if (selectedOption != "上次选择") {
                                val level = when (selectedOption) {
                                    "标准" -> "standard"
                                    "极高" -> "exhigh"
                                    "无损" -> "lossless"
                                    "Hi-res" -> "hires"
                                    else -> "exhigh"
                                }
                                prefs.putIsAutoLevel(false)
                                prefs.putMusicLevel(level)
                            } else
                                prefs.putIsAutoLevel(true)
                        }
                    )
                }

                item {
                    ActionListItem(
                        icon = Icons.Outlined.Edit,
                        title = "文件命名规则",
                        description = "修改下载的歌曲文件名命名规则",
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("前面的区域，以后再来探索吧")
                            }
                        }
                    )
                }

                item {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 6.dp, 16.dp, 6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    ActionListItem(
                        icon = Icons.Outlined.Apps,
                        title = "应用版本",
                        description = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        onClick = {
                            scope.launch {
                                SystemUtils.copyToClipboard(context, "version", BuildConfig.VERSION_NAME)
                            }
                        }
                    )
                }

                item {
                    ActionListItem(
                        icon = Icons.Outlined.Link,
                        title = "GitHub",
                        description = "https://github.com/Guang233/CloudX",
                        onClick = {}, // TODO: 跳转浏览器
                        onLongClick = {
                            scope.launch {
                                SystemUtils.copyToClipboard(context, "github", "https://github.com/Guang233/CloudX")
                            }
                        }
                    )
                }
                
                item { 
                    ActionListItem(
                        icon = Icons.Outlined.CleaningServices,
                        title = "清除缓存",
                        description = "清除歌曲封面缓存",
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                Glide.get(context).clearDiskCache()
                                withContext(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("已清理")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun SwitchListItem(
        icon: ImageVector,
        title: String,
        description: String? = null,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        ListItem(
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .wrapContentSize(align = Alignment.Center)
                )
            },
            headlineContent = { Text(title) },
            supportingContent = description?.let { { Text(it) } },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    interactionSource = interactionSource
                )
            },
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource
                ) {
                    onCheckedChange(!checked)
                }
        )
    }

    @Composable
    fun MenuListItem(
        icon: ImageVector,
        title: String,
        options: List<String>,
        selectedOption: String,
        onOptionSelected: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        var currentOption by remember { mutableStateOf(selectedOption) }

        ListItem(
            modifier = Modifier.clickable { expanded = true },
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .wrapContentSize(align = Alignment.Center)
                )
            },
            headlineContent = { Text(title) },
            supportingContent = { Text(currentOption) },
            trailingContent = {
                Box {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onOptionSelected(option)
                                    currentOption = option
                                    expanded = false
                                },
                                modifier = Modifier.background(
                                    if (option == currentOption)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else
                                        Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun ActionListItem(
        icon: ImageVector,
        title: String,
        description: String? = null,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .wrapContentSize(align = Alignment.Center)
                )
            },
            headlineContent = { Text(title) },
            supportingContent = description?.let { { Text(it, maxLines = 1,  overflow = TextOverflow.Ellipsis) } },
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
    }

    private fun tryResolveAbsolutePathFromTreeUri(context: Context, treeUri: Uri): String? {
        // 文档 id 例如 "primary:Music/myfolder" 或 "0123-4567:some/folder"
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        // 常见 primary 情况
        if (docId.startsWith("primary:")) {
            val rel = docId.removePrefix("primary:")
            return "/storage/emulated/0/$rel"
        }

        // 如果是 "volumeId:rest" 形式，尝试构造 /storage/<volumeId>/rest
        val idx = docId.indexOf(':')
        if (idx > 0) {
            val volumeId = docId.substring(0, idx)
            val rest = docId.substring(idx + 1)
            // 这在某些设备上可能位于 /storage/<volumeId>/<rest>
            val candidate = "/storage/$volumeId/$rest"
            // 简单校验：文件存在则返回
            val f = java.io.File(candidate)
            if (f.exists()) return candidate
        }

        // 其他情况无法解析
        return null
    }
}