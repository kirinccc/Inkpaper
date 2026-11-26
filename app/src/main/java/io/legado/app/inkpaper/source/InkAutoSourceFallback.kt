package io.legado.app.inkpaper.source

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.inkpaper.ui.search.InkSearchQueryHelper
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx
import java.util.concurrent.ConcurrentHashMap

/**
 * Automatically recovers from content quality / empty-body failures during
 * reading or prefetch by probing healthy alternative sources and performing a
 * seamless whole-book source switch.
 */
object InkAutoSourceFallback {

    private val switchMutex = Mutex()
    private val chapterFailCounts = ConcurrentHashMap<String, Int>()
    private val titleNormalizeRegex = Regex("[\\s\\p{P}\\p{S}]+")

    suspend fun tryRecoverChapterAndSwitchSource(
        book: Book,
        failedChapter: BookChapter,
        failedSource: BookSource,
        isQualityFailure: Boolean,
    ): String? {
        if (book.isLocal || book.name.isBlank()) return null

        return switchMutex.withLock {
            // Another prefetch coroutine may have already switched this book to a healthy source.
            val currentBook = ReadBook.book
            val currentSource = ReadBook.bookSource
            if (currentBook != null &&
                currentSource != null &&
                currentBook.isSameNameAuthor(book) &&
                currentSource.bookSourceUrl != failedSource.bookSourceUrl
            ) {
                val switchedChapters = appDb.bookChapterDao.getChapterList(currentBook.bookUrl)
                val matched = findMatchingChapter(switchedChapters, failedChapter)
                if (matched != null) {
                    val fetched = runCatching {
                        WebBook.getContentAwait(currentSource, currentBook, matched, needSave = true)
                    }.getOrNull()
                    if (!fetched.isNullOrBlank()) {
                        return@withLock fetched
                    }
                }
            }

            val failKey = "${book.bookUrl}::${failedSource.bookSourceUrl}"
            val failCount = (chapterFailCounts[failKey] ?: 0) + 1
            chapterFailCounts[failKey] = failCount

            val nowMs = System.currentTimeMillis()
            val healthMap = SourceHealthManager.loadHealthMap()
            val parts = appDb.bookSourceDao.allEnabledPart.associateBy { it.bookSourceUrl }

            var candidates = runCatching {
                appDb.searchBookDao.changeSourceSearch(book.name, book.author, "", "")
            }.getOrDefault(emptyList())
                .filter { candidate ->
                    candidate.origin != failedSource.bookSourceUrl &&
                        !SourceHealthManager.isCircuitOpen(healthMap[candidate.origin], nowMs)
                }

            if (candidates.size < 2) {
                val extraCandidates = searchTopHealthySources(book, failedSource.bookSourceUrl, parts, healthMap, nowMs)
                val existingOrigins = candidates.map { it.origin }.toHashSet()
                candidates = candidates + extraCandidates.filter { it.origin !in existingOrigins }
            }

            val ranked = candidates
                .distinctBy { it.origin }
                .sortedByDescending { candidate ->
                    val part = parts[candidate.origin]
                    SourceHealthManager.computeEffectiveScore(
                        staticWeight = part?.weight ?: 0,
                        customOrder = part?.customOrder ?: candidate.originOrder,
                        health = healthMap[candidate.origin],
                        nowMs = nowMs,
                    )
                }
                .take(3)

            for (candidate in ranked) {
                val candidateSource = appDb.bookSourceDao.getBookSource(candidate.origin) ?: continue
                val recovered = runCatching {
                    var targetBook = candidate.toBook()
                    if (targetBook.tocUrl.isBlank()) {
                        targetBook = WebBook.getBookInfoAwait(candidateSource, targetBook)
                    }
                    val toc = WebBook.getChapterListAwait(candidateSource, targetBook).getOrThrow()
                    val matchedChapter = findMatchingChapter(toc, failedChapter) ?: return@runCatching null
                    val content = WebBook.getContentAwait(
                        bookSource = candidateSource,
                        book = targetBook,
                        bookChapter = matchedChapter,
                        nextChapterUrl = toc.getOrNull(matchedChapter.index + 1)?.url,
                        needSave = false,
                    )
                    Triple(targetBook, toc, matchedChapter to content)
                }.getOrNull() ?: continue

                val (targetBook, toc, chapterAndContent) = recovered
                val (matchedChapter, content) = chapterAndContent
                if (content.isBlank()) continue

                val shouldSwitchBook = isQualityFailure || failCount >= 2
                if (shouldSwitchBook) {
                    val coercedIndex = if (toc.isNotEmpty()) {
                        book.durChapterIndex.coerceIn(0, toc.lastIndex)
                    } else {
                        0
                    }
                    val updatedBook = targetBook.copy().apply {
                        origin = candidateSource.bookSourceUrl
                        originName = candidateSource.bookSourceName
                        durChapterIndex = coercedIndex
                        durChapterTitle = toc.getOrNull(coercedIndex)?.title ?: book.durChapterTitle
                        durChapterPos = if (coercedIndex == book.durChapterIndex) book.durChapterPos else 0
                        order = book.order
                        group = book.group
                        type = book.type
                        canUpdate = book.canUpdate
                        totalChapterNum = toc.size
                        latestChapterTitle = toc.lastOrNull()?.title ?: latestChapterTitle
                    }
                    appDb.bookDao.replace(book, updatedBook)
                    BookHelp.updateCacheFolder(book, updatedBook)
                    appDb.bookChapterDao.delByBook(updatedBook.bookUrl)
                    appDb.bookChapterDao.insert(*toc.toTypedArray())
                    BookHelp.saveContent(candidateSource, updatedBook, matchedChapter, content)
                    chapterFailCounts.remove(failKey)

                    if (ReadBook.book?.isSameNameAuthor(updatedBook) == true) {
                        ReadBook.bookSource = candidateSource
                        ReadBook.onChapterListUpdated(updatedBook, loadContent = true)
                    }
                    appCtx.toastOnUi("当前源正文受限，已自动切换至「${candidateSource.bookSourceName}」")
                }
                return@withLock content
            }
            null
        }
    }

