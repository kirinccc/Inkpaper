# InkPaper

> 黑白灰纸墨极简 Android 阅读器 —— 保留 Legado 完整内核与书源生态，前端 100% 基于 **Jetpack Compose** 推翻重写。

---

## 核心特性

- **黑白灰纸墨 Design System**
  - 零科技蓝、零彩色勾线，仅以纸面明暗层级与 `0.5dp` 极细线构建阅读秩序。
  - 内置 4 套克制主题预设：**素纸**（暖纸日间）、**冷雾**（清爽日间）、**墨夜**（护眼深色）、**纯黑**（AMOLED），支持跟随系统深色。
  - 双字体排版策略：UI 界面清晰利落；默认封面、正文与统计数字使用衬线体 / 内置**霞鹜文楷**（`LXGWWenKai-Regular.ttf`）。
- **核心阅读体验**
  - **书架**：直连 Room 实时流、胶囊分组筛选、可选「继续阅读」单行卡片、网格 / 列表视图切换、无图自动生成纸感竖排默认封面。
  - **全局聚合搜索 & 原生发现 / RSS**：多源并发检索、自动归并多源结果；原生支持书源发现分类流与 RSS 订阅切换。
  - **沉浸阅读器**：100% 保留 Legado 原生 `PageView` 排版与翻页引擎，外围悬浮顶底栏、高频设置抽屉、目录/书签/划线笔记抽屉、一键换源、极简听书（TTS）、更多排版设置全部 Compose 化。
- **全页面风格统一**
  - 彻底淘汰 `PreferenceFragment` 与系统弹窗：书源管理与导入、替换净化规则与编辑器、WebDAV 云端/本地备份恢复、阅读与通用习惯、阅读记录、全部书签与笔记、高级工具与服务开关全套统一。

---

## 重构阶段说明

详细阶段交付记录见：[docs/INKPAPER_PHASES.md](docs/INKPAPER_PHASES.md)

- **Phase 1**：底座与核心阅读闭环（已完成）
- **Phase 2**：阅读器全 Compose 抽屉与原生发现 / RSS 流（已完成）
- **Phase 3**：书源 / 净化 / WebDAV 备份 / 阅读习惯设置全 Compose 化（已完成）
- **Phase 4**：阅读记录 / 书签笔记 / 高级工具收口与 InkPaper 品牌图标重塑（已完成）

---

## 技术栈与构建

- **语言与架构**：Kotlin + Jetpack Compose + Navigation Compose + Room + Coroutines
- **构建调试包**：
  ```bash
  ./gradlew :app:assembleAppDebug
  ```

---

## 致谢与协议

- 底层书源引擎、正文排版、听书与同步内核基于开源项目 [gedoor/legado](https://github.com/gedoor/legado) 与 [LegadoTeam/legado](https://github.com/LegadoTeam/legado)。
- 遵循原项目 [LICENSE](LICENSE)（GNU General Public License v3.0）。
