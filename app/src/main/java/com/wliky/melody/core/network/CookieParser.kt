package com.wliky.melody.core.network

/**
 * 把用户从浏览器 / 抓包工具里复制出来的 Cookie、以及服务端回传的 Set-Cookie 片段，
 * 规整成可直接用于请求的形式。
 *
 * 需要处理的输入形态：
 *
 *  - 完整 Cookie 串：`MUSIC_U=xxx; __csrf=yyy`
 *  - 单个键值对：`MUSIC_U=xxx`
 *  - 只有值：`xxx`（自动补成 `MUSIC_U=xxx`）
 *  - 带 `Cookie:` 前缀、多余空白、换行、成对引号
 *  - **原始 Set-Cookie 拼接串**：`MUSIC_U=xxx; Path=/; HttpOnly; Expires=...`
 *    —— 自建 API 服务（api-enhanced 的 `/login/qr/check` 等）就是把 Set-Cookie 数组
 *    直接 `join(';')` 返回的，其中的 `Path` / `Expires` / `Max-Age` 是**响应头属性而不是
 *    Cookie**，必须丢掉，否则会被当成 Cookie 名原样发回服务端。
 *
 * 纯 JVM 实现，不依赖 Android，可以直接单元测试。
 */
object CookieParser {

    const val KEY_MUSIC_U = "MUSIC_U"

    /** MUSIC_U 的典型长度在 200 以上，这里用一个宽松下限做「看起来没粘错」的判断。 */
    private const val MIN_CREDENTIAL_LENGTH = 16

    /**
     * Set-Cookie 的属性名（大小写不敏感）。它们不是 Cookie 本身。
     */
    private val RESERVED_ATTRIBUTES = setOf(
        "path", "domain", "expires", "max-age", "httponly", "secure",
        "samesite", "version", "comment", "priority", "partitioned",
        "commenturl", "discard", "port",
    )

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

        return sanitize(trimmed)
    }

    /**
     * 只做「清洗」不做「补全」：解析出所有真正的 Cookie 键值对，丢掉 Set-Cookie 属性。
     * 返回 null 表示洗完之后什么都不剩。
     */
    fun sanitize(raw: String?): String? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        val pairs = parsePairs(text)
        if (pairs.isEmpty()) return null
        return join(pairs)
    }

    /** 把键值对拼成请求头里用的形式。 */
    fun join(pairs: Map<String, String>): String =
        pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }

    /** 解析成键值对，忽略空段、不含 `=` 的段、以及 Set-Cookie 属性。 */
    fun parsePairs(raw: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        raw.split(';', '\n', '\r').forEach { part ->
            val item = part.trim().removePrefix("Cookie:").trim()
            if (item.isEmpty()) return@forEach
            val separator = item.indexOf('=')
            if (separator <= 0) return@forEach
            val key = item.substring(0, separator).trim()
            if (key.isEmpty() || key.lowercase() in RESERVED_ATTRIBUTES) return@forEach
            val value = item.substring(separator + 1).trim().trim('"')
            if (value.isNotEmpty()) result[key] = value
        }
        return result
    }

    /**
     * 粗判输入是否像一份可用的登录凭据（有 MUSIC_U 且长度合理）。
     * 用于在发请求之前就给用户明确提示，而不是等一次必然失败的请求。
     */
    fun looksUsable(raw: String?): Boolean = hasMusicU(normalize(raw))

    /**
     * 这份 Cookie 里是否真的带着登录态。
     *
     * **这是「是否已登录」的唯一判据。** 不能拿「Cookie 非空」当登录标志：
     * 网易（以及任何兼容服务）对**匿名请求也会下发** `NMTID` / `_ntes_nuid` / `WNMCID`
     * 之类的访客 Cookie，一旦把它们写进会话，App 就会在完全没登录的情况下自认为已登录
     * —— 表现就是「一进登录页就显示已登录」。只有 `MUSIC_U` 才是真正的登录凭据。
     */
    fun hasMusicU(raw: String?): Boolean = musicU(raw) != null

    /** 取 `MUSIC_U` 的值，没有则返回 null。 */
    fun musicU(raw: String?): String? {
        val value = raw?.let { parsePairs(it)[KEY_MUSIC_U] } ?: return null
        return value.takeIf { it.length >= MIN_CREDENTIAL_LENGTH }
    }

    /** 打日志 / 展示用的脱敏形式，只保留头尾各 4 个字符。 */
    fun mask(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.length <= 8) return "****"
        return "${value.take(4)}…${value.takeLast(4)}"
    }
}
