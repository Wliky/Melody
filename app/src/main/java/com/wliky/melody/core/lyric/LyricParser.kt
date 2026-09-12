package com.wliky.melody.core.lyric

import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.LyricLine

/**
 * LRC 歌词解析。独立于 UI（文档 §6「歌词接口应独立于 UI」），
 * 纯函数实现，便于单元测试。
 *
 * 支持：
 *  - 一行多个时间标签：`[00:12.30][01:20.10]歌词`
 *  - 毫秒位数不定：`[00:12]`、`[00:12.3]`、`[00:12.345]`
 *  - 元信息行（`[ti:]`、`[ar:]`、`[al:]`、`[by:]`、`[offset:]`）自动跳过
 *  - 翻译按时间戳合并到对应行
 */
object LyricParser {

    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val META_TAG = Regex("^\\[(ti|ar|al|by|offset|re|ve|length):.*]$")

    /** offset 单独匹配：META_TAG 带 ^$ 锚点，只能整行匹配，不能拿去 findAll 多行文本。 */
    private val OFFSET_TAG = Regex("\\[offset:\\s*(-?\\d+)\\s*]")

    fun parse(rawLrc: String, rawTranslation: String = ""): Lyric {
        if (rawLrc.isBlank()) return Lyric.EMPTY
        val offset = parseOffset(rawLrc)
        val translations = parseTranslation(rawTranslation)
        val lines = parseLines(rawLrc, offset).map { line ->
            val translation = translations[line.timeMs]
            if (translation.isNullOrBlank()) line else line.copy(translation = translation)
        }
        return Lyric(lines = lines.sortedBy { it.timeMs }, raw = rawLrc)
    }

    private fun parseLines(raw: String, offset: Long): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || META_TAG.matches(trimmed)) return@forEach
            val matches = TIME_TAG.findAll(trimmed).toList()
            if (matches.isEmpty()) return@forEach
            val text = trimmed.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val timeMs = match.toMilliseconds() - offset
                result += LyricLine(timeMs = timeMs.coerceAtLeast(0L), text = text)
            }
        }
        return result.filter { it.text.isNotBlank() }
    }

    private fun parseTranslation(raw: String): Map<Long, String> {
        if (raw.isBlank()) return emptyMap()
        return parseLines(raw, offset = 0L)
            .associate { it.timeMs to it.text }
    }

    /**
     * LRC 的 `[offset:N]` 单位是毫秒，正值表示歌词整体提前（时间轴减去 N），
     * 与 LRC 规范一致；负值则整体延后。
     */
    private fun parseOffset(raw: String): Long =
        OFFSET_TAG.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private fun MatchResult.toMilliseconds(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fraction = groupValues[3]
        val millis = when {
            fraction.isNullOrEmpty() -> 0L
            fraction.length == 1 -> fraction.toLong() * 100
            fraction.length == 2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + millis
    }
}
