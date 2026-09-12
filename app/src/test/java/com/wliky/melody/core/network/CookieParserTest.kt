package com.wliky.melody.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cookie 规整逻辑的回归测试。
 *
 * 这些用例对应真实的用户输入：从浏览器 Cookies 面板复制的整段串、
 * 只复制了值、从聊天窗口粘贴带上了 `Cookie:` 前缀和换行，等等。
 */
class CookieParserTest {

    @Test
    fun `整段 Cookie 会被规整成标准形式`() {
        val normalized = CookieParser.normalize("MUSIC_U=abc123def456; __csrf=def456")

        assertEquals("MUSIC_U=abc123def456; __csrf=def456", normalized)
    }

    @Test
    fun `只粘贴值时自动补上 MUSIC_U`() {
        assertEquals("MUSIC_U=abcdefghijklmnop", CookieParser.normalize("abcdefghijklmnop"))
    }

    @Test
    fun `带 Cookie 前缀 多余空白与换行也能识别`() {
        val raw = "  Cookie:  MUSIC_U=abcdefghijklmnop ;  __csrf=xyz \n"

        assertEquals("MUSIC_U=abcdefghijklmnop; __csrf=xyz", CookieParser.normalize(raw))
    }

    @Test
    fun `成对引号会被去掉`() {
        assertEquals(
            "MUSIC_U=abcdefghijklmnop",
            CookieParser.normalize("\"MUSIC_U=abcdefghijklmnop\""),
        )
        assertEquals(
            "MUSIC_U=abcdefghijklmnop",
            CookieParser.normalize("'MUSIC_U=abcdefghijklmnop'"),
        )
    }

    @Test
    fun `空输入与无有效片段时返回 null`() {
        assertNull(CookieParser.normalize(null))
        assertNull(CookieParser.normalize("   "))
        assertNull(CookieParser.normalize("; ; ;"))
        assertNull(CookieParser.normalize("Cookie:"))
    }

    @Test
    fun `可用性判断要求存在 MUSIC_U 且长度合理`() {
        assertTrue(CookieParser.looksUsable("MUSIC_U=0123456789abcdef0123456789abcdef"))
        assertFalse(CookieParser.looksUsable("MUSIC_U=short"))
        assertFalse(CookieParser.looksUsable("__csrf=0123456789abcdef0123456789abcdef"))
        assertFalse(CookieParser.looksUsable(null))
    }

    @Test
    fun `解析键值对时忽略非法片段`() {
        val pairs = CookieParser.parsePairs("MUSIC_U=aaa; broken; =novalue; __csrf=bbb")

        assertEquals(mapOf("MUSIC_U" to "aaa", "__csrf" to "bbb"), pairs)
    }

    @Test
    fun `脱敏只保留头尾各四个字符`() {
        assertEquals("****", CookieParser.mask("short"))
        assertEquals("abcd…wxyz", CookieParser.mask("abcdefghijklmnopwxyz"))
    }

    @Test
    fun `sanitize 会丢掉 Set-Cookie 属性并保留真正的 Cookie`() {
        val raw = "MUSIC_U=abc123def456ghi789; Path=/; HttpOnly; Expires=Wed, 21 Oct 2026 07:28:00 GMT; __csrf=xyz"

        assertEquals("MUSIC_U=abc123def456ghi789; __csrf=xyz", CookieParser.sanitize(raw))
    }

    @Test
    fun `sanitize 对只有属性的串返回 null`() {
        assertNull(CookieParser.sanitize("Path=/; HttpOnly; Secure"))
    }

    @Test
    fun `hasMusicU 只认 MUSIC_U 不认匿名访客 Cookie`() {
        assertTrue(CookieParser.hasMusicU("MUSIC_U=0123456789abcdef"))
        assertFalse(CookieParser.hasMusicU("NMTID=00abcdef; _ntes_nuid=1234; WNMCID=abcd"))
        assertFalse(CookieParser.hasMusicU(""))
        assertFalse(CookieParser.hasMusicU(null))
    }

    @Test
    fun `匿名访客 Cookie 不会让 musicU 返回真值`() {
        assertNull(CookieParser.musicU("NMTID=00abcdef"))
        assertEquals("0123456789abcdef", CookieParser.musicU("MUSIC_U=0123456789abcdef"))
    }
}
