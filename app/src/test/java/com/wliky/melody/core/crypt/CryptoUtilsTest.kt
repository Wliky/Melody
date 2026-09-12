package com.wliky.melody.core.crypt

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun `md5 结果与已知向量一致`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", CryptoUtils.md5Hex("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", CryptoUtils.md5Hex(""))
    }

    @Test
    fun `自实现的 Base64 与 JDK 实现一致`() {
        val samples = listOf(
            ByteArray(0),
            "f".toByteArray(),
            "fo".toByteArray(),
            "foo".toByteArray(),
            "foob".toByteArray(),
            "fooba".toByteArray(),
            "foobar".toByteArray(),
            ByteArray(256) { (it and 0xFF).toByte() },
        )
        samples.forEach { bytes ->
            assertEquals(
                "长度 ${bytes.size} 的 Base64 编码不一致",
                Base64.getEncoder().encodeToString(bytes),
                Base64Codec.encode(bytes),
            )
        }
    }

    @Test
    fun `Base64 解码可还原原文`() {
        val bytes = ByteArray(97) { ((it * 7) and 0xFF).toByte() }
        val encoded = Base64Codec.encode(bytes)

        assertTrue(bytes.contentEquals(Base64Codec.decode(encoded)))
    }

    @Test
    fun `AES-CBC 加密结果可被标准实现解密`() {
        val key = "0CoJUm6Qyw8W8jud"
        val iv = "0102030405060708"
        val plain = """{"s":"告白气球","type":1,"limit":30}"""

        val encoded = CryptoUtils.aesEncryptCbcBase64(plain, key, iv)

        // 用 JDK 的 AES 解密，验证 密钥 / IV / 填充 处理正确
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encoded))

        assertEquals(plain, String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `weapi 第二段使用 32 位密钥（AES-256）`() {
        val secretKey = "0123456789abcdef0123456789abcdef"
        val iv = "0102030405060708"
        val stageOne = CryptoUtils.aesEncryptCbcBase64("plain", "0CoJUm6Qyw8W8jud", iv)
        val stageTwo = CryptoUtils.aesEncryptCbcBase64(stageOne, secretKey, iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        assertEquals(stageOne, String(cipher.doFinal(Base64.getDecoder().decode(stageTwo)), Charsets.UTF_8))
    }

    @Test
    fun `AES-ECB 输出大写 HEX 且可解密（eapi 用）`() {
        val key = "e82ckenh8dichen8"
        val plain = "/api/song/enhance/player/url/v1-36cd479b6b5-{}-36cd479b6b5-abc"

        val hex = CryptoUtils.aesEncryptEcbHex(plain, key)

        assertEquals(hex, hex.uppercase())
        assertEquals(0, hex.length % 32)

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        assertEquals(plain, String(cipher.doFinal(hex.hexToBytes()), Charsets.UTF_8))
    }

    @Test
    fun `RSA 加密输出固定 256 位 HEX`() {
        val secretKey = "0123456789abcdef0123456789abcdef"
        val hex = CryptoUtils.rsaEncryptNoPaddingHex(
            data = secretKey.reversed().toByteArray(Charsets.UTF_8),
            modulusHex = MODULUS,
            exponentHex = "010001",
        )

        assertEquals(256, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun `hex 与字节数组互转可逆`() {
        val bytes = ByteArray(33) { ((it * 13 + 5) and 0xFF).toByte() }
        assertTrue(bytes.contentEquals(bytes.toHex().hexToBytes()))
        assertEquals("00ff10", byteArrayOf(0, -1, 16).toHex())
    }

    private companion object {
        const val MODULUS =
            "e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec" +
                "4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424" +
                "d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    }
}
