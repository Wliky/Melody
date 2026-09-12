package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.model.CommentPage
import com.wliky.melody.core.model.CommentSort
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 评论仓库（v0.3.0-preview.3+）。
 *
 * 只读封装 —— 当前不做发送评论，所以不需要 ViewModel 层做请求排队、乐观更新、
 * 失败回滚。这一层只把分页 + 排序参数转给数据源，然后透传结果。
 *
 * 未来要加发送评论时，把这里的「写」能力做成新增函数即可，不要硬塞进 fetch。
 */
@Singleton
class CommentRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
) {

    suspend fun fetchComments(
        songId: String,
        sort: CommentSort = CommentSort.HOT,
        cursor: Long = 0L,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): AppResult<CommentPage> = appRunCatching {
        providers.current().songComments(songId = songId, sort = sort, cursor = cursor, limit = limit)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}