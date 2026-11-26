package io.legado.app.inkpaper.source

import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RuleSub
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.association.BookSourceImportJson
import io.legado.app.ui.association.parseBookSourceJson
import io.legado.app.utils.isAbsUrl
import kotlinx.coroutines.currentCoroutineContext

/**
 * Result of synchronizing a book-source RuleSub subscription.
 */
data class InkRuleSubSyncResult(
    val parsedCount: Int,
    val updatedCount: Int,
    val disabledMirrorCount: Int,
    val parsedSources: List<BookSource>,
)

/**
 * Unified book-source subscription sync supporting AnalyzeUrl custom headers,
 * private Bearer token injection, and @mirror dead-source pruning.
 */
object InkRuleSubSync {

    private const val MIRROR_TAG_PREFIX = "[ink-sub:"

    suspend fun syncBookSourceSub(
        sub: RuleSub,
        applyChanges: Boolean = true,
    ): InkRuleSubSyncResult {
        val rawContent = fetchSubscriptionText(sub.url)
        val parsed = parseBookSources(rawContent, sub.url)
        if (parsed.isEmpty()) error("未解析到有效书源")

        val isMirrorMode = sub.js?.contains("@mirror", ignoreCase = true) == true ||
            sub.name.contains("@mirror", ignoreCase = true)
        val subTag = "$MIRROR_TAG_PREFIX${sub.id}]"
        val incomingUrls = parsed.mapTo(HashSet()) { it.bookSourceUrl }
        val toInsert = mutableListOf<BookSource>()

        for (incoming in parsed) {
            val local = appDb.bookSourceDao.getBookSource(incoming.bookSourceUrl)
            val needsUpdate = local == null || local.lastUpdateTime < incoming.lastUpdateTime ||
                (isMirrorMode && local.bookSourceComment?.contains(subTag) != true)
            if (needsUpdate) {
                if (local != null && !local.bookSourceGroup.isNullOrBlank()) {
                    incoming.bookSourceGroup = local.bookSourceGroup
                }
                if (isMirrorMode) {
                    val existingComment = incoming.bookSourceComment.orEmpty()
                    if (!existingComment.contains(subTag)) {
                        incoming.bookSourceComment = if (existingComment.isBlank()) {
                            subTag
                        } else {
                            "$existingComment\n$subTag"
                        }
                    }
                }
                toInsert.add(incoming)
            }
        }

        var disabledMirrorCount = 0
        if (applyChanges) {
            if (toInsert.isNotEmpty()) {
                SourceHelp.insertBookSource(*toInsert.toTypedArray())
            }
            if (isMirrorMode) {
                val staleSources = appDb.bookSourceDao.all.filter { src ->
                    src.enabled &&
                        src.bookSourceComment?.contains(subTag) == true &&
                        src.bookSourceUrl !in incomingUrls
                }
                if (staleSources.isNotEmpty()) {
                    val disabled = staleSources.map { it.copy(enabled = false) }
                    appDb.bookSourceDao.update(*disabled.toTypedArray())
                    disabledMirrorCount = disabled.size
                }
            }
            appDb.ruleSubDao.update(sub.copy(update = System.currentTimeMillis()))
        }

        return InkRuleSubSyncResult(
            parsedCount = parsed.size,
            updatedCount = toInsert.size,
            disabledMirrorCount = disabledMirrorCount,
            parsedSources = parsed,
        )
    }

    suspend fun fetchSubscriptionText(url: String): String {
        val trimmed = url.trim()
        // Support AnalyzeUrl option suffix e.g. https://... ,{"headers":{"Authorization":"Bearer ..."}}
        if (trimmed.contains(",{")) {
            val analyzeUrl = AnalyzeUrl(
                mUrl = trimmed,
                coroutineContext = currentCoroutineContext(),
            )
            return analyzeUrl.getStrResponseAwait().body.orEmpty()
        }
        val cleanUrl = if (trimmed.endsWith("#requestWithoutUA")) {
            trimmed.substringBeforeLast("#requestWithoutUA")
        } else {
            trimmed
        }
        val defaultSubUrl = BuildConfig.INKPAPER_DEFAULT_SUB_URL
        val defaultSubToken = BuildConfig.INKPAPER_DEFAULT_SUB_TOKEN
        val shouldAttachBearer = defaultSubUrl.isNotBlank() &&
            defaultSubToken.isNotBlank() &&
            cleanUrl.startsWith(defaultSubUrl)

        return okHttpClient.newCallResponseBody {
            url(cleanUrl)
            if (trimmed.endsWith("#requestWithoutUA")) {
                header(AppConst.UA_NAME, "null")
            }
            if (shouldAttachBearer) {
                header("Authorization", "Bearer $defaultSubToken")
            }
        }.decompressed().text("utf-8")
    }

    private suspend fun parseBookSources(content: String, rootUrl: String): List<BookSource> {
        return when (val parsed = parseBookSourceJson(content.trim(), allowSourceUrls = true)) {
            is BookSourceImportJson.Sources -> parsed.items
            is BookSourceImportJson.SourceUrls -> {
                val all = mutableListOf<BookSource>()
                for (subUrl in parsed.items) {
                    if (!subUrl.isAbsUrl() || subUrl == rootUrl) continue
                    val subContent = fetchSubscriptionText(subUrl)
                    when (val subParsed = parseBookSourceJson(subContent.trim(), allowSourceUrls = false)) {
                        is BookSourceImportJson.Sources -> all.addAll(subParsed.items)
                        is BookSourceImportJson.SourceUrls -> Unit
                    }
                }
                all
            }
        }
    }
}
