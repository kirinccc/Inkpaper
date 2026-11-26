# InkPaper 前端重构阶段说明

> 基于 Legado 完整内核，使用 **Jetpack Compose + 黑白灰纸墨 Design System** 推翻重写 Android 前端，做到主流程极简克制、全页面风格 100% 统一。

---

## Phase 1：底座与核心阅读闭环（已完成）

- **Compose 底座与字体接入**：接入 Kotlin Compose Compiler 与 Compose BOM，打通 `ChapterProvider` 内置字体加载（包含霞鹜文楷 `LXGWWenKai-Regular.ttf`）。
- **InkPaper 黑白灰纸墨设计系统**（`io.legado.app.inkpaper.design`）：
  - 零科技蓝、零彩色勾线，仅以纸面明暗与 `0.5dp` 极细线建立秩序；
  - 提供 4 套克制预设（**素纸 / 冷雾 / 墨夜 / 纯黑**）+ 跟随系统深色；
  - 统一大标题顶栏、胶囊筛选、圆角分组卡片、极简开关、分段器及**无图自动纸感竖排默认封面**。
- **全新单 Activity 主壳与核心页面**（`InkMainActivity`）：
  - 全屏 Edge-to-Edge + 极简 3 Tab 底栏（**书架 / 发现 / 我的**）；
  - **书架**：直连 Room 实时流、胶囊分组筛选、可选「继续阅读」单行卡片、3 列网格/列表切换、长按操作抽屉；
  - **全局聚合搜索**：复用 `SearchModel` 多源并发检索、搜索历史胶囊、多源归并展示；
  - **书籍详情**：居中纸感封面、墨黑主按钮、加入书架切换、Compose 目录抽屉；
  - **发现 / 我的**：书源发现列表、阅读统计卡、4 套主题实时切换及二三级页面入口。
- **阅读器 Compose 悬浮菜单与高频设置抽屉**：
  - 保留原有 `PageView` 排版渲染引擎，重写外围悬浮顶底栏（章节拖动条 + 目录 / 听书 / 夜间 / 设置）；
  - 设置抽屉支持字号、字体（含霞鹜文楷）、5 种纸感/墨夜背景、翻页动画、3 档排版间距及亮度调节。

---

## Phase 2：阅读完整体验与发现流（已完成）

- 阅读器内部交互全 Compose 化：
  - **目录 / 书签 / 划线笔记统一抽屉**（`InkReaderTocSheet`）：3 段切换、章节搜索、正倒序、自动定位当前章、书签与笔记直跳/删除；
  - **一键换源抽屉**（`InkChangeSourceSheet`）：并发搜源、进度条实时展示、点击直接拉取目录并换源续读；
  - **极简听书（TTS）面板**（`InkReadAloudSheet`）：播放/暂停、上下段、语速调节与跟随系统、定时停止、朗读引擎入口；
  - **更多排版设置二级抽屉**（`InkMoreTypographySheet`）：左右/上下边距、字距、首行缩进、正文字重、章节标题位置、段落阅读模式、净化替换、状态栏显隐与自动翻页速度。
- 原生**发现与 RSS 订阅流**：
  - `InkDiscoverScreen` 顶部新增「书源发现 / RSS 订阅」分段切换；
  - 新增 `InkExploreBooksScreen` 原生 Compose 发现书籍列表页，支持分类胶囊、分页加载与直达新书籍详情页。

---

## Phase 3：我的、全套设置与书源同步（已完成）

- 彻底淘汰日常路径上的 `PreferenceFragment`，全部二级管理页 Compose 化：
  - **书源管理与导入**（`InkBookSourcesScreen`）：搜索、状态/分组胶囊筛选、行内一键启停、置顶/发现开关/删除抽屉、链接/JSON/本地文件导入与批量启停；
  - **替换净化规则**（`InkReplaceRulesScreen`）：搜索筛选、行内启停、原生规则编辑器抽屉（正则/范围/正文标题开关）、链接/JSON/本地文件导入；
  - **备份与恢复**（`InkBackupScreen`）：WebDAV 配置保存与连通测试、一键云端备份、云端备份列表恢复、本地目录导出与 ZIP 恢复；
  - **阅读与通用习惯**（`InkReadHabitsScreen`）：启动续读、音量键翻页、防误触返回、屏幕常亮时长、精准搜索、预下载章节数与并发线程数；
  - **本地书籍快捷导入**（`InkLocalImportSheet`）：支持 SAF 一键多选文件直接导入，并保留按文件夹扫描入口。

---

## Phase 4：边角收口与精细打磨（已完成）

- 补全「我的」全部二级与三级原生页面：
  - **阅读记录**（`InkReadRecordsScreen`）：累计阅读时长统计、最近阅读/阅读时长排序、点击直达阅读、单条删除；
  - **全部书签与笔记**（`InkAllBookmarksScreen`）：书签/划线笔记分段切换、全局搜索、点击直跳原书对应位置、单条删除；
  - **高级工具与关于**（`InkAdvancedToolsScreen`）：Web 服务 / MCP 服务开关与地址展示、自动任务、TXT 目录/字典/RSS 规则入口、InkPaper 品牌卡片与版本许可；
- **动效与品牌重塑**：
  - `InkNavHost` 增加细腻的横向微滑入与淡入淡出页面转场；
  - 应用名称全语言统一为 **InkPaper**；
  - 更换极简黑白灰纸墨矢量自适应桌面图标（`ic_inkpaper_launcher_bg` / `ic_inkpaper_launcher_fg`）与素纸冷启动欢迎页。

