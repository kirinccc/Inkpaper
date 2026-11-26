# InkPaper 书源治理、全链路自愈与合规解耦技术方案

> 适用项目：InkPaper（基于 Legado 阅读内核与 Jetpack Compose 重写的 Android 极简纸墨阅读器）
> 方案目标：系统性解决“搜得到但看不了 / 源短命 / 公开仓库合规隐患”三大核心问题，实现站内全本阅读闭环、少而精的高可用源池，以及公开仓库干净与个人手机“开箱即用”兼得的工作流。

---

## 一、现状与核心痛点诊断

结合当前仓库源码分析，痛点根源在于**搜索、正文加载、巡检验证、书源分发**四段链路相互割裂，缺少运行时质量反馈闭环：

1. **搜索排序与正文可用性脱钩**：
   `SearchModel.kt` 的 `mergeItems()` 按 `(name, author)` 合并多源后，仅按 4 级匹配桶和来源数 `origins.size` 排序，默认沿用先返回或静态权重高的源。像 `7xdian.com` 这类“搜索与目录走快接口、VIP 正文已失效”的半残源，因响应快且在 `bookSources.json` 中权重高，会稳定霸占默认首选。
2. **正文层缺少语义验真与自动熔断**：
   `WebBook.kt` 的 `getContentAwait` 只要解析出非空字符串就落盘并交由 `ReadBook.kt` 渲染，无法识别“试读截断 / VIP 付费提示 / 广告错误页 / 未解密 PUA 字体乱码”，也不会反向降权或触发自动换源。
3. **巡检止步于浅层免费章**：
   `tools/check_book_sources.py` 仅校验 GET 搜索页 HTTP 状态与明显假活跳转；App 内置的 `CheckSourceChapterSelection.kt` 也仅固定抽检第 1 章（绝大多数站点的第 1 章均为免费章），未覆盖第 100~200+ 章等深层 VIP 正文。
4. **敏感规则与主仓库强耦合**：
   `bookSources.json` 直接打包在公开仓库与 APK 中，既带来开源版权合规隐患，也导致书源维护周期绑死在客户端发版周期上；同时 `RuleSub` 订阅同步“只增不减”，无法淘汰远端已删的死源。

---

## 二、技术架构升级：从“静态源池”走向“闭环自愈引擎”

```mermaid
flowchart LR
    A["全链路深水区验源<br/>(首章 + 深层/VIP章)"] --> B["书源分层与角色解耦<br/>(线索目录源 vs 全本正文源)"]
    B --> C["搜索归并主源优选<br/>(effectiveScore 抢占主源)"]
    C --> D["阅读器正文质量判定<br/>(异常熔断 + 后台无感整书切源)"]
    D -->|EMA 动态升降权| C
```

### 2.1 站内动态源健康度反馈与重排（`SourceHealth`）

在 Room 数据库（`AppDatabase v114`）中新增 `source_health` 表（外键级联关联 `book_sources.bookSourceUrl`）：

| 字段名 | 类型 | 作用说明 |
|---|---|---|
| `sourceUrl` | `String (PK)` | 关联 `book_sources.bookSourceUrl` |
| `contentOkCount` | `Int` | 正文验真通过次数 |
| `contentFailCount` | `Int` | 正文请求/质量校验失败次数 |
| `consecutiveFailCount` | `Int` | 连续失败次数（成功后归零，用于触发熔断） |
| `truncatedCount` | `Int` | 命中试读截断次数 |
| `adErrorCount` | `Int` | 命中广告错误页/VIP 拦截页/防爬页次数 |
| `garbledCount` | `Int` | 命中未解密 PUA 字体或乱码次数 |
| `avgContentLatencyMs` | `Long` | 正文平均加载耗时（EMA 平滑） |
| `circuitOpenUntil` | `Long` | 熔断截止时间戳（毫秒），未到期前沉底 |
| `lastFailReason` | `String` | 最近一次失败原因，用于换源面板提示 |
| `updatedAt` | `Long` | 最近更新时间 |

**核心接入点**：
- **综合评分 `effectiveScore`**：熔断期内（`now < circuitOpenUntil`）直接返回 `-10000` 沉底；正常源综合静态 `weight`、`customOrder`、贝叶斯平滑成功率 `(ok + 2) / (ok + fail + 3)`、质量奖惩与延迟微调。
- **搜索结果主源择优（`SearchModel.kt`）**：启动搜索时一次性加载内存评分快照 `sourceScoreMap`。在 `mergeItems()` 合并同名同作者结果时，若后到达的健康全本源评分高于先到达的半残源，则以高分源实例整体替换主 `SearchBook`（保留原 `origins` 并择优继承高清封面与简介）。
- **换源面板智能排序（`InkChangeSourceSheet.kt`）**：候选源按 `effectiveScore` 降序排列，副标题展示状态标签（如 `正文稳定`、`熔断冷却中`、`近期异常: ...`）。

