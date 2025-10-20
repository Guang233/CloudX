package com.guang.cloudx.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.logic.model.UserData
import com.guang.cloudx.ui.ui.theme.CloudXTheme
import kotlinx.coroutines.launch

// 嗯，对，总之，一开始是xml，后来用 compose 重构，所以，嗯，这一坨先拉这
class LoginActivity : BaseActivity() {
//    private val cookieEditText by lazy { findViewById<EditText>(R.id.cookieEditText) }
//    private val userIdEditText by lazy { findViewById<EditText>(R.id.userIdEditText) }
//    private val button by lazy { findViewById<MaterialButton>(R.id.saveButton) }
//
//    private val topAppBar by lazy { findViewById<MaterialToolbar>(R.id.topAppBar) }


     private val viewModel by viewModels<LoginViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
//        setContentView(R.layout.activity_login)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        userIdEditText.setText(prefs.getUserId())
//        cookieEditText.setText(prefs.getCookie())
//
//        topAppBar.setNavigationOnClickListener { finish() }
//        button.setOnClickListener { _ ->
//            prefs.putUserId(userIdEditText.text.toString())
//            prefs.putCookie(cookieEditText.text.toString())
//            finish()
//        }
        setContent {
            CloudXTheme {
                LoginScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LoginScreen() {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val pages = Destination.entries
        val pagerState = rememberPagerState(
            initialPage = Destination.COOKIES.ordinal,
            pageCount = { pages.size }
        )

        var pagerScrollEnabled by remember { mutableStateOf(true) }

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopAppBar(
                    title = { Text("登录") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
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
                        Destination.COOKIES -> CookiesScreen()
                        Destination.PHONE_NUMBER -> PhoneNumberScreen { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                        Destination.WEB -> WebScreen {
                            pagerScrollEnabled = !it
                        }
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
    fun CookiesScreen() {
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
                    finish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) { Text("保存") }
        }
    }

    @Composable
    fun PhoneNumberScreen(showSnackBar: (String) -> Unit) {
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
                        prefs.putCookie("os=pc; osver=Microsoft-Windows-11-Home-China-build-26100-64bit; appver=3.1.11.203994; channel=netease; mode=83NN; MUSIC_U=${data.token}")
                        finish()
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
    fun WebScreen(onTouchEvent: (Boolean) -> Unit) {
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
}