package com.wliky.melody.feature.auth

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.network.CookieParser

/**
 * 登录页（v0.4.0-preview.3+）。
 *
 * 三层结构，用内部 [LoginStage] 切换（不新增导航目的地）：
 *
 *  1. [LoginStage.ENTRY]   —— 登录入口：Melody Logo + 标题 + 「扫码登录」主按钮 +
 *                             「Cookie 登录」次级入口。符合产品规范 §12（扫码为主，Cookie 兜底）。
 *  2. [LoginStage.QR]      —— 全屏 WebView，加载网易云官方登录页，扫码 / 验证码 / 邮箱
 *                             都由网易自己处理，成功后从 CookieManager 抓带 MUSIC_U 的 Cookie。
 *  3. [LoginStage.COOKIE]  —— 独立 Cookie 登录页：粘贴浏览器里的 MUSIC_U 登录。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pageLoaded by viewModel.pageLoaded.collectAsStateWithLifecycle()
    var stage by remember { mutableStateOf(LoginStage.ENTRY) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var cookieInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    // 登录成功：退出登录页（不管 profile 是否已拉取，MUSIC_U 落盘即算登录成功）。
    LaunchedEffect(state) {
        if (state is LoginViewModel.LoginState.Success) onLoggedIn()
    }

    when (stage) {
        LoginStage.ENTRY -> {
            LoginEntryScreen(
                onBack = onBack,
                onScanLogin = {
                    viewModel.consumeError()
                    stage = LoginStage.QR
                },
                onCookieLogin = {
                    viewModel.consumeError()
                    stage = LoginStage.COOKIE
                },
            )
        }

        LoginStage.QR -> {
            // 从二维码页返回入口页。
            BackHandler { stage = LoginStage.ENTRY }

            Column(modifier = modifier.fillMaxSize()) {
                LoginTopBar(
                    title = "扫码登录",
                    onBack = { stage = LoginStage.ENTRY },
                    onRefresh = { webView?.reload() },
                )

                LoginStatusBar(
                    state = state,
                    pageLoaded = pageLoaded,
                    onDismissError = viewModel::consumeError,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    WebViewLogin(
                        onWebViewReady = { webView = it },
                        onPageStarted = viewModel::onPageStarted,
                        onPageFinished = { wv ->
                            viewModel.onPageLoaded()
                            val cookie = collectWebViewCookies(wv)
                            viewModel.onWebViewCookies(cookie)
                        },
                    )

                    val failed = state as? LoginViewModel.LoginState.Failed
                    if (failed != null && !failed.recoverable) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = failed.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = viewModel::consumeError) {
                                    Text("重新登录")
                                }
                            }
                        }
                    }
                }

                // 兜底入口：扫码不便时可直接切到 Cookie 登录。
                TextButton(
                    onClick = {
                        viewModel.consumeError()
                        stage = LoginStage.COOKIE
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 4.dp),
                ) {
                    Text("扫码不方便？使用 Cookie 登录")
                }

                LoginPrivacyHint()
            }
        }

        LoginStage.COOKIE -> {
            BackHandler { stage = LoginStage.ENTRY }

            CookieLoginScreen(
                state = state,
                input = cookieInput,
                onInputChange = {
                    cookieInput = it
                    viewModel.consumeError()
                },
                onPaste = {
                    clipboard.getText()?.text?.let { cookieInput = it }
                },
                onSubmit = { viewModel.submitManualCookie(cookieInput) },
                onBack = { stage = LoginStage.ENTRY },
            )
        }
    }
}

/** 登录页的三个子页面。 */
private enum class LoginStage { ENTRY, QR, COOKIE }

/**
 * 登录入口页（默认落地页）：极简，符合产品规范 §12。
 * 主视觉是 Melody Logo + 一句引导，主按钮「扫码登录」，次级「Cookie 登录」。
 */
