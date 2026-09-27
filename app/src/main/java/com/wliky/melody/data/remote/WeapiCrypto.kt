package com.wliky.melody.data.remote

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.math.BigInteger

/**
 * 网易云 weapi 加密（纯 Kotlin 实现，与 NeteaseCloudMusicApi / Melodia 对齐）。
 *
 * 流程：
 * 1. secretKey = 16 位随机字符串
 * 2. params   = base64(AES-CBC(payload, presetKey)) 再 base64(AES-CBC(_, secretKey))
 * 3. encSecKey = RSA(secretKey 反序, e=0x10001, n=官方公钥模数)，hex 左补零至 256 位
 */
object WeapiCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val RSA_EXPONENT = "010001"
    private const val RSA_MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b7251" +
            "52b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93" +
            "870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97dde" +
            "f52741d546b8e289dc6935b3ece0462db0a22b8e7"

    private const val SECRET_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /**
     * 加密一条 JSON 载荷，返回表单字段 params 与 encSecKey。
     */
    fun encrypt(payloadJson: String): Map<String, String> {
        val secretKey = randomSecretKey()
        val params = aesEncrypt(
            plaintext = aesEncrypt(payloadJson, PRESET_KEY),
            key = secretKey,
        )
        val encSecKey = rsaEncrypt(secretKey)
        return mapOf(
            "params" to params,
            "encSecKey" to encSecKey,
        )
    }

    /** AES-128-CBC + PKCS5Padding，输出 Base64。 */
    private fun aesEncrypt(plaintext: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8)),
        )
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /** 教科书式 RSA（与官方 JS bigInt(base256) 等价）：字符串反序后按字节大端取幂，hex 输出补齐 256 字符。 */
    private fun rsaEncrypt(text: String): String {
        val reversed = BigInteger(text.reversed().toByteArray(Charsets.UTF_8))
        val e = BigInteger(RSA_EXPONENT, 16)
        val n = BigInteger(RSA_MODULUS, 16)
        val result = reversed.modPow(e, n)
        return result.toString(16).padStart(256, '0')
    }

    private fun randomSecretKey(): String {
        val random = SecureRandom()
        return buildString {
            repeat(16) { append(SECRET_CHARS[random.nextInt(SECRET_CHARS.length)]) }
        }
    }
}