### 2.2 正文语义校验器（`ContentQualityValidator`）与无感熔断降级

在 `WebBook.getContentAwait` 解析得到正文后、写入 `BookHelp` 本地缓存之前，执行纯本地语义校验：

1. **空正文（`EMPTY`）**：剥离 HTML 标签、空白与重复章名后有效字符为 `0`。
2. **广告与拦截页（`AD_OR_BLOCK_PAGE`）**：有效字符 `< 800` 且命中高置信度特征（如 `本章为VIP章节`、`请下载官方App`、`订阅本章`、`Just a moment...`、`Cloudflare`、`Access Denied`、`正在手打中`、`接口已失效` 等）。
3. **字体未解密/乱码（`GARBLED_CONTENT`）**：PUA 私有区字符（`\uE000..\uF8FF`）数量 `>= 5` 或占比 `> 1%`，或替换乱码符 `\uFFFD >= 8`。
4. **半残试读截断（`TRUNCATED_PREVIEW`）**：
   - 标题命中短章白名单（`请假|通知|感言|说明|公告|楔子|序章|后记|完结` 等）且有效字符 `>= 20` 放行；
   - 有效字符 `< 180` 直接判为残章；
   - 有效字符在 `180..599` 之间，若结尾命中截断引导语（`...`、`……`、`未完待续`、`阅读全文`、`登录后继续阅读`）或非正常句末标点结尾，判为试读截断。

**分级熔断与整书无感换源闭环（`InkAutoSourceFallback`）**：
- **短熔断（6 小时）**：单源连续 `2` 次触发正文质量异常。
- **长熔断（7 天）**：单源连续 `5` 次触发异常。
- **阅读器预加载与翻页兜底**：当 `CacheBook.downloadAwait` 捕获 `ContentQualityException` 或 `ContentEmptyException` 时，后台自动从健康候选源拉取目录并按规范化章节名对齐目标章；一旦拿到健康正文，直接静默切换该书绑定的 `Book` 主源与目录（继承阅读进度与书架状态），实现真正的站内全本闭环。

### 2.3 全链路自动化验源体系：双轨替代纯 HTTP 脚本

- **轨 A（`tools/check_book_sources.py`）**：秒级预筛——JSON Schema 校验、DNS/TLS 连通性、假活跳转识别、四类基准测试词矩阵（连载 VIP、完本长篇、改名热书、近期新书）及 `--output-verified` 精简输出。
- **轨 B（`BookSourceFullChainVerifyTest`）**：直接复用 `WebBook` 内核完成 `Search -> BookInfo -> TOC -> 首章 + 深水区/VIP 章 Content` 四步闭环，并接入 `ContentQualityValidator` 产出 `TIER_A / TIER_B / HALF_DEAD / DEAD` 分级报告。

### 2.4 WebView 过盾与本地解析取舍

- **搜书阶段**：100% 纯 HTTP 并发，禁止拉起 WebView，避免阻塞搜书池。
- **正文/目录阶段**：主力走 OkHttp + 本地 JS/字体映射还原；轻量 JS 盾仅在首次遇阻时借 WebView 获取 Cookie 回写 `CookieJar`；强验证/强制登录墙源移出自动池。

---

## 三、书源生态与合规解耦方案

1. **双 Flavor 构建解耦（`app` vs `oss`）**：
   - `app` 变体保持既有 `applicationId`（兼容本地覆盖安装），支持从 `.gitignore` 忽略的 `local.properties` 注入个人私有订阅地址与鉴权 Token（`BuildConfig.INKPAPER_DEFAULT_SUB_URL` / `INKPAPER_DEFAULT_SUB_TOKEN`）。
   - `oss` 变体（`app/src/oss/assets/defaultData/bookSources.json` 为 `[]`，`BuildConfig.IS_OSS_BUILD = true`）彻底剥离内置第三方书源与硬编码社区订阅芯片，保证公开仓库与开源构建包零侵权规则残留。
2. **动态 URL 路由解耦（`InkBookUrlResolver`）**：
   - 移除对特定商业站点的硬编码绑定，改为遍历本地已安装书源的 `bookUrlPattern` 动态匹配与解析。
3. **统一订阅同步与镜像死源清理（`InkRuleSubSync`）**：
   - 收口 `RuleUpdate` 与 `InkBookSourcesViewModel` 的订阅拉取逻辑，支持 `AnalyzeUrl` 自定义请求头与 Bearer Token 鉴权；
   - 对开启镜像模式（`@mirror`）的订阅，自动禁用远端已剔除的同源归属旧源，保证本地源池常年少而精。
4. **冷启动引导（`InkBookshelfScreen`）**：
   - 当书架为空且已启用书源数为 `0` 时，展示纸墨风格引导卡片，提供「导入本地书籍」「添加书源订阅」「WebDAV 恢复」三个一键入口。
