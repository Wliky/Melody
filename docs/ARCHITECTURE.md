# 架构说明

本文档记录 Melody 的关键设计决策，方便后续贡献者快速上手。

## 1. 分层与依赖方向

```
feature (Compose UI)
   │  只发意图，不碰网络
   ▼
feature/*ViewModel
   │  只调用 Repository，暴露不可变 State + StateFlow
   ▼
data/repository          ← 业务规则：缓存、分页、登录校验、错误语义化
   │
   ▼
data/netease (NeteaseDataSource 实现)   ← 唯一知道「网易云字段长什么样」的地方
   │
   ▼
core/network (ApiClient)  ← 唯一 HTTP 出口
```

播放器是**独立链路**，不经过 Repository：

```
Compose UI → PlayerViewModel → PlayerController(接口) → Media3PlayerController → MediaSessionService(ExoPlayer)
```

`core/player/SongUrlProvider` 是 core 定义的端口，实现放在 data 层
（`NeteaseSongUrlProvider`），这样 core 不需要知道任何接口细节。

## 2. 为什么是三个数据源

接口形态是不稳定的，把不确定性关在一个接口后面，换实现不牵动上层：

| 实现 | 用途 |
| --- | --- |
| `DirectNeteaseDataSource` | 直连。路径按官方客户端的做法把 `api` 段替换为 `weapi`，参数走 AES+RSA 签名 |
| `ApiServerNeteaseDataSource` | 指向自建兼容服务，纯 HTTP，最容易自行修 |
| `MockNeteaseDataSource` | 离线演示 + 测试替身，保证「接口全挂时 App 依然可用」 |

`BaseNeteaseDataSource` 承载所有响应解析与领域模型映射，子类只实现「怎么把请求发出去」
（`request`）与「参数名怎么规整」（`adaptPayload`），避免三份重复的解析代码。

## 3. 音频地址的懒解析

队列里的曲目先以 `melody://song/<songId>` 入队，`LazySongUrlDataSource`
（`ResolvingDataSource.Factory`）在真正开始加载某首歌时才把占位 URI 换成真实地址。

好处：

- 通知栏 / 锁屏 / 耳机的上一首下一首可用（队列是完整的一批 MediaItem）；
- 只对播放到的曲目发请求，不会为 500 首歌各请求一次地址；
- 解析失败会变成播放错误，UI 给出「该内容当前不可播放」，而不是静默卡住。

`NeteaseSongUrlProvider` 内部用 `runBlocking` 把挂起请求桥接到 ExoPlayer 的加载线程，
并带 10 分钟缓存（地址本身有时效）。

## 4. 播放事件队列

`PlaybackEventRecorder` 只做一件事：累计**有效播放时长**。

- 只在 `isPlaying` 为真时累加；
- 单次 tick 增量 > 5s 视为拖动进度条，不计入；
- 进度回退视为重新开始，只重置基准点；
- 一次播放累计不足 5s 不产生事件（快速划过不进历史）；
- 切歌 / 退出时结算，`eventId` 为 UUID 保证幂等。

`SyncRepository` 负责队列落库与提交：

- `insert` 使用 `OnConflictStrategy.IGNORE`，重复事件不会重复提交；
- 按 `startAt` 顺序批量提交；
- `FAILED` 累加 `retryCount`，达到 5 次后不再重试（不会无限打接口）；
- Provider 明确表示不支持时标记 `SKIPPED`，而不是让队列永远堆积。

`SyncProvider` 用 `@IntoSet` 多绑定注册，新增同步目标只需要多一个 `@Binds @IntoSet`。

## 5. 字段容错策略

第三方接口的字段会变，解析层必须比接口更皮实：

- 所有远端 ID 统一 `String`（`FlexibleStringSerializer` 能吃下数字或字符串）；
- 数字字段用 `FlexibleLongSerializer` / `FlexibleIntSerializer`；
- `Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }`；
- DTO 全部字段可空 + 默认值，缺失字段退化成 0 / null 而不是抛异常；
- 首页各区块（推荐 / 新歌 / 榜单单）独立兜底，一个接口挂了不影响整页；
  如果全部区块都失败，才把第一个真实错误抛给 UI。

## 6. 手机 / 平板自适应

- 断点：`screenWidthDp >= 600`（`rememberIsWideLayout()`）；
- 窄屏：底部两个主入口（首页 / 我的）+ 迷你播放器贴底；
- 宽屏：侧边 `NavigationRail`（首页 / 搜索 / 历史 / 我的）+ 迷你播放器贴内容区底部；
- 宽屏下推荐歌单直接铺成 4 列，搜索页用「结果列表 + 选中详情」双栏；
- 旋转 / 折叠屏变化通过 `android:configChanges` 处理，避免重建导致播放器闪断。

## 7. 测试策略

单元测试全部是**纯 JVM**（不依赖 Robolectric），因此跑得很快，也不会因为
`android.util.*` 返回 null 而假失败：

- 不再使用 `android.util.Base64`，自己实现了 `Base64Codec`，并用「与 JDK 实现逐字节一致」来验证；
- 加解密用 JDK 的 AES 反向解密来验证密钥 / IV / 填充处理正确；
- 播放事件记录器注入 `Clock`，可以精确断言时长累计；
- `SyncRepositoryTest` 用内存 DAO 假实现验证幂等、重试上限、SKIPPED 语义。

## 8. 后续演进

- 拆分 Gradle 模块（`:core:*` / `:feature:*`）以加快增量构建；
- 打开 R8（`app/proguard-rules.pro` 已预置序列化 / OkHttp / Media3 规则）；
- 引入 KMP 共享 domain 与 data 层，为桌面端做准备（文档 §13 第三阶段）；
- 崩溃监控与性能埋点。
