package com.wliky.melody.core.lyric

import com.wliky.melody.core.model.Lyric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    @Test
    fun `解析基本时间标签`() {
        val raw = """
            [00:00.00]第一行
            [00:12.30]第二行
            [01:05.50]第三行
        """.trimIndent()

        val lyric = LyricParser.parse(raw)

        assertEquals(3, lyric.lines.size)
        assertEquals(0L, lyric.lines[0].timeMs)
        assertEquals(12_300L, lyric.lines[1].timeMs)
        assertEquals(65_500L, lyric.lines[2].timeMs)
        assertEquals("第二行", lyric.lines[1].text)
    }

    @Test
    fun `一行多时间标签会展开成多行`() {
        val lyric = LyricParser.parse("[00:10.00][01:20.00]同一句歌词")

        assertEquals(2, lyric.lines.size)
        assertEquals(10_000L, lyric.lines[0].timeMs)
        assertEquals(80_000L, lyric.lines[1].timeMs)
        assertEquals("同一句歌词", lyric.lines[0].text)
        assertEquals("同一句歌词", lyric.lines[1].text)
    }

    @Test
    fun `毫秒位数不足时按位补足`() {
        val lyric = LyricParser.parse("[00:01.5]一位\n[00:02.25]两位\n[00:03]没有")

        assertEquals(1_500L, lyric.lines[0].timeMs)
        assertEquals(2_250L, lyric.lines[1].timeMs)
        assertEquals(3_000L, lyric.lines[2].timeMs)
    }

    @Test
    fun `元信息行被忽略`() {
        val raw = """
            [ti:歌名]
            [ar:歌手]
            [al:专辑]
            [by:某人]
            [00:01.00]正文
        """.trimIndent()

        val lyric = LyricParser.parse(raw)

        assertEquals(1, lyric.lines.size)
        assertEquals("正文", lyric.lines[0].text)
    }

    @Test
    fun `offset 会平移时间轴`() {
        // offset 单位是毫秒，正值表示歌词整体提前（时间轴减去 offset）
        val lyric = LyricParser.parse("[offset:500]\n[00:01.00]歌词")

        assertEquals(500L, lyric.lines[0].timeMs)
    }

    @Test
    fun `负 offset 会把歌词整体延后`() {
        val lyric = LyricParser.parse("[offset:-500]\n[00:01.00]歌词")

        assertEquals(1_500L, lyric.lines[0].timeMs)
    }

    @Test
    fun `翻译按时间戳合并到对应行`() {
        val raw = "[00:01.00]Hello\n[00:05.00]World"
        val translation = "[00:01.00]你好\n[00:05.00]世界"

        val lyric = LyricParser.parse(raw, translation)

        assertEquals("你好", lyric.lines[0].translation)
        assertEquals("世界", lyric.lines[1].translation)
    }

    @Test
    fun `空歌词返回 EMPTY`() {
        val lyric = LyricParser.parse("")
        assertTrue(lyric.isEmpty)
        assertEquals(Lyric.EMPTY, lyric)
    }

    @Test
    fun `indexAt 能定位当前行`() {
        val lyric = LyricParser.parse("[00:00.00]A\n[00:10.00]B\n[00:20.00]C")

        assertEquals(-1, lyric.indexAt(-1))
        assertEquals(0, lyric.indexAt(0))
        assertEquals(0, lyric.indexAt(9_999))
        assertEquals(1, lyric.indexAt(10_000))
        assertEquals(2, lyric.indexAt(1_000_000))
    }
}
