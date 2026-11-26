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

    private const val FANQIE_MAIN_SOURCE_URL = "https://fanqienovel.com"

    suspend fun resolve(rawInput: String): InkBookUrlResolveResult {
        val extractedUrl = InkSearchQueryHelper.extractBookUrlOrId(rawInput)
            ?: return InkBookUrlResolveResult.Failure("未识别到有效网址或 19 位书籍 ID")

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

        if (InkSearchQueryHelper.isFanqieUrlOrId(rawInput)) {
            val fanqieSource = appDb.bookSourceDao.getBookSource(FANQIE_MAIN_SOURCE_URL)
            if (fanqieSource != null && !fanqieSource.enabled) {
                return InkBookUrlResolveResult.Failure(
                    message = "检测到番茄链接/ID，但「番茄小说」书源尚未启用。请先在书源管理中启用并登录该书源。",
                    needEnableSource = true,
                )
            } else if (fanqieSource != null && fanqieSource.enabled) {
                val retryFanqie = fetchBookInfo(extractedUrl, fanqieSource)
                if (retryFanqie != null) {
                    appDb.searchBookDao.insert(retryFanqie.toSearchBook())
                    return InkBookUrlResolveResult.Success(retryFanqie)
                }
                return InkBookUrlResolveResult.Failure(
                    message = "番茄书源解析失败，请检查网络或尝试在书源管理中登录番茄账号",
                    needEnableSource = true,
                )
            }
        }

        return InkBookUrlResolveResult.Failure(
            message = "未找到匹配的已启用书源，请先在书源管理中启用或导入对应站点书源",
            needEnableSource = true,
        )
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