@Composable
private fun LoginEntryScreen(
    onBack: () -> Unit,
    onScanLogin: () -> Unit,
    onCookieLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 顶栏：返回。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
        }

        // 主体：Logo + 引导语 + 登录按钮。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "登录网易云音乐",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "登录后即可播放、收藏与同步你的音乐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // 底部按钮区。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = onScanLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("扫码登录", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCookieLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Cookie, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cookie 登录", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * 独立 Cookie 登录页：粘贴浏览器里复制出来的 Cookie（核心是 MUSIC_U）。
 * 独立成页，替代早先藏在「高级选项」折叠面板里的兜底入口。
 */
@Composable
private fun CookieLoginScreen(
    state: LoginViewModel.LoginState,
    input: String,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LoginTopBar(
            title = "Cookie 登录",
            onBack = onBack,
            onRefresh = null,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "粘贴你的网易云登录 Cookie",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "在浏览器登录 music.163.com 后，复制包含 MUSIC_U 的 Cookie 到这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("MUSIC_U=xxxxx; __csrf=yyy") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(12.dp))

            // 错误提示（仅当失败且非可恢复时展示）。
            val failed = state as? LoginViewModel.LoginState.Failed
            if (failed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = failed.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSubmit,
                enabled = input.isNotBlank() && state !is LoginViewModel.LoginState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state is LoginViewModel.LoginState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onPaste,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("从剪贴板粘贴")
            }
        }
    }
}

@Composable
private fun LoginTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Cookie, contentDescription = "刷新")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun LoginStatusBar(
    state: LoginViewModel.LoginState,
    pageLoaded: Boolean,
    onDismissError: () -> Unit,
) {
    Surface(
        color = when (state) {
            is LoginViewModel.LoginState.Success -> MaterialTheme.colorScheme.primaryContainer
            is LoginViewModel.LoginState.Failed -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                LoginViewModel.LoginState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在打开网易云登录页…",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                LoginViewModel.LoginState.Ready -> {
                    Icon(
                        Icons.Rounded.Cookie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (pageLoaded) "请在下方登录（扫码 / 验证码 / 邮箱都可以）" else "页面加载中…",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                is LoginViewModel.LoginState.Failed -> {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissError) {
                        Text("重试", style = MaterialTheme.typography.labelMedium)
                    }
                }
                LoginViewModel.LoginState.Success -> {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "登录成功",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLogin(
    onWebViewReady: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: (WebView) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile; rv:124.0) " +
                        "Gecko/124.0 Firefox/124.0"
                }
                onWebViewReady(this)
            }.also { wv ->
                @Suppress("DEPRECATION")
                CookieManager.getInstance().run {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(wv, true)
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        onPageStarted()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view != null) onPageFinished(view)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val target = request?.url?.toString().orEmpty()
                        if (view != null && (target.contains("y.music.163.com") || target.contains("music.163.com/#/"))) {
                            view.postDelayed({ onPageFinished(view) }, 300L)
                        }
                        return false
                    }
                }
                wv.loadUrl(LOGIN_URL)
            }
        },
        update = { /* state 变化由 ViewModel 自身读取 CookieManager 触发 */ },
    )
}

/** 从 WebView 提取所有 Cookie（含 music.163.com / y.music.163.com），合并去重。 */
private fun collectWebViewCookies(webView: WebView): String? {
    val cookieManager = CookieManager.getInstance()
    cookieManager.flush()
    val urls = linkedSetOf(LOGIN_URL, "https://music.163.com", "https://y.music.163.com")
    val pairs = LinkedHashMap<String, String>()
    urls.forEach { url ->
        val raw = runCatching { cookieManager.getCookie(url) }.getOrNull() ?: return@forEach
        CookieParser.parsePairs(raw).forEach { (k, v) -> pairs[k] = v }
    }
    if (pairs.isEmpty()) return null
    return CookieParser.join(pairs)
}

@Composable
private fun LoginPrivacyHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "登录页是网易云官方地址，登录凭据只保存在本机 Keystore 加密存储中，不会上传到任何第三方。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LOGIN_URL = "https://music.163.com/m/login"
