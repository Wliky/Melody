package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Comment
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 评论排序：推荐=99 / 最热=2 / 最新=3（wire 值为网易云 sortType）。
 */
enum class CommentSort(val wire: Int) {
    RECOMMEND(99),
    HOT(2),
    LATEST(3),
}

/**
 * 一页评论。
 *
 * @property cursor 下一页游标（最新排序沿用上页返回值；推荐/最热用偏移换算）
 */
data class CommentPage(
    val comments: List<Comment>,
    val totalCount: Int,
    val hasMore: Boolean,
    val cursor: String,
)

/**
 * 评论仓库（只读）。
 *
 * 列表接口：`/weapi/v2/resource/comments`，body `{threadId, pageNo, pageSize, cursor, sortType, showInner}`
 * → `data.comments[] / data.totalCount / data.hasMore / data.cursor`。
 *
 * 点赞/发表/回复等写操作暂不做：服务端对模拟器等非常规环境有设备风控
 * （返回「系统检测到您的设备存在安全风险」），交互层也已回滚，待后续处理。
 *
 * threadId 前缀：歌曲 `R_SO_4_` / 歌单 `A_PL_0_`。
 */
@Singleton
class CommentRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /** 歌曲评论线程 id。 */
    fun songThreadId(songId: Long): String = "R_SO_4_$songId"

    /** 歌单评论线程 id。 */
    fun playlistThreadId(playlistId: Long): String = "A_PL_0_$playlistId"

    /**
     * 评论列表（V2，支持排序与游标分页）。
     * 游标规则：推荐用偏移；最热用 `normalHot#偏移`；最新沿用上页 cursor。
     */
    suspend fun comments(
        threadId: String,
        pageNo: Int = 1,
        pageSize: Int = 30,
        sort: CommentSort = CommentSort.RECOMMEND,
        previousCursor: String? = null,
    ): AppResult<CommentPage> {
        val cursor = when (sort) {
            CommentSort.RECOMMEND -> ((pageNo - 1) * pageSize).toString()
            CommentSort.HOT -> "normalHot#${(pageNo - 1) * pageSize}"
            CommentSort.LATEST -> if (pageNo <= 1) "0" else (previousCursor ?: "0")
        }
        val body = buildJsonObject {
            put("threadId", threadId)
            put("pageNo", pageNo)
            put("pageSize", pageSize)
            put("cursor", cursor)
            put("sortType", sort.wire)
            put("showInner", true)
        }.toString()
        return when (val res = client.callWeapi("/weapi/v2/resource/comments", body)) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val data = res.data["data"]?.jsonObject
                AppResult.success(
                    CommentPage(
                        comments = data?.get("comments")?.jsonArray
                            ?.mapNotNull { parse(it.jsonObject) }
                            .orEmpty(),
                        totalCount = data?.get("totalCount")?.jsonPrimitive?.intOrNull ?: 0,
                        hasMore = data?.get("hasMore")?.jsonPrimitive?.booleanOrNull ?: false,
                        cursor = data?.get("cursor")?.jsonPrimitive?.content ?: cursor,
                    ),
                )
            }
        }
    }

    /** 解析一条评论。 */
    private fun parse(obj: JsonObject): Comment? {
        val id = obj["commentId"]?.jsonPrimitive?.longOrNull
            ?: obj["id"]?.jsonPrimitive?.longOrNull
            ?: return null
        val user = obj["user"]?.jsonObject
        return Comment(
            id = id,
            userNickname = user?.get("nickname")?.jsonPrimitive?.content.orEmpty(),
            userAvatarUrl = user?.get("avatarUrl")?.jsonPrimitive?.content,
            content = obj["content"]?.jsonPrimitive?.content.orEmpty(),
            timeMs = obj["time"]?.jsonPrimitive?.longOrNull ?: 0L,
            likedCount = obj["likedCount"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }
}
