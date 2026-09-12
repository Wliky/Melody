package com.wliky.melody.data.netease

import com.wliky.melody.core.network.MelodyJson
import com.wliky.melody.core.network.arr
import com.wliky.melody.core.network.boolean
import com.wliky.melody.core.network.int
import com.wliky.melody.core.network.obj
import com.wliky.melody.core.network.str
import com.wliky.melody.data.netease.dto.PlaylistDto
import com.wliky.melody.data.netease.dto.SongDto
import com.wliky.melody.data.netease.dto.SongUrlDto
import com.wliky.melody.data.netease.dto.toDomain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第三方接口字段经常变化（数字变字符串、字段缺失、多包一层），
 * 这组测试用接近真实的响应片段，确保解析层不会因此崩掉。
 *
 * 真实响应结构参考：搜索/歌单详情/歌曲地址/榜单四个接口。
 */
class NeteaseDtoParsingTest {

    @Test
    fun `搜索单曲：数字 id 会被转成字符串，字段完整`() {
        val json = MelodyJson.parseToJsonElement(SEARCH_RESPONSE).jsonObject

        val result = json.obj("result")!!
        val songs = MelodyJson.decodeFromJsonElement(
            ListSerializer(SongDto.serializer()),
            result.arr("songs")!!,
        )

        assertEquals(2, songs.size)

        val first = songs[0].toDomain()
        assertEquals("347230", first.id)
        assertEquals("告白气球", first.name)
        assertEquals("周杰伦", first.artistText)
        assertEquals("周杰伦的床边故事", first.album?.name)
        assertEquals(215_000L, first.durationMs)
        assertTrue(first.isPaidOnly)
        assertEquals("https://p1.music.126.net/cover.jpg", first.coverUrl)

        // 分页信息
        assertEquals(100, result.int("songCount"))
        assertTrue(result.boolean("hasMore") == true)
    }

    @Test
    fun `字符串 id 与缺失字段同样能解析`() {
        val json = MelodyJson.parseToJsonElement(SEARCH_RESPONSE).jsonObject
        val songs = MelodyJson.decodeFromJsonElement(
            ListSerializer(SongDto.serializer()),
            json.obj("result")!!.arr("songs")!!,
        )

        val second = songs[1].toDomain()
        assertEquals("509781611", second.id)
        assertEquals("夜曲", second.name)
        // dt 缺失时退化为 0，而不是抛异常
        assertEquals(0L, second.durationMs)
        // al 缺失时封面为 null
        assertNull(second.coverUrl)
        assertFalse(second.isPaidOnly)
    }

    @Test
    fun `新增的未知字段不会破坏解析`() {
        val song = MelodyJson.decodeFromJsonElement(
            SongDto.serializer(),
            MelodyJson.parseToJsonElement(UNKNOWN_FIELDS_SONG).jsonObject,
        ).toDomain()

        assertEquals("1234567", song.id)
        assertEquals("测试歌曲", song.name)
        assertEquals("未知歌手", song.artistText)
    }

    @Test
    fun `歌单详情：嵌套结构与创建者信息`() {
        val json = MelodyJson.parseToJsonElement(PLAYLIST_RESPONSE).jsonObject
        val playlistJson = json.obj("playlist")!!

        val playlist = MelodyJson.decodeFromJsonElement(PlaylistDto.serializer(), playlistJson).toDomain()

        assertEquals("24381616", playlist.id)
        assertEquals("我喜欢的音乐", playlist.name)
        assertEquals(2, playlist.trackCount)
        assertEquals("Melody 用户", playlist.creator)
        assertEquals(999_999L, playlist.playCount)
        assertEquals(5, playlist.specialType)
        assertEquals("https://p1.music.126.net/list.jpg", playlist.coverUrl)

        val tracks = MelodyJson.decodeFromJsonElement(
            ListSerializer(SongDto.serializer()),
            playlistJson.arr("tracks")!!,
        )
        assertEquals(1, tracks.size)
        assertEquals("晴天", tracks[0].toDomain().name)
    }

    @Test
    fun `推荐歌单的 picUrl 会兜底成封面`() {
        val json = MelodyJson.parseToJsonElement(RECOMMEND_PLAYLIST_ITEM).jsonObject
        val playlist = MelodyJson.decodeFromJsonElement(PlaylistDto.serializer(), json).toDomain()

        assertEquals("推荐歌单 A", playlist.name)
        assertEquals("https://p1.music.126.net/pic.jpg", playlist.coverUrl)
        // 推荐歌单接口不返回创建者
        assertNull(playlist.creator)
    }

    @Test
    fun `歌曲地址：无版权时 url 为 null，不会误判为可播放`() {
        val json = MelodyJson.parseToJsonElement(SONG_URL_RESPONSE).jsonObject
        val items = json.arr("data")!!

        val playable = MelodyJson.decodeFromJsonElement(SongUrlDto.serializer(), items[0].jsonObject)
        assertEquals("https://m10.music.126.net/song.mp3", playable.url)
        assertEquals(320_000, playable.br)
        assertEquals("exhigh", playable.level)

        val blocked = MelodyJson.decodeFromJsonElement(SongUrlDto.serializer(), items[1].jsonObject)
        assertNull(blocked.url)
        assertEquals(0, blocked.br ?: -1)
    }

