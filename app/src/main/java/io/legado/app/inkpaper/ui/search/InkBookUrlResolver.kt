package io.legado.app.inkpaper.ui.search

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject

sealed interface InkBookUrlResolveResult {
    data class Success(val book: Book) : InkBookUrlResolveResult
    data class Failure(val message: String, val needEnableSource: Boolean = false) : InkBookUrlResolveResult
}

object InkBookUrlResolver {

    private val bareNumericIdRegex = Regex("^\\d{10,20}$")

    suspend fun resolve(rawInput: String): InkBookUrlResolveResult {
        val trimmed = rawInput.trim()
        val extractedUrl = InkSearchQueryHelper.extractBookUrlOrId(trimmed)
            ?: return InkBookUrlResolveResult.Failure("未识别到有效网址或书籍 ID")

        appDb.bookDao.getBook(extractedUrl)?.let { existing ->
            return InkBookUrlResolveResult.Success(existing)
        }

        val baseUrl = NetworkUtils.getBaseUrl(extractedUrl)
            ?: return InkBookUrlResolveResult.Failure("书籍地址格式不正确")

        val resolved = resolveBookFromSources(extractedUrl, baseUrl)
        if (resolved != null) {
            appDb.searchBookDao.insert(resolved.toSearchBook())
            return InkBookUrlResolveResult.Success(resolved)
        }

        if (bareNumericIdRegex.matches(trimmed)) {
            val idMatched = resolveByNumericId(trimmed)
            if (idMatched != null) {
                appDb.searchBookDao.insert(idMatched.toSearchBook())
                return InkBookUrlResolveResult.Success(idMatched)
            }
        }

        return InkBookUrlResolveResult.Failure(
            message = "未找到匹配的已启用书源，请先在书源管理中启用或导入对应站点书源",
            needEnableSource = true,
        )
    }

    private suspend fun resolveByNumericId(bookId: String): Book? {
        for (part in appDb.bookSourceDao.hasBookUrlPattern) {
            val source = part.getBookSource() ?: continue
            if (!source.enabled) continue
            val pattern = source.bookUrlPattern ?: continue
            val base = source.bookSourceUrl.substringBefore("#").trimEnd('/')
            val candidates = listOf(
                "$base/api/book/info?bookId=$bookId",
                "$base/book/$bookId/",
                "$base/page/$bookId",
            )
            val regex = runCatching { pattern.toRegex() }.getOrNull() ?: continue
            for (candidateUrl in candidates) {
                if (candidateUrl.matches(regex)) {
                    fetchBookInfo(candidateUrl, source)?.let { return it }
                }
            }
        }
        return null
    }

    private suspend fun resolveBookFromSources(bookUrl: String, baseUrl: String): Book? {
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(bookUrl)
        if (urlMatcher.find()) {
            val origin = GSON.fromJsonObject<AnalyzeUrl.UrlOption>(
                bookUrl.substring(urlMatcher.end()),
            ).getOrNull()?.getOrigin()
            if (origin != null) {
                appDb.bookSourceDao.getBookSource(origin)?.let { source ->
                    fetchBookInfo(bookUrl, source)?.let { return it }
                }
            }
        }

        appDb.bookSourceDao.getBookSourceAddBook(baseUrl)?.let { source ->
            fetchBookInfo(bookUrl, source)?.let { return it }
        }

        for (part in appDb.bookSourceDao.hasBookUrlPattern) {
            val source = part.getBookSource() ?: continue
            val pattern = source.bookUrlPattern ?: continue
            val matched = runCatching { bookUrl.matches(pattern.toRegex()) }.getOrDefault(false)
            if (matched) {
                fetchBookInfo(bookUrl, source)?.let { return it }
            }
        }
        return null
    }

    private suspend fun fetchBookInfo(bookUrl: String, source: BookSource): Book? {
        return runCatching {
            val book = Book(
                bookUrl = bookUrl,
                origin = source.bookSourceUrl,
                originName = source.bookSourceName,
            )
            WebBook.getBookInfoAwait(source, book)
        }.getOrNull()
    }
}
