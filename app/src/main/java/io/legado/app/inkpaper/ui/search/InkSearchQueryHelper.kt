package io.legado.app.inkpaper.ui.search

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object InkSearchQueryHelper {

    private val bracketRegex = Regex("[《》「」『』【】]")
    private val whitespaceRegex = Regex("\\s+")
    private val splitSegmentRegex = Regex("[：:，,、—\\-｜|/·\\s]+")
    private val prefixRegex = Regex("^(我的|这个|重生之|穿越之|我在|开局|全球|全员|系统[：:]|末世[：:]|末日[：:])")
    private val suffixRegex = Regex("(果然有问题|怎么了|有点不对劲|不对劲|全集|小说|txt|TXT)$")
    private val fanqieIdRegex = Regex("^\\d{19}$")
    private val urlExtractRegex = Regex("https?://[^\\s\"'<>\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]+")
    private val trailingPunctuationRegex = Regex("[.,!?;:)\\]\"']+$")
    private val fanqieHostRegex = Regex("https?://[^/]*(fqnovel|snssdk|fanqienovel|changdunovel)\\.com(/|$)", RegexOption.IGNORE_CASE)
    private val titlePunctuationRegex = Regex("[：:，,、—\\-·\\s]+")

    /**
     * Normalizes a user search query by removing wrapping book brackets and collapsing whitespace.
     */
    fun normalizeQuery(raw: String): String {
        return raw
            .replace('\u3000', ' ')
            .replace(bracketRegex, "")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    /**
     * Generates shorter/rewritten fallback queries when the primary search yields few or no results.
     */
    fun generateFallbackHints(rawQuery: String): List<String> {
        val clean = normalizeQuery(rawQuery)
        if (clean.length < 2) return emptyList()

        val hints = LinkedHashSet<String>()
        fun addHint(candidate: String) {
            val trimmed = candidate.trim()
            if (trimmed.length >= 2 && trimmed != clean && hints.size < 5) {
                hints.add(trimmed)
            }
        }

        // 1. Split by punctuation/separators (e.g. "末世：避难所太挤，女神请自重")
        val segments = clean.split(splitSegmentRegex).filter { it.length in 2..12 }
        for (seg in segments) {
            addHint(seg)
        }

        // 2. Strip common web-novel prefixes & suffixes (e.g. "我的末世修罗场果然有问题" -> "末世修罗场")
        val noPrefix = prefixRegex.replace(clean, "")
        val noSuffix = suffixRegex.replace(clean, "")
        val strippedBoth = suffixRegex.replace(noPrefix, "")
        addHint(strippedBoth)
        addHint(noPrefix)
        addHint(noSuffix)

        // 3. Fallback substring slices for longer titles
        if (clean.length >= 6 && hints.size < 2) {
            addHint(clean.substring(0, 4))
            val start = (clean.length - 4) / 2
            addHint(clean.substring(start, start + 4))
        }

        return hints.take(5)
    }

    /**
     * Extracts a book URL or converts a bare 19-digit Fanqie book_id into a detail URL.
     */
    fun extractBookUrlOrId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        if (fanqieIdRegex.matches(trimmed)) {
            return "https://reading.snssdk.com/reading/bookapi/detail/v/?book_id=$trimmed"
        }
        val match = urlExtractRegex.find(trimmed) ?: return null
        return match.value.replace(trailingPunctuationRegex, "").takeIf { it.isNotBlank() }
    }

    /**
     * Returns true if the input is a 19-digit Fanqie ID or points to a Fanqie-family domain.
     */
    fun isFanqieUrlOrId(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (fanqieIdRegex.matches(trimmed)) return true
        val url = extractBookUrlOrId(trimmed) ?: return false
        return fanqieHostRegex.containsMatchIn(url)
    }

    /**
     * Builds an external web search URL for discovering author names or former book titles.
     */
    fun buildWebSearchUrl(query: String): String {
        val clean = normalizeQuery(query)
        val encoded = URLEncoder.encode("$clean 小说 作者 曾用名", StandardCharsets.UTF_8.name())
        return "https://cn.bing.com/search?q=$encoded"
    }

    /**
     * Compares two book titles ignoring book brackets, punctuation, and whitespace.
     */
    fun isSameBookTitle(a: String, b: String): Boolean {
        val normA = normalizeQuery(a).replace(titlePunctuationRegex, "")
        val normB = normalizeQuery(b).replace(titlePunctuationRegex, "")
        return normA.isNotEmpty() && normA.equals(normB, ignoreCase = true)
    }

    /**
     * Compares two author names tolerating whitespace and partial containment.
     */
    fun isSameAuthor(a: String, b: String): Boolean {
        val normA = a.trim().replace(whitespaceRegex, "")
        val normB = b.trim().replace(whitespaceRegex, "")
        if (normA.isEmpty() || normB.isEmpty()) return true
        return normA.equals(normB, ignoreCase = true) ||
            normA.contains(normB, ignoreCase = true) ||
            normB.contains(normA, ignoreCase = true)
    }

    /**
     * Returns true when a search result has a strong title or author match with the query,
     * filtering out noisy single-character fuzzy matches from remote sources.
     */
    fun isStrongSearchMatch(bookName: String, bookAuthor: String, rawQuery: String): Boolean {
        val cleanQuery = normalizeQuery(rawQuery)
        if (cleanQuery.isEmpty()) return false
        val normName = normalizeQuery(bookName)
        val normAuthor = bookAuthor.trim().replace(whitespaceRegex, "")
        val compactQuery = cleanQuery.replace(titlePunctuationRegex, "")
        val compactName = normName.replace(titlePunctuationRegex, "")
        if (compactName.isNotEmpty() && compactQuery.isNotEmpty() && compactName.equals(compactQuery, ignoreCase = true)) {
            return true
        }
        if (normName.contains(cleanQuery, ignoreCase = true)) return true
        if (normAuthor.isNotEmpty() && normAuthor.contains(cleanQuery, ignoreCase = true)) return true
        if (compactQuery.length >= 4 && compactName.length >= 4 && compactQuery.contains(compactName, ignoreCase = true)) {
            return true
        }
        return false
    }
}
