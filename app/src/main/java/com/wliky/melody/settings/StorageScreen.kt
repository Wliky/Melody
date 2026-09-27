package com.wliky.melody.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import com.wliky.melody.data.cache.RepoCache
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 存储管理：应用占用概览 + 清理缓存。 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repoCache: RepoCache,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val clearing: Boolean = false,
        val cacheBytes: Long = 0,
        val filesBytes: Long = 0,
    ) {
        val totalBytes: Long get() = cacheBytes + filesBytes
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                UiState(
                    loading = false,
                    clearing = _uiState.value.clearing,
                    cacheBytes = dirSize(context.cacheDir),
                    filesBytes = dirSize(context.filesDir),
                )
            }
            _uiState.value = state
        }
    }

    /** 清空 cacheDir（含 Coil 磁盘缓存 + 页面快照缓存磁盘）+ 内存缓存，随后刷新。 */
    fun clearCache() {
        if (_uiState.value.clearing) return
        _uiState.value = _uiState.value.copy(clearing = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repoCache.clearAll()
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                // Coil 3 的 memoryCache 可空
                context.imageLoader.memoryCache?.clear()
            }
            refresh()
            // refresh 会带出 clearing=true 的新状态，这里统一关掉
            _uiState.value = _uiState.value.copy(clearing = false, loading = false)
        }
    }

    /** 递归求目录大小。 */
    private fun dirSize(dir: File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

/** 存储管理二级页：占用卡片 + 清理按钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存储管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 总占用卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storage, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            text = "应用占用",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    } else {
                        Text(
                            text = "共 ${formatBytes(state.totalBytes)}",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        // 缓存占比进度条
                        LinearProgressIndicator(
                            progress = {
                                if (state.totalBytes > 0) {
                                    state.cacheBytes.toFloat() / state.totalBytes
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 明细卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("缓存（封面图、网络缓存）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatBytes(state.cacheBytes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text("应用数据（登录态、设置、持久化）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatBytes(state.filesBytes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            // 清理按钮
            Button(
                onClick = viewModel::clearCache,
                enabled = !state.clearing && !state.loading && state.cacheBytes > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.clearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.height(0.dp))
                } else {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                }
                Text(
                    text = if (state.clearing) "清理中…" else "清理应用缓存",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(
                text = "清理缓存不会影响登录状态与设置，已缓存封面将按需重新下载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 字节数人性化显示。 */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 30 -> "%.2f GB".format(bytes / 1024f / 1024f / 1024f)
    bytes >= 1 shl 20 -> "%.2f MB".format(bytes / 1024f / 1024f)
    bytes >= 1 shl 10 -> "%.2f KB".format(bytes / 1024f)
    bytes > 0 -> "$bytes B"
    else -> "0 B"
}
