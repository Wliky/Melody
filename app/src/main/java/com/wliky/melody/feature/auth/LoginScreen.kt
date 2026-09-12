package com.wliky.melody.feature.auth

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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Phone
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 登录页（v0.4.0-preview.5+）：两种原生登录方式。
 *
 * 用内部 [LoginStage] 切换（不新增导航目的地）：
 *  - ENTRY  —— 两个并列入口：手机号登录 / Cookie 登录
 *  - PHONE  —— 手机号 + 短信验证码
 *  - COOKIE —— 粘贴 MUSIC_U
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var stage by remember { mutableStateOf(LoginStage.ENTRY) }

    // 登录成功：退出登录页。
    LaunchedEffect(state) {
        if (state is LoginViewModel.LoginState.Success) onLoggedIn()
    }

    when (stage) {
        LoginStage.ENTRY -> {
            LoginEntryScreen(
                onBack = onBack,
                onPhoneLogin = { stage = LoginStage.PHONE },
                onCookieLogin = { stage = LoginStage.COOKIE },
            )
        }

        LoginStage.PHONE -> {
            PhoneLoginScreen(
                viewModel = viewModel,
                onBack = { stage = LoginStage.ENTRY },
            )
        }

        LoginStage.COOKIE -> {
            CookieLoginScreen(
                viewModel = viewModel,
                onBack = { stage = LoginStage.ENTRY },
            )
        }
    }
}

private enum class LoginStage { ENTRY, PHONE, COOKIE }

// ------------------------------------------------------------------ 入口页

@Composable
private fun LoginEntryScreen(
    onBack: () -> Unit,
    onPhoneLogin: () -> Unit,
    onCookieLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
        }

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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = onPhoneLogin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Phone, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("手机号登录", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCookieLogin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Cookie, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cookie 登录", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ------------------------------------------------------------------ 手机号登录

@Composable
private fun PhoneLoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
) {
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()
    var phone by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    val countdown = (phoneState as? LoginViewModel.PhoneState.Sent)?.countdown ?: 0
    val isSent = phoneState is LoginViewModel.PhoneState.Sent
    val isSending = phoneState is LoginViewModel.PhoneState.Sending
    val isLoggingIn = phoneState is LoginViewModel.PhoneState.LoggingIn
    val error = (phoneState as? LoginViewModel.PhoneState.Failed)?.message

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LoginTopBar(title = "手机号登录", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "手机号登录",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "使用短信验证码登录，无需密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
                label = { Text("手机号") },
                placeholder = { Text("请输入 11 位手机号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                ),
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = captcha,
                    onValueChange = { captcha = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("验证码") },
                    placeholder = { Text("6 位验证码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { viewModel.sendCaptcha(phone) },
                    enabled = phone.length == 11 && !isSending && (countdown == 0),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = when {
                            isSending -> "发送中…"
                            countdown > 0 -> "${countdown}s"
                            else -> "获取验证码"
                        },
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.loginWithPhone(phone, captcha) },
                enabled = phone.length == 11 && captcha.length >= 4 && !isLoggingIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Cookie 登录

@Composable
private fun CookieLoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
) {
    val error by viewModel.cookieError.collectAsStateWithLifecycle()
    val loading by viewModel.cookieLoading.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LoginTopBar(title = "Cookie 登录", onBack = onBack)

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
                onValueChange = {
                    input = it
                    viewModel.clearCookieError()
                },
                placeholder = { Text("MUSIC_U=xxxxx; __csrf=yyy") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(12.dp))

            if (error != null) {
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
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.submitManualCookie(input) },
                enabled = input.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (loading) {
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
                onClick = {
                    clipboard.getText()?.text?.let {
                        input = it
                        viewModel.clearCookieError()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("从剪贴板粘贴")
            }
        }
    }
}

// ------------------------------------------------------------------ 通用顶栏

@Composable
private fun LoginTopBar(
    title: String,
    onBack: () -> Unit,
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
            Spacer(Modifier.width(48.dp))
        }
    }
}
