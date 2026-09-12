package com.wliky.melody.core.crypt

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val HEX_CHARS = "0123456789abcdef"

/** 字节数组 → 小写 HEX。 */
fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX_CHARS[v ushr 4]
        out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(out)
}

/** 小写/大写 HEX → 字节数组。 */
fun String.hexToBytes(): ByteArray {
    val len = length / 2
    val out = ByteArray(len)
    for (i in 0 until len) {
        out[i] = ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
    }
    return out
}

/**
 * 加解密工具。纯 JVM 实现（不依赖 android.util / java.util.Base64），
 * 可以直接在单元测试里跑，minSdk 24 也完全可用。
 *
 * 这些算法用于复现官方客户端自身公开使用的请求参数签名方式，
 * 属于「按原样调用官方接口」的技术细节，不涉及绕过任何访问控制。
 */
object CryptoUtils {

    private const val TRANSFORMATION_CBC = "AES/CBC/PKCS5Padding"
    private const val TRANSFORMATION_ECB = "AES/ECB/PKCS5Padding"
    private const val TRANSFORMATION_RSA = "RSA/ECB/NoPadding"

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    fun md5Hex(input: String): String = md5Hex(input.toByteArray(Charsets.UTF_8))

    fun md5Hex(input: ByteArray): String = MessageDigest.getInstance("MD5").digest(input).toHex()

    /** AES-CBC + PKCS5，返回标准 Base64（weapi 的参数加密）。 */
    fun aesEncryptCbcBase64(plain: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION_CBC)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        return Base64Codec.encode(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** AES-ECB + PKCS5，返回大写 HEX（eapi 的参数加密）。 */
    fun aesEncryptEcbHex(plain: String, key: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION_ECB)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8)).toHex().uppercase()
    }

    /**
     * RSA 无填充加密（weapi 用它对随机密钥做加密）。
     * 输出左侧补零至 256 位小写 HEX。
     */
    fun rsaEncryptNoPaddingHex(data: ByteArray, modulusHex: String, exponentHex: String): String {
        val modulus = BigInteger(modulusHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val cipher = Cipher.getInstance(TRANSFORMATION_RSA)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val blockSize = (modulus.bitLength() + 7) / 8
        val padded = ByteArray(blockSize)
        data.copyInto(padded, 0, 0, minOf(data.size, blockSize))
        val hex = cipher.doFinal(padded).toHex()
        return if (hex.length >= 256) hex.substring(hex.length - 256) else "0".repeat(256 - hex.length) + hex
    }
}
