package com.wliky.melody.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.QrLoginState
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.EmptyState
import com.wliky.melody.ui.components.GlassCard
import com.wliky.melody.ui.components.MelodyButton
import com.wliky.melody.ui.components.MelodyCard
import com.wliky.melody.ui.components.QrImage
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 二维码登录页：真机扫码 → 网易云 App 确认 → 登录完成展示用户信息。
 */
@Composable
fun LoginScreen(
    onEnterHome: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val hazeState = rememberHazeState()

    // 冷启动与退出登录后重新进入都会刷新（cookie 已清则走二维码流程）
    LaunchedEffect(Unit) { viewModel.refreshSession() }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        LoginBackground(hazeState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = "Melody",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "扫码登录网易云账号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    when {
                        state.user != null -> LoginSuccess(user = state.user!!, onEnterHome = onEnterHome)
                        state.qrContent != null -> QrSection(
                            qrContent = state.qrContent!!,
                            qrState = state.qrState,
                            onRefresh = viewModel::startLogin,
                        )
                        state.error != null -> ErrorSection(
                            message = state.error!!,
                            onRetry = viewModel::startLogin,
                        )
                        else -> SkeletonBox(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(MaterialTheme.shapes.large),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginBackground(hazeState: HazeState) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun QrSection(
    qrContent: String,
    qrState: QrLoginState?,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        QrImage(
            content = qrContent,
            modifier = Modifier
                .size(220.dp)
                .clip(MaterialTheme.shapes.large),
        )
        val hint = when (qrState) {
            QrLoginState.WaitingConfirm -> "已扫码，请在手机上确认"
            QrLoginState.Expired -> "二维码已过期"
            is QrLoginState.Authorized -> "登录成功"
            else -> "打开网易云音乐 App 扫码"
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (qrState is QrLoginState.Expired) {
            MelodyButton(
                text = "刷新二维码",
                onClick = onRefresh,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun LoginSuccess(
    user: com.wliky.melody.data.model.User,
    onEnterHome: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CoverImage(
            url = user.avatarUrl,
            contentDescription = user.nickname,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
        )
        Text(
            text = user.nickname,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "登录成功，欢迎回来",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MelodyButton(
            text = "进入首页",
            onClick = onEnterHome,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ErrorSection(message: String, onRetry: () -> Unit) {
    EmptyState(
        text = "登录遇到问题",
        hint = message,
        modifier = Modifier.fillMaxWidth(),
    )
    MelodyButton(text = "重新登录", onClick = onRetry)
}