---

## Phase 5：搜书覆盖率与书源治理（已完成）

- **书源底座清洗与三层分组**：
  - 新增 `tools/check_book_sources.py` 巡检脚本，剔除死站及已失效的未鉴权番茄 `api5` 接口源；
  - 将 `bookSources.json` 重整为「精选默认启用 / 长尾备用 / 官方需登录」三层结构，并在 `DefaultData` / `LocalConfig` 补齐首次安装自动 Seed。
- **搜索链路升级与改名书破局**：
  - 放开 `InkSearchScreen` 分组范围筛选与精准匹配开关，接入 `InkSearchQueryHelper` 规范化清洗与零结果/少结果改搜建议胶囊；
  - 详情页作者名支持点击直达该作者全部作品搜索；
  - 零结果兜底支持一键启用备用源重搜、内置浏览器检索作者/曾用名，以及通过 `InkAddBookUrlSheet` 粘贴书籍网址或 19 位番茄 `book_id` 直达解析。
- **书源治理闭环**：
  - `InkBookSourcesScreen` 接入 `CheckSource` 批量校验、失效源一键禁用、需登录标识与 `SourceLoginActivity` 入口，以及基于 `RuleSub` 的远程书源订阅管理。

---

## Phase 6：跨改名换源、假活源治理与订阅自动刷新（已完成）

- **假活源与空返回源深度清理**：
  - 升级 `tools/check_book_sources.py` 自动识别域名停放跳转、短 JS 重定向、`[api]` 返回 HTML 首页等假活源，以及热门词空列表响应，进一步净化 `bookSources.json`。
- **跨改名书换源与详情页换源**：
  - `InkChangeSourceSheet` 接入智能书名/作者匹配，新增「按作者找改名源」模式并在候选结果中标注站内旧书名；
  - `InkBookDetailScreen` / `InkBookDetailViewModel` 新增「切换书源」入口及空目录快捷换源，换源时自动继承阅读进度与书架状态。
- **剪贴板感知、书架直达入口与自动订阅刷新**：
  - 抽取公共 `InkBookUrlResolver` 并在书架「更多」菜单接入「链接 / 番茄 ID 加书」，`InkAddBookUrlSheet` 支持自动识别剪贴板链接/19 位 ID 一键填入；
  - `DefaultData.syncAutoUpdateBookSourceSubs()` 接入 `App` 启动后台协程，按订阅间隔静默增量刷新已开启自动更新的书源订阅。

---

## Phase 7：搜书体验收口与打包验收（已完成）

- **内置浏览器回搜与直达加书**：`WebViewActivity` 新增「用选中文字搜书」与「用当前网址加书」菜单项，从搜索页拉起外部查书后可一键带回作者/旧书名重搜或直接解析入架。
- **老用户书源一键重置**：`DefaultData` 新增 `resetToDefaultBookSources()`，并在 `InkBookSourcesScreen` 批量菜单提供「重置为内置精选书源（清空旧源）」。
- **搜书分组记忆与免费源优先级**：`LocalConfig.lastSearchGroup` 持久化记忆上次选中的搜书分组；重整 `bookSources.json` 的 `weight` 与 `customOrder` 权重层级，并在 `InkChangeSourceSheet` 按 `originOrder` 优先展示免费精选源。

---

## Phase 8：番茄实时线索发现、噪声折叠与社区书源扩充（已完成）

- **实时平台线索发现**：新增 `InkPlatformHintFetcher` 对接公开 `novel.snssdk.com` 搜索接口；当本地书源未强命中时，自动在结果页顶部呈现「番茄书库关联结果」，直接展示原名/曾用名、作者与简介，支持一键改搜或直达站内阅读。
- **弱匹配噪声折叠与常用社区书源**：新增 `InkSearchQueryHelper.isStrongSearchMatch` 将无关模糊单字结果折叠，保持搜书页素净；新增「XIU2 / YCK / Yiove」1-Tap 精选订阅推荐芯片与长尾稳定免费源。

---

## Phase 9：番茄官方检索与全本直连原生阅读（已完成）

- **内置「番茄小说」直连精选书源**：集成 `novel.snssdk.com` 搜索 + `fanqienovel.com` 官方详情与目录 + 全本正文直连（含网页端 PUA 字体映射解密兜底），支持直接拉取完整目录并进入 InkPaper 原生阅读器阅读全本章节。
- **站内无缝直达与增量书源升级**：`InkPlatformHintsCard` 由跳转外部网页改为调用 `InkBookUrlResolver` 站内直达书籍详情页与阅读器；`LocalConfig.needUpBookSources`（v2）与 `DefaultData.importDefaultBookSources` 升级为增量补齐内置源并清理已失效旧源。
- **弱相关噪声折叠**：新增 `InkSearchQueryHelper.isStrongSearchMatch`，当远程源返回大量无关单字分词结果时自动折叠弱相关条目，确保改名/兜底面板始终置顶可见。
- **书源质量修正与社区订阅预设**：下线失效的 `reading.snssdk.com#lcs`，将易产生单字噪声的 `移动阅读` 降级为长尾备用，修复 `潇湘书院` 作者名 PUA 乱码，新增实测可用的 `大唐小说 / 塔读小说 / SF轻小说`，并在订阅管理提供 `XIU2 / YCK / Yiove` 一键订阅预设。
