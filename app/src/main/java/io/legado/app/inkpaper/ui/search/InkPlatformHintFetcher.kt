package io.legado.app.inkpaper.ui.search

import com.google.gson.JsonParser
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class InkPlatformBookHint(
    val bookId: String,
    val title: String,
    val author: String,
    val category: String,
    val score: String,
    val abstractText: String,
    val coverUrl: String,
    val webUrl: String,
)

object InkPlatformHintFetcher {

    private val htmlTagRegex = Regex("</?em>", RegexOption.IGNORE_CASE)

    suspend fun fetchFanqieHints(rawQuery: String, limit: Int = 3): List<InkPlatformBookHint> {
        val clean = InkSearchQueryHelper.normalizeQuery(rawQuery)
        if (clean.isEmpty()) return emptyList()

        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        val reqUrl =
            "https://novel.snssdk.com/api/novel/channel/homepage/search/search/v1/?device_platform=android&parent_enterfrom=novel_channel_search.tab.&offset=0&aid=1967&q=$encoded"

        return withTimeoutOrNull(5_000L) {
            runCatching {
                val body = okHttpClient.newCallResponseBody {
                    url(reqUrl)
                }.decompressed().text("utf-8")
                parseFanqieHintsJson(body, limit)
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    fun parseFanqieHintsJson(json: String, limit: Int = 3): List<InkPlatformBookHint> {
        if (json.isBlank() || limit <= 0) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val retData = root.getAsJsonObject("data")?.getAsJsonArray("ret_data") ?: return emptyList()
            val results = mutableListOf<InkPlatformBookHint>()
            for (el in retData) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val bookId = obj.get("book_id")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                val rawTitle = (obj.get("title") ?: obj.get("book_name"))
                    ?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val title = rawTitle.replace(htmlTagRegex, "").trim()
                if (bookId.isEmpty() || title.isEmpty()) continue

                val author = obj.get("author")?.takeIf { !it.isJsonNull }?.asString
                    ?.replace(htmlTagRegex, "")?.trim().orEmpty()
                val category = obj.get("category")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                val score = obj.get("score")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                val abstractText = obj.get("abstract")?.takeIf { !it.isJsonNull }?.asString
                    ?.replace(htmlTagRegex, "")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val coverUrl = obj.get("thumb_url")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()

                results.add(
                    InkPlatformBookHint(
                        bookId = bookId,
                        title = title,
                        author = author,
                        category = category,
                        score = score,
                        abstractText = abstractText,
                        coverUrl = coverUrl,
                        webUrl = "https://fanqienovel.com/page/$bookId",
                    ),
                )
                if (results.size >= limit) break
            }
            results
        }.getOrDefault(emptyList())
    }
}
