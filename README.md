# Melody

> 开源的网易云音乐第三方客户端 · Android 手机 / 平板自适应
> Kotlin · Jetpack Compose · Material 3 · Media3 · Clean Architecture

[![Android CI](https://github.com/Wliky/Melody/actions/workflows/android.yml/badge.svg)](https://github.com/Wliky/Melody/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

Melody 是一个**面向学习与个人使用**的第三方音乐客户端。项目严格按《网易云第三方音乐客户端开发设计文档》实现：
Android 优先、领域层与网络层提前抽离、播放记录采用事件队列 + 可插拔同步 Provider。

---

## ✨ v0.2.0 更新要点

| 变更 | 说明 |
| --- | --- |
| **扫码登录修复** | 定位到根因：登录接口对请求头挑剔，必须带**移动端 UA** 且 `Referer` 指向 `/login`，否则 403 且响应里没有 `unikey`。登录链路改为 weapi → eapi 双通道兜底，识别风控码 8821，并新增 **Cookie 登录**作为兜底入口 |
| **界面重做** | 全局 Material 3 语义色板 + **封面取色**：播放页背景渐变、进度条、歌词高亮、播放按钮都跟随专辑封面。大圆角卡片、胶囊分段控件、播放中跳动指示条、分组式设置页、空态/错误态插画 |
| **底部导航 2 → 4** | 首页 / 搜索 / **历史** / 我的。手机用底部导航栏，≥600dp 自动切成侧边 NavigationRail |
| **高刷新率适配** | 启动时在屏幕支持的显示模式里挑「**同分辨率下刷新率最高**」的一档（90/120/144Hz），不再被锁在 60Hz；不切分辨率所以不会闪屏 |
| **自动同步，无手动入口** | 播放行为一产生就入队 → 防抖 3s 合并提交 → 断网恢复自动补交 → 5 分钟兜底重试。客户端里**不存在**任何「立即同步」按钮 |
| **测试 46 → 57 例** | 新增 Cookie 规整（8 例）与风控码识别（3 例），全部纯 JVM 跑在 CI 上 |

---

## ⚠️ 免责声明（请先读这一段）

- Melody 是**非官方**开源项目，与网易云音乐及其运营方**没有任何关联**，不代表其立场。
- 本项目**不破解会员、不绕过 DRM、不规避任何访问控制，也不提供未授权音乐下载**。
  它只调用你本人有权访问的数据与音源，音源地址仅用于在线播放，**不做本地留存**。
- 登录凭据保存在 Android Keystore 保护的存储中，不写入日志，也不会上传到任何第三方服务。
- 播放记录**默认只写入本机**（Room 本地历史，离线可看）。向服务端上报的通道仅在
  **自建 API 服务**模式下才存在 —— 那时听歌记录会自动、无感地提交到**你自己部署的服务**；
  直连模式与演示模式下官方并未向第三方开放该接口，记录不会离开设备。
  你随时可以在设置里整体关掉上传，本地历史不受影响。
- 请勿将本项目用于任何商业用途。因使用本项目产生的任何后果由使用者自行承担。
- 如有侵权，请联系删除。

---

## 功能

已实现（对应文档 §19 Definition of Done）：

| 能力 | 状态 |
| --- | --- |
| 扫码登录 / **Cookie 登录**兜底，Session 持久化（Keystore 保护） | ✅ |
| 首页推荐 / 每日推荐 / 推荐歌单 / 排行榜 / 新歌 + 下拉刷新 | ✅ |
| 搜索（联想词、单曲 / 歌手 / 专辑 / 歌单分类、分页、搜索历史） | ✅ |
| 底部 4 Tab（首页 / 搜索 / 历史 / 我的）+ 迷你播放器 + 全屏播放器 | ✅ |
| Media3 播放、队列、循环 / 单曲循环 / 随机 | ✅ |
| 后台播放 + 通知栏 / 锁屏媒体控制（MediaSessionService） | ✅ |
| 歌词解析与滚动同步（支持翻译行、多时间标签、offset），逐行明暗聚焦 | ✅ |
| **封面取色**：播放页背景 / 进度条 / 歌词高亮随专辑封面变化 | ✅ |
| 本地播放历史（Room 持久化，离线可看） | ✅ |
| 播放事件队列 + 可插拔同步 Provider + 防抖自动提交 + 重试上限 | ✅ |
| 高刷新率屏幕适配（90 / 120 / 144Hz，同分辨率下取最高档） | ✅ |
| 网络异常 / 登录失效 / 资源不可用均有明确 UI 状态 | ✅ |
| 手机 / 平板自适应（600dp 断点，底栏 ↔ 侧边导航栏、双栏布局） | ✅ |
| 单元测试（57 个用例，歌词、加解密、事件队列、Cookie、风控码、DTO 兼容性、分页） | ✅ |

三种数据源模式，随时切换、无需等 App 更新：

| 模式 | 说明 |
| --- | --- |
| **演示模式（离线）** | 内置示例曲库，音频由客户端实时合成，零配置即可体验完整 UI 与播放器链路 |
| **直连模式（默认）** | 客户端直接请求公开接口，扫码 / Cookie 登录即可使用，无需自建服务 |
| **自建 API 服务** | 指向你自己部署的兼容服务（如 NeteaseCloudMusicApi），兼容性最好，也是唯一支持听歌记录上报的模式 |

---

## 下载与安装

### 直接下载（推荐）

最新 APK 由 CI 自动构建并发布到 Release：

**➡️ [下载 v0.2.0](https://github.com/Wliky/Melody/releases/tag/v0.2.0)**

| 文件 | 大小 | 用途 |
| --- | --- | --- |
| `melody-v0.2.0-<sha>-release.apk` | 约 16 MB | 日常安装使用 |
| `melody-v0.2.0-<sha>-debug.apk` | 约 23 MB | 含调试信息，排查问题时用 |

安装前需要在系统设置中允许「安装未知来源应用」。

> release 包在未配置正式签名时使用 debug 签名，保证任何一次构建产物都可直接安装。
> 要发布正式版，请在 `app/build.gradle.kts` 的 `release` 中替换为自己的 `signingConfig`。

### 登录不上怎么办

扫码登录已经修好了走的是移动端链路，但网易的风控是动态的 —— 如果仍然提示风控（8821）或二维码异常，
请改用 **Cookie 登录**：

1. 电脑浏览器登录 [music.163.com](https://music.163.com)，按 F12 打开开发者工具；
2. Application → Cookies → `https://music.163.com`，找到 `MUSIC_U`；
3. 复制**整行**（`MUSIC_U=xxxx...`）或只复制值，粘贴到 App 的登录页 Cookie 输入框。

粘贴内容怎么写的都行：整段 Cookie、只给值、带 `Cookie:` 前缀、带引号、带换行，客户端都会自动规整。

### 每次提交自动构建

推送到任意分支都会触发一次完整构建（单测 → debug APK → release APK → 上传产物）：

1. 打开 [Actions](https://github.com/Wliky/Melody/actions/workflows/android.yml) → 选择最新一次成功的 run；
2. 在页面底部的 **Artifacts** 里下载 `melody-apk-<sha>`（内含 debug 与 release 两个 APK）。
3. 推 `v*` tag 时，除上述流程外还会自动创建 [Release](https://github.com/Wliky/Melody/releases) 并附上两个 APK。

## 从源码构建

```bash
git clone https://github.com/Wliky/Melody.git
cd Melody
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 跑单元测试
```

环境要求：JDK 17、Android SDK（compileSdk 35）。minSdk 24。

---

## 架构

```
Compose UI ──▶ ViewModel ──▶ Repository ──▶ DataSource ──▶ NetEase API / Room / DataStore
播放器独立链路：Compose UI ──▶ PlayerViewModel ──▶ PlayerController ──▶ Media3 ──▶ MediaSessionService
自动同步链路：播放事件 ──▶ 事件队列(Room) ──▶ SyncManager(防抖/补交/重试) ──▶ SyncProvider ──▶ 服务端
```

```
app/src/main/java/com/wliky/melody/
├── core/
│   ├── common/        统一 Result / Error 模型、调度器、网络监听
│   ├── model/         领域模型（与平台、与接口完全解耦）
│   ├── network/       ApiClient（唯一 HTTP 出口）+ 宽松 JSON 解析 + Cookie 规整
│   ├── crypt/         weapi / eapi 参数签名所需的加解密（纯 JVM，可测）
│   ├── database/      Room 实体与 DAO（本地历史、事件队列、搜索历史）
│   ├── datastore/     DataStore 设置（主题、数据源、音质、上报开关）
│   ├── security/      Keystore 保护的登录凭据存储 + 持久化设备号
│   ├── designsystem/  Material 3 主题、间距令牌、通用组件、封面取色、高刷适配、格式化
│   ├── lyric/         LRC 解析（纯函数，独立于 UI）
│   └── player/        PlayerController 抽象、Media3 实现、后台服务、事件记录器
├── data/
│   ├── netease/       三个可插拔数据源 + DTO + 映射 + 请求签名
│   ├── repository/    八个 Repository + 同步 Provider + 自动同步调度器
│   └── di/            data 层绑定
├── feature/           按功能分包：home / search / player / playlist / history / profile / settings / auth
└── navigation/        路由与手机 / 平板自适应外壳
```

关键设计（对照文档）：

- **接口适配隔离**：Compose 与 ViewModel 从不直接碰网易云字段，全部经 `NeteaseDataSource` 适配层（§7）。
- **懒解析音频地址**：队列里先放 `melody://song/<id>` 占位，真正播放时才换真实地址，
  于是通知栏 / 锁屏的上一首下一首天然可用，且不会一次请求几百个地址。
- **播放事件队列**：只累计「有效播放时长」，拖动进度条造成的跳变不计入；事件以 UUID 为主键，
  插入用 IGNORE，天然幂等（§9）。
- **可插拔同步 Provider**：`SyncProvider` 接口 + `@IntoSet` 多绑定，新增同步目标只需加一个绑定。
- **自动同步而非手动**：`SyncManager` 订阅队列长度与网络状态，全自动提交；`Mutex` 串行化
  保证定时器 / 网络恢复 / 新事件不会重复提交同一批数据。
- **字段容错**：ID 统一 String，数字字段用宽松序列化器（同一字段传 number 或 string 都能吃下），
  未知字段不会导致解析失败（§16 接口变化需可恢复）。
- **登录双通道 + Cookie 兜底**：直连模式登录先走 weapi（移动端 UA + `/login` Referer），
  失败或缺字段自动降级 eapi；两者都不通则引导用户用 Cookie 登录，不让用户卡死在二维码上。

更多细节见 [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)。

---

## 开发进度（对应文档 §14 里程碑）

- [x] **M1** 项目骨架、主题、导航、网络层、统一异常处理
- [x] **M2** 扫码登录、Session、用户信息
- [x] **M3** 首页、搜索、歌曲详情、基础播放
- [x] **M4** 全屏播放器、队列、后台播放、锁屏控制
- [x] **M5** 我的、歌单、收藏、播放历史
- [x] **M6** 本地播放事件队列、同步 Provider、重试机制
- [x] **M7** 歌词、动态主题、缓存、动画
- [x] **M7.5** 视觉重构（封面取色 / M3 语义色板 / 4 Tab 导航 / 高刷适配）· 登录风控修复 · 全自动同步
- [ ] **M8** 崩溃监控、Release 签名、隐私与合规审查（进行中）

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖 57 个用例：歌词解析（多时间标签 / offset 平移 / 翻译合并）、加解密（AES-CBC/ECB 用标准实现解密验证、
Base64 与 JDK 实现逐字节一致、RSA 输出长度）、播放事件队列（幂等、暂停不计时、拖动不计时、
重试上限、不支持时 SKIPPED）、Cookie 规整（整段 Cookie / 只给值 / 前缀引号换行 / 非法输入拒绝）、
登录风控码识别、DTO 兼容性（数字/字符串 ID、字段缺失、未知字段、顶层数组）、分页与格式化。

全部为**纯 JVM 单测**，不依赖 Robolectric 或 Android 设备，因此 CI 上可以稳定跑。

## 更新日志

### v0.2.0

- 修复扫码登录（移动端 UA + `/login` Referer，weapi → eapi 双通道，8821 风控识别），新增 Cookie 登录兜底。
- 界面重构：M3 语义色板、封面取色、大圆角卡片、胶囊控件、跳动播放指示条、分组设置页、空/错误态插画。
- 底部导航扩到 4 项（首页 / 搜索 / 历史 / 我的），平板侧边导航同步。
- 适配高刷新率屏幕（同分辨率取最高刷新率档）。
- 播放记录改为**全自动**：入队即防抖提交、断网恢复补交、定时兜底重试，移除所有手动同步入口。
- 单元测试 46 → 57 例。

### v0.1.0

- 首个版本：骨架、主题、导航、三种数据源、扫码登录、首页 / 搜索 / 播放器 / 歌单 / 历史、歌词、后台播放。

## 参与贡献

欢迎 Issue 与 PR。提交前请确保：

1. `./gradlew testDebugUnitTest` 通过；
2. 不引入任何绕过访问控制、破解、盗链相关的代码 —— 这类 PR 会被直接关闭；
3. 关键逻辑补测试，小步提交。

## License

[MIT](./LICENSE) © Wliky
