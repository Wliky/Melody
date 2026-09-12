package com.wliky.melody.data.netease

import com.wliky.melody.core.crypt.CryptoUtils
import com.wliky.melody.core.crypt.toHex

/**
 * 请求参数签名。复现官方客户端自身使用的公开算法，用于正常调用接口，
 * 不涉及任何绕过访问控制 / 破解会员的行为。
 *
 * weapi：AES-128-CBC（固定密钥）→ AES-256-CBC（随机密钥）→ RSA 加密随机密钥
 * eapi ：AES-128-ECB（固定密钥）
 * 二者的固定密钥与 RSA 公钥均为公开常量。
 */
object NeteaseCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val IV = "0102030405060708"
    private const val EAPI_SEPARATOR = "-36cd479b6b5-"

    private const val RSA_MODULUS =
        "e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec" +
            "4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424" +
            "d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val RSA_EXPONENT = "010001"

    /** 返回可直接作为表单提交的参数。 */
    fun weapi(jsonBody: String): Map<String, String> {
        // 16 字节随机量 -> 32 位 hex 字符串，作为第二段 AES-256 的密钥
        val secretKey = CryptoUtils.randomBytes(16).toHex()
        val onceEncrypted = CryptoUtils.aesEncryptCbcBase64(jsonBody, PRESET_KEY, IV)
        val params = CryptoUtils.aesEncryptCbcBase64(onceEncrypted, secretKey, IV)
        val encSecKey = CryptoUtils.rsaEncryptNoPaddingHex(
            data = secretKey.reversed().toByteArray(Charsets.UTF_8),
            modulusHex = RSA_MODULUS,
            exponentHex = RSA_EXPONENT,
        )
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    /** eapi 参数（大写 HEX）。 */
    fun eapi(path: String, jsonBody: String): String {
        val digest = CryptoUtils.md5Hex("nobody$path" + "use" + jsonBody + "md5forencrypt")
        val text = "$path$EAPI_SEPARATOR$jsonBody$EAPI_SEPARATOR$digest"
        return CryptoUtils.aesEncryptEcbHex(text, EAPI_KEY)
    }

    /**
     * 官方客户端会把路径中的 `api` 段替换掉：/api/xxx → /weapi/xxx 或 /eapi/xxx。
     */
    fun transformPath(path: String, target: String): String =
        path.replaceFirst(Regex("\\w*api"), target)
}
