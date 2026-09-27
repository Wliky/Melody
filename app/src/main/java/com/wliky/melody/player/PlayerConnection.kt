package com.wliky.melody.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI 层与 [PlayerService] 的连接器：
 * 进程内共享一个 MediaController，通过 StateFlow 暴露给界面层。
 *
 * MediaController 是 Player 接口的实现，UI 只通过它操作播放，不直接持有 Service。
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _controller = MutableStateFlow<MediaController?>(null)

    /** 就绪后非空；UI 层观察此流拿控制器。 */
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        controllerFuture = MediaController.Builder(context, token)
            .buildAsync()
            .also { future ->
                future.addListener(
                    { _controller.value = future.get() },
                    ContextCompat.getMainExecutor(context),
                )
            }
    }

    /** App 退出时释放连接（Application.onTerminate 并不可靠，由调用方自行决定时机）。 */
    fun release() {
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        _controller.value = null
    }
}
