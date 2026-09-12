package com.wliky.melody.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.io.IOException

/**
 * 让 Media3 支持「懒解析音频地址」：队列里的每首歌先以
 * `melody://song/<songId>` 的形式进队，真正开始加载时才去换真实地址。
 *
 * 好处：
 *  - 通知栏 / 锁屏 / 耳机的上一首下一首按钮天然可用（队列是完整的）；
 *  - 只对真正播放到的曲目发请求；
 *  - 解析失败会变成播放错误，UI 能给出「该内容不可播放」的明确提示。
 */
object LazySongUrlDataSource {

    const val SCHEME = "melody"

    fun songUri(songId: String): Uri = Uri.parse("$SCHEME://song/$songId")

    fun factory(context: Context, provider: SongUrlProvider): DataSource.Factory {
        val upstream = DefaultDataSource.Factory(context)
        return ResolvingDataSource.Factory(upstream) { dataSpec ->
            val uri = dataSpec.uri
            val songId = if (uri.scheme == SCHEME) uri.lastPathSegment.orEmpty() else null
            if (songId.isNullOrBlank()) {
                dataSpec
            } else {
                val resolved = provider.resolveBlocking(songId)
                if (resolved.isNullOrBlank()) {
                    throw IOException("该内容当前不可播放（songId=$songId）")
                }
                dataSpec.withUri(Uri.parse(resolved))
            }
        }
    }
}
