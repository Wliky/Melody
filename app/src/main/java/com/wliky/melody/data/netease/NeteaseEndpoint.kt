package com.wliky.melody.data.netease

import com.wliky.melody.core.model.ApiMode

/**
 * 所有被使用的网易云接口。
 *
 * [directPath]  —— 直连模式使用的路径（形如 /api/xxx），请求前会按官方客户端的做法
 *                  把 `api` 段替换为 `weapi` / `eapi`，再用加密参数 POST。
 * [serverPath]  —— 自建 API 服务（如 NeteaseCloudMusicApi）使用的路径。
 *
 * 空字符串表示该模式下不支持此接口，调用时会抛出明确的 AppError，而不是静默失败。
 */
enum class NeteaseEndpoint(
    val directPath: String,
    val serverPath: String,
) {
    /**
     * 二维码 key。
     *
     * 这个接口对请求头极其挑剔：直连时必须带移动端 UA 且 `Referer` 指向 `/login`，
     * 否则服务端直接 403，响应里没有 unikey —— 表现出来就是「二维码返回异常」。
     */
    QR_KEY("/api/login/qrcode/unikey", "/login/qr/key"),

    /** 自建服务才会用到（返回二维码图片地址）；直连模式由客户端自行拼接二维码内容。 */
    QR_CREATE("", "/login/qr/create"),

    /** 轮询扫码状态。同样需要移动端 UA + `/login` 来源。 */
    QR_CHECK("/api/login/qrcode/client/login", "/login/qr/check"),

    LOGOUT("/api/logout", "/logout"),
    ACCOUNT("/api/w/nuser/account/get", "/user/account"),
    USER_DETAIL("/api/v1/user/detail/{uid}", "/user/detail"),
    USER_PLAYLIST("/api/user/playlist", "/user/playlist"),
    PLAYLIST_DETAIL("/api/v6/playlist/detail", "/playlist/detail"),
    SONG_DETAIL("/api/v3/song/detail", "/song/detail"),
    SONG_URL("/api/song/enhance/player/url/v1", "/song/url/v1"),
    LYRIC("/api/song/lyric", "/lyric"),
    CLOUD_SEARCH("/api/cloudsearch/get/web", "/cloudsearch"),
    SEARCH_SUGGEST("/api/search/suggest/web", "/search/suggest"),
    RECOMMEND_PLAYLIST("/api/personalized/playlist", "/personalized"),
    RECOMMEND_NEW_SONG("/api/personalized/newsong", "/personalized/newsong"),
    DAILY_SONGS("/api/v1/discovery/recommend/songs", "/recommend/songs"),
    TOPLIST("/api/toplist", "/toplist"),
    LIKE_LIST("/api/song/like/get", "/likelist"),
    PLAY_RECORD("/api/v1/play/record", "/user/record"),
    SCROBBLE("", "/scrobble"),
    ;

    /** 登录链路端点：需要移动端 UA 与 `/login` 来源，且允许 weapi → eapi 双链路重试。 */
    val isQrEndpoint: Boolean
        get() = this == QR_KEY || this == QR_CHECK || this == QR_CREATE

    /** 该端点是否在某个模式下有实现。 */
    fun supports(mode: ApiMode): Boolean = when (mode) {
        ApiMode.DIRECT -> directPath.isNotBlank()
        ApiMode.API_SERVER -> serverPath.isNotBlank()
        ApiMode.MOCK -> true
    }
}