    @Test
    fun `榜单接口：返回顶层数组时也能取出 id 与名称`() {
        val items = MelodyJson.parseToJsonElement(TOPLIST_ARRAY) as JsonArray

        val charts = items.mapNotNull { element ->
            val dto = MelodyJson.decodeFromJsonElement(PlaylistDto.serializer(), element.jsonObject)
            dto.id?.takeIf { it.isNotBlank() }?.let { id -> id to dto.name }
        }

        assertEquals(2, charts.size)
        assertEquals("19723756" to "飙升榜", charts[0])
        assertEquals("3779629" to "新歌榜", charts[1])
    }

    @Test
    fun `歌词接口的 lrc 与 tlyric 可以安全取值`() {
        val json = MelodyJson.parseToJsonElement(LYRIC_RESPONSE).jsonObject

        assertEquals("[00:01.00]Hello", json.obj("lrc")?.str("lyric"))
        assertEquals("[00:01.00]你好", json.obj("tlyric")?.str("lyric"))
        // 缺失节点返回 null，而不是抛异常
        assertNull(json.obj("klyric")?.str("lyric"))
    }

    @Test
    fun `登录状态码语义正确`() {
        val waiting = MelodyJson.parseToJsonElement("""{"code":801,"message":"等待扫码"}""").jsonObject
        val expired = MelodyJson.parseToJsonElement("""{"code":800,"message":"二维码不存在或已过期"}""").jsonObject
        val ok = MelodyJson.parseToJsonElement("""{"code":803,"cookie":"MUSIC_U=xxx"}""").jsonObject

        assertEquals(801, waiting.int("code"))
        assertEquals(800, expired.int("code"))
        assertEquals("MUSIC_U=xxx", ok.str("cookie"))
    }

    private companion object {
        const val SEARCH_RESPONSE = """
        {
          "result": {
            "songs": [
              {
                "id": 347230,
                "name": "告白气球",
                "ar": [{"id": 6452, "name": "周杰伦"}],
                "al": {"id": 347208, "name": "周杰伦的床边故事", "picUrl": "https://p1.music.126.net/cover.jpg"},
                "dt": 215000,
                "fee": 1
              },
              {
                "id": "509781611",
                "name": "夜曲",
                "ar": [{"id": "6452", "name": "周杰伦"}]
              }
            ],
            "songCount": 100,
            "hasMore": true
          },
          "code": 200
        }
        """

        const val UNKNOWN_FIELDS_SONG = """
        {
          "id": "1234567",
          "name": "测试歌曲",
          "ar": [],
          "fee": "0",
          "brandNewField": {"nested": [1, 2, 3]},
          "anotherField": "whatever"
        }
        """

        const val PLAYLIST_RESPONSE = """
        {
          "playlist": {
            "id": 24381616,
            "name": "我喜欢的音乐",
            "coverImgUrl": "https://p1.music.126.net/list.jpg",
            "trackCount": 2,
            "playCount": 999999,
            "specialType": 5,
            "creator": {"userId": 1, "nickname": "Melody 用户"},
            "subscribed": true,
            "tracks": [
              {
                "id": 186016,
                "name": "晴天",
                "ar": [{"id": 6452, "name": "周杰伦"}],
                "al": {"id": 185809, "name": "叶惠美"},
                "dt": 269000
              }
            ]
          },
          "code": 200
        }
        """

        const val RECOMMEND_PLAYLIST_ITEM = """
        {
          "id": 123456,
          "name": "推荐歌单 A",
          "picUrl": "https://p1.music.126.net/pic.jpg",
          "playCount": 1234567,
          "copywriter": "编辑推荐"
        }
        """

        const val SONG_URL_RESPONSE = """
        {
          "data": [
            {"id": 347230, "url": "https://m10.music.126.net/song.mp3", "br": 320000, "size": 8600000, "level": "exhigh"},
            {"id": 999999, "url": null, "br": 0, "size": 0, "level": null, "fee": 1}
          ],
          "code": 200
        }
        """

        const val TOPLIST_ARRAY = """
        [
          {"id": 19723756, "name": "飙升榜", "coverImgUrl": "https://p1.music.126.net/a.jpg", "updateFrequency": "每天更新"},
          {"id": 3779629, "name": "新歌榜", "coverImgUrl": "https://p1.music.126.net/b.jpg", "updateFrequency": "每天更新"},
          {"name": "更多"}
        ]
        """

        const val LYRIC_RESPONSE = """
        {
          "lrc": {"version": 1, "lyric": "[00:01.00]Hello"},
          "tlyric": {"version": 1, "lyric": "[00:01.00]你好"},
          "code": 200
        }
        """
    }
}
