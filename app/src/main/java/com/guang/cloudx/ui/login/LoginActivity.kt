package com.guang.cloudx.ui.login

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (pages[page]) {
                        Destination.COOKIES -> CookiesScreen()
                        Destination.PHONE_NUMBER -> PhoneNumberScreen { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    }
                }
            }
        }
    }

    enum class Destination(val route: String, val label: String) {
        COOKIES("cookies", "Cookies"),
        PHONE_NUMBER("phone-numbers", "手机验证码")
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
                maxLines = 10
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

            when (captchaState) {
                is LoginViewModel.UiState.Error -> { showSnackBar(captchaState.message) }
                LoginViewModel.UiState.Idle -> {}
                LoginViewModel.UiState.Loading -> {}
                is LoginViewModel.UiState.Success<*> -> { showSnackBar("验证码发送成功") }
            }

            Button(
                onClick = { if (otp.length == 4) viewModel.login(phoneNumber, otp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) { Text("登录") }

            when (loginState) {
                is LoginViewModel.UiState.Error -> { showSnackBar(loginState.message) }
                LoginViewModel.UiState.Idle -> {}
                LoginViewModel.UiState.Loading -> { showSnackBar("正在登录") }
                is LoginViewModel.UiState.Success<*> -> {
                    val data = loginState.data as UserData
                    prefs.putUserId(data.id)
                    prefs.putCookie("os=pc; osver=Microsoft-Windows-11-Home-China-build-26100-64bit; appver=3.1.11.203994; channel=netease; mode=83NN; MUSIC_U=${data.token}")
                    finish()
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
}