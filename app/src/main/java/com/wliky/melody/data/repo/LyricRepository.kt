package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.LyricLine
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌词仓库：`/weapi/song/lyric`（`lrc.lyric` 为主文本，`tlyric.lyric` 为翻译）。
 * LRC 每行可带多个时间戳 `[00:12.34][01:30.00]文本`，统一展开排序。
 */
@Singleton
class LyricRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /**
     * 拉取歌词。无歌词（纯音乐）返回空列表而非失败。
     */
    suspend fun getLyric(songId: Long): AppResult<List<LyricLine>> =
        when (
            val res = client.callWeapi(
                "/weapi/song/lyric",
                """{"id":"$songId","lv":1,"kv":1,"tv":-1,"csrf_token":""}""",
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val main = res.data["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content
                if (main == null) {
                    AppResult.success(emptyList())
                } else {
                    val translation = res.data["tlyric"]?.jsonObject
                        ?.get("lyric")?.jsonPrimitive?.content
                        ?.let(::parseLrc)
                        ?.associateBy { it.timeMs }
                    AppResult.success(
                        parseLrc(main).map { line ->
                            line.copy(translation = translation?.get(line.timeMs)?.text)
                        },
                    )
                }
            }
        }

    /** `[mm:ss(.xx)]文本`，一行多戳全部展开；跳过元数据行与空行。 */
    private fun parseLrc(raw: String): List<LyricLine> =
        raw.lineSequence()
            .flatMap { line ->
                val stamps = STAMP.findAll(line).toList()
                val text = line.replace(STAMP, "").trim()
                if (stamps.isEmpty() || text.isEmpty()) {
                    emptySequence()
                } else {
                    stamps.asSequence().map { m ->
                        val frac = m.groupValues[3]
                        val fracMs = when (frac.length) {
                            0 -> 0L
                            1 -> frac.toLong() * 100
                            2 -> frac.toLong() * 10
                            else -> frac.take(3).toLong()
                        }
                        LyricLine(
                            timeMs = m.groupValues[1].toLong() * 60_000 +
                                m.groupValues[2].toLong() * 1_000 + fracMs,
                            text = text,
                        )
                    }
                }
            }
            .sortedBy { it.timeMs }
            .toList()

    private companion object {
        /** 时间戳：`[00:12]` / `[00:12.3]` / `[00:12.34]` / `[00:12:34]` */
        private val STAMP = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    }
}
