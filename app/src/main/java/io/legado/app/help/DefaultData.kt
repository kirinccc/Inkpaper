package io.legado.app.help

import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearSharedGlobalState
import io.legado.app.model.BookCover
import io.legado.app.model.RuleUpdate
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx
import java.io.File

object DefaultData {

    fun upVersion() {
        if (LocalConfig.versionCode < AppConst.appInfo.versionCode) {
            Coroutine.async {
                if (LocalConfig.needUpHttpTTS) {
                    importDefaultHttpTTS()
                }
                if (LocalConfig.needUpTxtTocRule) {
                    importDefaultTocRules()
                }
                if (LocalConfig.needUpRssSources) {
                    importDefaultRssSources()
                }
                if (LocalConfig.needUpDictRule) {
                    importDefaultDictRules()
                }
                if (LocalConfig.needUpBookSources) {
                    importDefaultBookSources(overwriteExisting = false)
                }
            }.onError {
                it.printOnDebug()
            }
        }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                appCtx.assets.open("defaultData${File.separator}httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ThemeConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfig.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    val bookSources: List<BookSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}bookSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<BookSource>(json).getOrDefault(emptyList())
    }

    fun importDefaultBookSources(overwriteExisting: Boolean = false): Int {
        val sources = bookSources
        if (overwriteExisting) {
            if (sources.isNotEmpty()) {
                SourceHelp.insertBookSource(*sources.toTypedArray())
            }
            return sources.size
        }
        val existingCount = appDb.bookSourceDao.allCount()
        if (existingCount == 0) {
            if (sources.isNotEmpty()) {
                SourceHelp.insertBookSource(*sources.toTypedArray())
            }
            return sources.size
        }
        // 增量升级：清理失效内置源、停用已知半残源并补齐新增的内置精选源，不覆盖用户自定义源
        appDb.bookSourceDao.getBookSource("https://reading.snssdk.com#lcs")?.let { obsolete ->
            SourceHelp.deleteBookSource(obsolete.bookSourceUrl)
        }
        appDb.bookSourceDao.getBookSource("https://7xdian.com")?.let { brokenQidian ->
            if (brokenQidian.enabled || brokenQidian.weight > 0) {
                appDb.bookSourceDao.update(
                    brokenQidian.copy(
                        enabled = false,
                        weight = -50,
                        bookSourceGroup = "仅目录·半残停用",
                    ),
                )
            }
        }
        val missing = sources.filter { src ->
            appDb.bookSourceDao.getBookSource(src.bookSourceUrl) == null
        }
        if (missing.isNotEmpty()) {
            SourceHelp.insertBookSource(*missing.toTypedArray())
        }
        return missing.size
    }

    fun resetToDefaultBookSources(): Int {
        val existing = appDb.bookSourceDao.all
        if (existing.isNotEmpty()) {
            SourceHelp.deleteBookSources(existing)
        }
        val sources = bookSources
        if (sources.isNotEmpty()) {
            SourceHelp.insertBookSource(*sources.toTypedArray())
        }
        return sources.size
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.all
            .filter { it.id < 0 }
            .forEach { it.clearSharedGlobalState() }
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.all
            .filter { it.sourceGroup == "legado" }
            .forEach { it.clearSharedGlobalState() }
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    fun importDefaultDictRules() {
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

    suspend fun syncAutoUpdateBookSourceSubs() {
        val injectedSubUrl = BuildConfig.INKPAPER_DEFAULT_SUB_URL.trim()
        if (injectedSubUrl.isNotBlank() && appDb.ruleSubDao.findByUrl(injectedSubUrl) == null) {
            runCatching {
                appDb.ruleSubDao.insert(
                    RuleSub(
                        name = BuildConfig.INKPAPER_DEFAULT_SUB_NAME.ifBlank { "私有精选订阅" },
                        url = injectedSubUrl,
                        type = 0,
                        customOrder = 0,
                        autoUpdate = true,
                        silentUpdate = true,
                        update = 0L,
                        updateInterval = 24,
                        js = "@mirror",
                    ),
                )
            }.onFailure { it.printOnDebug() }
        }
        val now = System.currentTimeMillis()
        appDb.ruleSubDao.all
            .filter { it.type == 0 && it.autoUpdate && it.url.isNotBlank() }
            .forEach { sub ->
                val intervalHours = if (sub.updateInterval > 0) sub.updateInterval else 24
                if (sub.update + intervalHours * 3600_000L <= now) {
                    runCatching {
                        RuleUpdate.cacheSource(
                            sub.copy(silentUpdate = true, updateInterval = intervalHours),
                        )
                    }.onFailure {
                        it.printOnDebug()
                    }
                }
            }
    }

}