    private suspend fun searchTopHealthySources(
        book: Book,
        excludedOrigin: String,
        parts: Map<String, io.legado.app.data.entities.BookSourcePart>,
        healthMap: Map<String, io.legado.app.data.entities.SourceHealth>,
        nowMs: Long,
    ) = coroutineScope {
        val topParts = parts.values
            .filter { part ->
                part.bookSourceUrl != excludedOrigin &&
                    !part.hasLoginUrl &&
                    !SourceHealthManager.isCircuitOpen(healthMap[part.bookSourceUrl], nowMs)
            }
            .sortedByDescending { part ->
                SourceHealthManager.computeEffectiveScore(
                    staticWeight = part.weight,
                    customOrder = part.customOrder,
                    health = healthMap[part.bookSourceUrl],
                    nowMs = nowMs,
                )
            }
            .take(8)

        val semaphore = Semaphore(4)
        val cleanName = InkSearchQueryHelper.normalizeQuery(book.name)
        topParts.map { part ->
            async {
                semaphore.withPermit {
                    val src = part.getBookSource() ?: return@withPermit emptyList()
                    val found = withTimeoutOrNull(8_000L) {
                        runCatching { WebBook.searchBookAwait(src, cleanName, 1) }.getOrNull()
                    }.orEmpty()
                    val matched = found.filter {
                        InkSearchQueryHelper.isSameBookTitle(it.name, book.name) &&
                            InkSearchQueryHelper.isSameAuthor(it.author, book.author)
                    }
                    if (matched.isNotEmpty()) {
                        matched.forEach { it.releaseHtmlData() }
                        runCatching { appDb.searchBookDao.insert(*matched.toTypedArray()) }
                    }
                    matched
                }
            }
        }.awaitAll().flatten()
    }

    internal fun findMatchingChapter(
        chapters: List<BookChapter>,
        target: BookChapter,
    ): BookChapter? {
        if (chapters.isEmpty()) return null
        val normTarget = normalizeChapterTitle(target.title)
        if (normTarget.isNotEmpty()) {
            chapters.firstOrNull { normalizeChapterTitle(it.title) == normTarget }?.let { return it }
            chapters.firstOrNull {
                val candidate = normalizeChapterTitle(it.title)
                candidate.isNotEmpty() && (candidate.contains(normTarget) || normTarget.contains(candidate))
            }?.let { return it }
        }
        return chapters.getOrNull(target.index)
    }

    private fun normalizeChapterTitle(title: String): String {
        return titleNormalizeRegex.replace(title, "").lowercase()
    }
}
