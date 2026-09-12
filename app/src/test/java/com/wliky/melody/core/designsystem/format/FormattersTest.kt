package com.wliky.melody.core.designsystem.format

import com.wliky.melody.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `时长格式化`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("1:00", formatDuration(60_000))
        assertEquals("3:35", formatDuration(215_000))
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("2:03:04", formatDuration(7_384_000))
    }

    @Test
    fun `数量格式化`() {
        assertEquals("999", formatCount(999))
        assertEquals("1.2万", formatCount(12_345))
        assertEquals("1.0亿", formatCount(100_000_000))
        assertEquals("", formatCount(-1))
    }

    @Test
    fun `队列序号补零`() {
        assertEquals("01", padStart2(1))
        assertEquals("09", padStart2(9))
        assertEquals("10", padStart2(10))
    }

    @Test
    fun `分页追加会合并结果与游标`() {
        val first = Page(items = listOf("a", "b"), page = 0, hasMore = true, total = 5)
        val second = Page(items = listOf("c", "d"), page = 1, hasMore = false, total = -1)

        val merged = first.append(second)

        assertEquals(listOf("a", "b", "c", "d"), merged.items)
        assertEquals(1, merged.page)
        assertFalse(merged.hasMore)
        // total 缺失时保留上一页的值
        assertEquals(5, merged.total)
    }

    @Test
    fun `空分页不参与追加`() {
        val page: Page<String> = Page.empty()
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertEquals(0, page.total)
    }
}
