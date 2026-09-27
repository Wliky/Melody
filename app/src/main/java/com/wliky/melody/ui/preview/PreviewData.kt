package com.wliky.melody.ui.preview

/**
 * 阶段 1 组件展示用的占位数据。
 * 阶段 2 接入真实接口后被领域模型取代，此文件届时删除。
 */
object PreviewData {

    data class Song(
        val title: String,
        val subtitle: String,
        val artworkUrl: String,
    )

    val songs = listOf(
        Song("晴天", "周杰伦 · 叶惠美", "https://picsum.photos/seed/melody1/300"),
        Song("消愁", "毛不易 · 平凡的一天", "https://picsum.photos/seed/melody2/300"),
        Song("起风了", "买辣椒也用券 · 起风了", "https://picsum.photos/seed/melody3/300"),
        Song("孤勇者", "陈奕迅 · 孤勇者", "https://picsum.photos/seed/melody4/300"),
    )
}
