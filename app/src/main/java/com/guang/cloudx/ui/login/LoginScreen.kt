package com.guang.cloudx.ui.login

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guang.cloudx.logic.model.UserData
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pages = Destination.entries
    val pagerState = rememberPagerState(
        initialPage = Destination.COOKIES.ordinal,
        pageCount = { pages.size }
    )
    val context = LocalContext.current
    val prefs = remember { SharedPreferencesUtils(context) }

    var pagerScrollEnabled by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage
            ) {
                Destination.entries.forEachIndexed { index, destination ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(destination.label)
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = pagerScrollEnabled
            ) { page ->
                when (pages[page]) {
                    Destination.COOKIES -> CookiesScreen(
                        prefs = prefs,
                        onSave = onLoginSuccess
                    )
                    Destination.PHONE_NUMBER -> PhoneNumberScreen(
                        viewModel = viewModel,
                        prefs = prefs,
                        onLoginSuccess = onLoginSuccess,
                        showSnackBar = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    )
                    Destination.WEB -> WebScreen(
                        prefs = prefs,
                        onTouchEvent = {
                            pagerScrollEnabled = !it
                        }
                    )
                }
            }
        }
    }
}

enum class Destination(val route: String, val label: String) {
    COOKIES("cookies", "Cookies"),
    PHONE_NUMBER("phone-numbers", "手机号(推荐)"),
    WEB("web", "网页(测试)")
}

@Composable
fun CookiesScreen(
    prefs: SharedPreferencesUtils,
    onSave: () -> Unit
) {
    var userId by rememberSaveable { mutableStateOf(prefs.getUserId()) }
    var cookies by rememberSaveable { mutableStateOf(prefs.getCookie()) }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxSize()
    ) {
        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it },
            label = { Text("用户ID(用于获取头像和昵称)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        TextField(
            value = cookies,
            onValueChange = { cookies = it },
            label = { Text("Cookies...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            minLines = 5,
            maxLines = 12
        )

        Button(
            onClick = {
                prefs.putUserId(userId)
                prefs.putCookie(cookies)
                onSave()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) { Text("保存") }
    }
}

@Composable
fun PhoneNumberScreen(
    viewModel: LoginViewModel,
    prefs: SharedPreferencesUtils,
    onLoginSuccess: () -> Unit,
    showSnackBar: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val captchaState = viewModel.captchaState
    val loginState = viewModel.loginState

    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxSize()
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            isError = isError && !isFocused,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        isError = phoneNumber.length != 11 && phoneNumber.isNotEmpty()
                    }
                    isFocused = focusState.isFocused
                },
            label = { Text("手机号") },
            leadingIcon = { Text("+86") },
            trailingIcon = { if (isError && !isFocused) Icon(imageVector = Icons.Outlined.Error, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            )
        )
        if (isError && !isFocused) {
            Text(
                text = "请输入正确的手机号",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("验证码") },
            trailingIcon = {
                Button(
                    onClick = {
                        if (phoneNumber.length == 11)
                            viewModel.sendCaptcha(phoneNumber)
                        else showSnackBar("请输入正确的手机号")
                    },
                    modifier = Modifier.padding(end = 4.dp)
                ) { Text("获取") }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
        )

        LaunchedEffect(captchaState) {
            when (captchaState) {
                is LoginViewModel.UiState.Error -> {
                    showSnackBar(captchaState.message)
                }
                LoginViewModel.UiState.Idle -> {}
                LoginViewModel.UiState.Loading -> {}
                is LoginViewModel.UiState.Success<*> -> {
                    showSnackBar("验证码发送成功")
                }
            }
        }

        Button(
            onClick = { if (otp.length == 4) viewModel.login(phoneNumber, otp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) { Text("登录") }

        LaunchedEffect(loginState) {
            when (loginState) {
                is LoginViewModel.UiState.Error -> {
                    showSnackBar(loginState.message)
                }

                LoginViewModel.UiState.Idle -> {}
                LoginViewModel.UiState.Loading -> {
                    showSnackBar("正在登录")
                }

                is LoginViewModel.UiState.Success<*> -> {
                    val data = loginState.data as UserData
                    prefs.putUserId(data.id)
                    prefs.putCookie("os=pc; osver=Microsoft-Windows-11-Home-China-build-26100-64bit; appver=3.1.23.204750; channel=netease; mode=83NN; MUSIC_U=${data.token}")
                    onLoginSuccess()
                }
            }
        }

        Text(
            text = "注意：请勿短时间内多次请求验证码或重复登录，容易冻结账号",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebScreen(
    prefs: SharedPreferencesUtils,
    onTouchEvent: (Boolean) -> Unit
) {
    var webUrl by remember { mutableStateOf("https://music.163.com/") }

    if (prefs.getCookie() != "") {
        Text(
            text = "请先退出登录后再进行网页登录",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.Center)
        )
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(it).apply {
                    settings.apply {
                        javaScriptEnabled = true

                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/114.0.0.0 Safari/537.36"

                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)

                        cacheMode = WebSettings.LOAD_DEFAULT
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    setOnTouchListener { _, event: MotionEvent ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE -> onTouchEvent(true)

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> onTouchEvent(false)
                        }
                        false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null) {
                                webUrl = url
                                val cookie = CookieManager.getInstance().getCookie(url)
                                if (cookie != null) {
                                    prefs.putCookie(cookie)
                                }
                            }
                        }
                    }

                    loadUrl(webUrl)
                }
            },
            update = { webView ->
                if (webView.url != webUrl) {
                    webView.loadUrl(webUrl)
                }
            }
        )
    }
}
