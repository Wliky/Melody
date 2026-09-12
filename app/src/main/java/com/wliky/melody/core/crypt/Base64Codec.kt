package com.wliky.melody.core.crypt

/**
 * 标准 Base64 编解码（纯 JVM 实现）。
 * 不使用 android.util.Base64，也不用 java.util.Base64（API 26+），
 * 这样加解密逻辑可以直接在 JVM 单元测试里跑，且 minSdk 24 也能用。
 */
object Base64Codec {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val out = StringBuilder((input.size + 2) / 3 * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) {
                out.append(ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            } else {
                out.append('=')
            }
            if (b2 >= 0) {
                out.append(ALPHABET[b2 and 0x3F])
            } else {
                out.append('=')
            }
            i += 3
        }
        return out.toString()
    }

    fun decode(input: String): ByteArray {
        val clean = input.filter { !it.isWhitespace() && it != '=' }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
