package com.wliky.melody.feature.auth

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.wliky.melody.core.model.ApiMode

/**
 * 登录页：三条互为兜底的通路。
 *
 * 1. **手机验证码**（默认）—— 不碰密码，国内网络下最稳。
 * 2. **扫码** —— 不想收短信时用。
 * 3. **Cookie** —— 前两条都被风控挡住（403 / 8821）时的终极兜底，
 *    直接复用浏览器里已有的登录态。
 *
 * 二维码在本地用 zxing 生成，不需要相机权限，也不会把二维码内容发往任何第三方。
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val method by viewModel.method.collectAsStateWithLifecycle()
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()
    val cookieState by viewModel.cookieState.collectAsStateWithLifecycle()
    val apiMode by viewModel.apiMode.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is LoginViewModel.LoginState.Success) onLoggedIn()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(text = "登录", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "登录后即可同步你的歌单、收藏与听歌记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        if (apiMode == ApiMode.MOCK) {
            DemoModeNotice(onBack = onBack)
            return@Column
        }

        MethodSwitcher(current = method, onSelect = viewModel::switchMethod)
        Spacer(Modifier.height(20.dp))

        when (method) {
            LoginViewModel.Method.PHONE -> PhonePanel(
                state = phoneState,
                onPhoneChange = viewModel::onPhoneChange,
                onCaptchaChange = viewModel::onCaptchaChange,
                onSendCaptcha = viewModel::sendCaptcha,
                onSubmit = viewModel::submitPhone,
            )

            LoginViewModel.Method.QR -> QrPanel(
                state = state,
                onRefresh = viewModel::refresh,
                onSwitchToCookie = { viewModel.switchMethod(LoginViewModel.Method.COOKIE) },
                onSwitchToPhone = { viewModel.switchMethod(LoginViewModel.Method.PHONE) },
            )

            LoginViewModel.Method.COOKIE -> CookiePanel(
                state = cookieState,
                onInputChange = viewModel::onCookieInputChange,
                onSubmit = viewModel::submitCookie,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "登录凭据只保存在本机的 Keystore 加密存储中，不会写入日志，也不会上传到任何第三方服务。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 登录方式切换（胶囊分段）。 */
@Composable
private fun MethodSwitcher(
    current: LoginViewModel.Method,
    onSelect: (LoginViewModel.Method) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        LoginViewModel.Method.entries.forEach { entry ->
            val selected = entry == current
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun QrPanel(
    state: LoginViewModel.LoginState,
    onRefresh: () -> Unit,
    onSwitchToCookie: () -> Unit,
    onSwitchToPhone: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            is LoginViewModel.LoginState.QrReady -> {
                QrCodeImage(content = state.qr.content)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "在手机 App 中：设置 → 扫一扫",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is LoginViewModel.LoginState.Loading -> {
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "正在获取二维码…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is LoginViewModel.LoginState.Expired -> NoticeCard(
                title = "二维码已过期",
                message = "重新获取一个二维码即可继续。",
                tone = NoticeTone.NEUTRAL,
            )

            is LoginViewModel.LoginState.RiskControlled -> NoticeCard(
                title = "扫码登录被风控拦截",
                message = state.message,
                tone = NoticeTone.WARNING,
                actionLabel = "改用验证码登录",
                onAction = onSwitchToPhone,
            )

            is LoginViewModel.LoginState.Failed -> NoticeCard(
                title = "获取二维码失败",
                message = state.message,
                tone = NoticeTone.WARNING,
                actionLabel = "改用验证码登录",
                onAction = onSwitchToPhone,
            )

            is LoginViewModel.LoginState.Success -> {
                Text(text = "登录成功", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state is LoginViewModel.LoginState.Expired) "刷新二维码" else "重新获取")
            }
            TextButton(onClick = onSwitchToCookie) { Text("改用 Cookie") }
        }
    }
}

/**
 * 手机验证码登录面板。
 *
 * 国内网络环境下这是最稳的一条路：不走二维码、不碰密码，风控也最松。
 */
@Composable
private fun PhonePanel(
    state: LoginViewModel.PhoneState,
    onPhoneChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onSendCaptcha: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            singleLine = true,
            label = { Text("手机号") },
            placeholder = { Text("11 位中国大陆手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.captcha,
            onValueChange = onCaptchaChange,
            singleLine = true,
            label = { Text("短信验证码") },
            placeholder = { Text("收到的 4~6 位数字") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            trailingIcon = {
                TextButton(onClick = onSendCaptcha, enabled = state.canSend) {
                    Text(
                        text = when {
                            state.sending -> "发送中…"
                            state.countdown > 0 -> "${state.countdown}s"
                            else -> "获取验证码"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )

        if (state.notice != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("正在登录…")
            } else {
                Text("登录", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "验证码由网易云音乐直接下发到你的手机；本客户端只把它转发给接口，不留存任何验证码。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Cookie 登录面板。
 *
 * 用户多数情况下只想复制 `MUSIC_U` 的值，但整段 Cookie 也接受，
 * 后端会统一规整（见 CookieParser）。
 */
@Composable
private fun CookiePanel(
    state: LoginViewModel.CookieState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "怎么拿到 Cookie",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "在电脑浏览器登录 music.163.com → 按 F12 → Application（或存储）\n" +
                        "→ Cookies → 找到 MUSIC_U，复制它的值粘贴到下面即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text("MUSIC_U 或整段 Cookie") },
            placeholder = { Text("MUSIC_U=xxxxx; __csrf=yyy") },
            minLines = 3,
            maxLines = 6,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            trailingIcon = {
                IconButton(
                    onClick = {
                        val text = clipboard.getText()?.text
                        if (!text.isNullOrBlank()) onInputChange(text)
                    },
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "从剪贴板粘贴")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onSubmit,
            enabled = !state.submitting && state.input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("正在验证…")
            } else {
                Text("登录", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "只做一次账号信息校验，校验通过后凭据会加密保存在本机。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class NoticeTone { NEUTRAL, WARNING }

@Composable
private fun NoticeCard(
    title: String,
    message: String,
    tone: NoticeTone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container = when (tone) {
        NoticeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerLow
        NoticeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer = when (tone) {
        NoticeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        NoticeTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = onContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DemoModeNotice(onBack: () -> Unit) {
    NoticeCard(
        title = "当前是演示模式",
        message = "演示模式不需要登录，所有内容都是内置示例数据。\n" +
            "想登录自己的账号，请先到「设置 → 数据源」切到直连模式或自建服务。",
        tone = NoticeTone.NEUTRAL,
    )
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onBack) { Text("返回") }
}

@Composable
private fun QrCodeImage(content: String) {
    val bitmap = remember(content) { generateQrBitmap(content) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier
            .size(248.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "登录二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxSize(),
                )
            } else {
                Text(
                    text = "二维码生成失败，请点击下方重新获取",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int = 640): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val offset = y * size
        for (x in 0 until size) {
            pixels[offset + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
