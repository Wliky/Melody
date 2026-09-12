package com.wliky.melody.core.model

/** 播放器对外暴露的、与平台无关的播放状态。 */
enum class PlayerState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR,
}

enum class AppRepeatMode {
    OFF,
    ALL,
    ONE,
}

/** 当前播放内容快照，供迷你播放器 / 全屏播放器 / 通知使用。 */
data class NowPlaying(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val durationMs: Long,
    val isPlaying: Boolean = false,
    val state: PlayerState = PlayerState.IDLE,
    val errorMessage: String? = null,
) {
    companion object {
        val EMPTY = NowPlaying(
            songId = "",
            title = "",
            artist = "",
            album = "",
            coverUrl = null,
            durationMs = 0L,
        )
    }
}

data class PlaybackSnapshot(
    val nowPlaying: NowPlaying? = null,
    val state: PlayerState = PlayerState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: AppRepeatMode = AppRepeatMode.OFF,
    val shuffle: Boolean = false,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val errorMessage: String? = null,
)
