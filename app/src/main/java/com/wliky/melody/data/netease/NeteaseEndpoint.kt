package com.wliky.melody.data.netease

/**
 * 所有被使用的网易云接口。
 *
 * [directPath]  —— 直连模式使用的路径（形如 /api/xxx），请求前会按官方客户端的做法
 *                  把 `api` 段替换为 `weapi`，再用加密参数 POST。
 * [serverPath]  —— 自建 API 服务（如 NeteaseCloudMusicApi）使用的路径。
 *
 * 空字符串表示该模式下不支持此接口，调用时会抛出明确的 AppError，而不是静默失败。
 */
enum class NeteaseEndpoint(
    val directPath: String,
    val serverPath: String,
) {
    QR_KEY("/api/login/qrcode/unikey", "/login/qr/key"),
    QR_CREATE("", "/login/qr/create"),
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
}
