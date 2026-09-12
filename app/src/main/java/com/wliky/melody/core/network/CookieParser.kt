package com.wliky.melody.core.network

/**
 * 把用户从浏览器 / 抓包工具里复制出来的 Cookie 规整成可直接用于请求的形式。
 *
 * 扫码登录被风控拦截时，直接使用已有登录态（`MUSIC_U`）是唯一稳定的办法，
 * 但用户粘贴过来的内容格式五花八门，这里统一处理：
 *
 *  - 完整 Cookie 串：`MUSIC_U=xxx; __csrf=yyy`
 *  - 单个键值对：`MUSIC_U=xxx`
 *  - 只有值：`xxx`（自动补成 `MUSIC_U=xxx`）
 *  - 带 `Cookie:` 前缀、多余空白、换行、成对引号（直接整段复制时的常见情况）
 *
 * 纯 JVM 实现，不依赖 Android，可以直接单元测试。
 */
object CookieParser {

    const val KEY_MUSIC_U = "MUSIC_U"

    /** MUSIC_U 的典型长度在 200 以上，这里用一个宽松下限做「看起来没粘错」的判断。 */
    private const val MIN_CREDENTIAL_LENGTH = 16

    /**
     * 返回可直接放进请求头的 Cookie 串；无法识别时返回 null。
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw
            ?.trim()
            ?.removePrefix("Cookie:")
            ?.trim()
            ?.trim('"', '\'', '`')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (!trimmed.contains('=')) {
            // 用户只复制了值。但要排除「;;;」「---」这类误粘：单个凭据里既不该有分隔符，
            // 也不该短到不可能是有效内容。
            val candidate = trimmed.filterNot { it.isWhitespace() }
            if (candidate.contains(';') || candidate.length < MIN_CREDENTIAL_LENGTH) return null
            return "$KEY_MUSIC_U=$candidate"
        }

        val pairs = parsePairs(trimmed)
        if (pairs.isEmpty()) return null
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 解析成键值对，忽略空段与不含 `=` 的段。 */
    fun parsePairs(raw: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        raw.split(';', '\n', '\r').forEach { part ->
            val item = part.trim().removePrefix("Cookie:").trim()
            if (item.isEmpty()) return@forEach
            val separator = item.indexOf('=')
            if (separator <= 0) return@forEach
            val key = item.substring(0, separator).trim()
            val value = item.substring(separator + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) result[key] = value
        }
        return result
    }

    /**
     * 粗判输入是否像一份可用的登录凭据（有 MUSIC_U 且长度合理）。
     * 用于在发请求之前就给用户明确提示，而不是等一次必然失败的请求。
     */
    fun looksUsable(raw: String?): Boolean {
        val normalized = normalize(raw) ?: return false
        val value = parsePairs(normalized)[KEY_MUSIC_U] ?: return false
        return value.length >= MIN_CREDENTIAL_LENGTH
    }

    /** 打日志 / 展示用的脱敏形式，只保留头尾各 4 个字符。 */
    fun mask(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.length <= 8) return "****"
        return "${value.take(4)}…${value.takeLast(4)}"
    }
}
