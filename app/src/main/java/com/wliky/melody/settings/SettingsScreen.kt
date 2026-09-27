package com.wliky.melody.settings

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewCompact
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页音质 / 迷你条偏好（DataStore 持久化）。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    val audioQuality: StateFlow<AudioQuality> = settingsPreferences.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.EXHIGH)

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { settingsPreferences.setAudioQuality(quality) }
    }

    val miniBarMode: StateFlow<MiniBarMode> = settingsPreferences.miniBarMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniBarMode.FIXED)

    fun setMiniBarMode(mode: MiniBarMode) {
        viewModelScope.launch { settingsPreferences.setMiniBarMode(mode) }
    }
}

/**
 * 设置页：只放真实生效的项。
 *
 * - 外观：主题模式（跟随系统 / 浅色 / 深色），写 DataStore 并在 Activity 层实时生效
 * - 播放：音质选择（标准 / 极高 / 无损 / Hi-Res，默认极高）
 * - 存储：存储管理（占用概览 + 清理缓存）
 * - 歌词：歌词管理（外部歌词、歌词字号、播放倍速）
 * - 关于：版本号（点击跳转 GitHub 项目页）；长按 3 次进入组件画廊（开发期复查组件视觉用，非产品入口）
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // 二级页避让挖孔屏 / 状态栏
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = Spacing.screen,
                vertical = Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { AppearanceSection(themeMode = themeMode, onSelectThemeMode = onSelectThemeMode) }
            item { PlaybackSection(viewModel = viewModel) }
            item { ManagementSection(
                onOpenStorage = onOpenStorage,
                onOpenLyricsSettings = onOpenLyricsSettings,
                viewModel = viewModel,
            ) }
            item { AboutSection(onOpenGallery = onOpenGallery) }
            // 避让手势条 / 导航栏
            item {
                Spacer(
                    modifier = Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("外观")
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "主题模式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == themeMode,
                            onClick = { onSelectThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                            label = { Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }
    }
}

/** 播放分组：音质选择（单选弹窗，默认高品）。 */
@Composable
private fun PlaybackSection(viewModel: SettingsViewModel) {
    val quality by viewModel.audioQuality.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("播放")
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SettingsEntryRow(
                    icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                    title = "音质选择",
                    subtitle = "当前：${quality.label}",
                    onClick = { showPicker = true },
                    // 入口行自带左右内边距，去掉默认 padding 保持对齐
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
        }
    }

    if (showPicker) {
        AudioQualityPickerDialog(
            current = quality,
            onSelect = {
                viewModel.setAudioQuality(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 音质单选列表弹窗。 */
@Composable
private fun AudioQualityPickerDialog(
    current: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音质选择") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AudioQuality.entries.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onSelect(quality) })
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = quality == current,
                            onClick = { onSelect(quality) },
                        )
                        Text(
                            text = quality.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
                Text(
                    text = "无损 / Hi-Res 需要账号权限，无权限时自动回退可用的最高音质。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 存储管理 / 歌词管理 / 迷你条显示模式。 */
@Composable
private fun ManagementSection(
    onOpenStorage: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val miniBarMode by viewModel.miniBarMode.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("管理")
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsEntryRow(
                    icon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                    title = "存储管理",
                    subtitle = "存储占用与缓存清理",
                    onClick = onOpenStorage,
                )
                SettingsEntryRow(
                    icon = { Icon(Icons.Rounded.Lyrics, contentDescription = null) },
                    title = "歌词管理",
                    subtitle = "外部歌词 · 字号 · 倍速",
                    onClick = onOpenLyricsSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.ViewCompact,
                            contentDescription = null,
                            modifier = Modifier.size(MelodySize.iconS),
                        )
                        Column(modifier = Modifier.padding(start = Spacing.md)) {
                            Text(
                                text = "迷你条",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "滑动隐藏 = 下滑收起，上滑唤出",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        MiniBarMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == miniBarMode,
                                onClick = { viewModel.setMiniBarMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = MiniBarMode.entries.size,
                                ),
                                label = {
                                    Text(
                                        mode.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通用设置入口行：图标 + 标题/副标题 + [trailing，默认右箭头]。
 *
 * 全设置页唯一的行样式（存储 / 歌词 / 音质 / 版本行共用），
 * 图标统一收敛到 20dp（不许调用方各写各的尺寸），右箭头默认由本组件提供。
 */
@Composable
private fun SettingsEntryRow(
    title: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // 图标统一 20dp：无论调用方传多大，行内视觉保持一致
            Box(
                modifier = Modifier.size(MelodySize.iconS),
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
        val textStart = if (icon != null) Spacing.md else 0.dp
        Column(modifier = Modifier.padding(start = textStart)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MelodySize.iconS),
            )
        }
    }
}

@Composable
private fun AboutSection(onOpenGallery: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName
        }.getOrDefault("1.0.0")
    }

    // 长按版本行进入组件画廊（隐藏入口）；副作用放在 LaunchedEffect 里，不在重组中直接调用
    var galleryIntent by remember { mutableLongStateOf(0L) }
    LaunchedEffect(galleryIntent) {
        if (galleryIntent > 0L) onOpenGallery()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("关于")
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 复用通用入口行：与「存储管理 / 歌词管理」行到同一样式与箭头
                SettingsEntryRow(
                    title = "版本",
                    onClick = {
                        // 点击版本行 → GitHub 项目页
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)),
                            )
                        }
                    },
                    onLongClick = { galleryIntent = SystemClock.uptimeMillis() },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = versionName ?: "1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(MelodySize.iconS),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Melody · 网易云第三方客户端",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** GitHub 项目主页（关于-版本行点击跳转）。 */
private const val GITHUB_REPO_URL = "https://github.com/Wliky/Melody"
