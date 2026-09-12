package com.wliky.melody.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wliky.melody.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 后台播放服务（文档 §10）。
 *
 * ExoPlayer 与 MediaSession 都活在这里，所以退出界面后播放不会中断，
 * 通知栏 / 锁屏 / 耳机的媒体按键也都由它统一处理。
 *
 * v0.4.0 起支持通知栏歌词：订阅 [NotificationLyricBridge]，把当前歌词行
 * 写入 MediaSession 的 metadata（title 后追加歌词），系统媒体通知即显示歌词。
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var urlProvider: SongUrlProvider

    @Inject
    lateinit var lyricBridge: NotificationLyricBridge

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(LazySongUrlDataSource.factory(this, urlProvider)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity())
            .build()

        observeLyric()
    }

    /** 订阅通知栏歌词，动态更新 MediaSession 的 metadata。 */
    private fun observeLyric() {
        scope.launch {
            lyricBridge.currentLine.collectLatest { line ->
                val exoPlayer = player ?: return@collectLatest
                val index = exoPlayer.currentMediaItemIndex
                if (index < 0 || index >= exoPlayer.mediaItemCount) return@collectLatest
                val currentItem = exoPlayer.getMediaItemAt(index)
                val current = currentItem.mediaMetadata

                val updated = current.buildUpon()
                    .apply {
                        if (!line.isNullOrBlank()) {
                            // 把歌词行作为副标题塞进 metadata，通知栏标题栏下会显示
                            setSubtitle(line)
                        } else {
                            setSubtitle("")
                        }
                    }
                    .build()

                // 用 replaceMediaItem 更新当前项的 metadata（Media3 没有直接的 setMediaItemMetadata）
                exoPlayer.replaceMediaItem(index, currentItem.buildUpon().setMediaMetadata(updated).build())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
