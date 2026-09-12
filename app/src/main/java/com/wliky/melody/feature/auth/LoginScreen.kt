package com.wliky.melody.feature.auth

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.network.CookieParser

/**
 * 登录页（v0.3.0-preview.3+）。
 *
 * 主要是一张全屏 WebView，加载网易云官方登录页 `https://music.163.com/m/login`，
 * 用户扫码 / 验证码 / 邮箱登录都由网易自己处理；登录成功后从 `CookieManager`
 * 拿到带 `MUSIC_U` 的完整 Cookie，提交给 AuthRepository。
 *
 * 折叠的高级选项里放了「粘贴 Cookie」的兜底入口：在浏览器已经登录好、
 * WebView 加载又比较慢时用。
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
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var advancedInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    // 监听登录成功。
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is LoginViewModel.LoginState.Success) onLoggedIn()
    }

    BackHandler(enabled = showAdvanced) { showAdvanced = false }

    Column(modifier = modifier.fillMaxSize()) {
        LoginTopBar(
            onBack = onBack,
            webView = webView,
        )

        // 顶部状态条：根据 state 展示不同文案。
        LoginStatusBar(
            state = state,
            pageLoaded = pageLoaded,
            onDismissError = viewModel::consumeError,
        )

        // WebView 主体。
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WebViewLogin(
                onWebViewReady = { webView = it },
                onPageStarted = viewModel::onPageStarted,
                onPageFinished = { wv ->
                    viewModel.onPageLoaded()
                    // 官方登录页会在 y.music.163.com 上携带 Set-Cookie。
                    // 监听页面 URL 变化不可靠，每次加载完都尝试一次更稳。
                    val cookie = collectWebViewCookies(wv)
                    viewModel.onWebViewCookies(cookie)
                },
            )

            // 错误时盖一层提示。
            if (state is LoginViewModel.LoginState.Failed) {
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
                            text = (state as LoginViewModel.LoginState.Failed).message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::consumeError) {
                            Text("我知道了")
                        }
                    }
                }
            }
        }

        // 折叠的高级选项（Cookie 兜底）。
        AdvancedPanel(
            expanded = showAdvanced,
            onToggle = { showAdvanced = !showAdvanced },
            input = advancedInput,
            onInputChange = {
                advancedInput = it
                viewModel.consumeError()
            },
            onPasteFromClipboard = {
                clipboard.getText()?.text?.let { advancedInput = it }
            },
            onSubmit = { viewModel.submitManualCookie(advancedInput) },
        )

        LoginPrivacyHint()
    }
}

@Composable
private fun LoginTopBar(
    onBack: () -> Unit,
    webView: WebView?,
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
                text = "登录网易云",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { webView?.reload() },
                enabled = webView != null,
            ) {
                Icon(
                    Icons.Rounded.Cookie,
                    contentDescription = "刷新",
                )
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
                    // WebView 默认会拒掉第三方 Cookie —— 网易在 y.music.163.com 上写 Cookie
                    // 必须打开，否则登录态根本拿不到。
                    @Suppress("DEPRECATION")
                    run {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this@apply, true)
                    }
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile; rv:124.0) " +
                        "Gecko/124.0 Firefox/124.0"
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?) {
                        onPageStarted()
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view != null) onPageFinished(view)
                    }
                    // 不拦截 shouldOverrideUrlLoading —— 让网易自己的跳转正常进行
                    // （扫码登录成功后官方页会跳到 y.music.163.com/m/）。
                }
                loadUrl(LOGIN_URL)
                onWebViewReady(this)
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
private fun AdvancedPanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Cookie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "高级：粘贴 MUSIC_U 登录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        label = { Text("MUSIC_U 或完整 Cookie") },
                        placeholder = { Text("MUSIC_U=xxxxx; __csrf=yyy") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        TextButton(onClick = onPasteFromClipboard) {
                            Text("从剪贴板粘贴")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = onSubmit,
                            enabled = input.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        ) {
                            Text("用这个 Cookie 登录")
                        }
                    }
                }
            }
        }
    }
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

@Suppress("unused")
private fun stableId(): String = UUID.randomUUID().toString()