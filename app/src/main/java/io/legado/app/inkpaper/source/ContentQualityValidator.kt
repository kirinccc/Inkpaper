package io.legado.app.inkpaper.source

/**
 * Classification of chapter body quality check outcomes.
 */
enum class ContentIssueType {
    OK,
    EMPTY,
    TRUNCATED_PREVIEW,
    AD_OR_BLOCK_PAGE,
    GARBLED_CONTENT,
}

/**
 * Result of validating fetched chapter content before caching or rendering.
 */
data class ContentQualityResult(
    val issue: ContentIssueType,
    val effectiveCharCount: Int,
    val puaCharCount: Int,
    val reason: String = "",
) {
    val isHealthy: Boolean get() = issue == ContentIssueType.OK
}

/**
 * Pure local semantic validator for chapter text.
 * Detects empty bodies, trial/preview truncation, VIP/anti-bot block pages,
 * and undeciphered PUA custom font garbled text without making network calls.
 */
object ContentQualityValidator {

    private val scriptOrStyleRegex = Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("<[^>]+>")
    private val whitespaceRegex = Regex("\\s+")
    private val punctuationRegex = Regex("[\\p{P}\\p{S}\\s]+")

    private val shortChapterWhitelistRegex = Regex(
        "(请假|通知|感言|说明|公告|楔子|序章|序言|前言|后记|尾声|番外预告|完结|新书|致读者|封推|上架)",
    )

    private val adOrBlockPatterns = listOf(
        Regex("本章为?\\s*VIP\\s*章节", RegexOption.IGNORE_CASE),
        Regex("请(下载|前往|打开).{0,15}(App|客户端|官方).*阅读", RegexOption.IGNORE_CASE),
        Regex("(订阅本章|付费章节|购买本章|登录后继续阅读)"),
        Regex("(Just a moment\\.\\.\\.|Attention Required!\\s*\\|\\s*Cloudflare|cf-browser-verification|Access Denied)", RegexOption.IGNORE_CASE),
        Regex("(正在手打中|内容正在(转码|加载|同步)中|接口已(失效|关闭|停用)|源站维护)"),
    )

    private val truncatedEndingRegex = Regex(
        "(\\.\\.\\.|……|未完待续|阅读全文|剩余\\s*\\d+%?|本章未完|加载全文|登录后继续阅读)\\s*$",
    )

    private val sentenceEndingChars = setOf(
        '。', '！', '？', '!', '?', '…', '”', '’', '」', '』', ')', '）', '~', '～', '"', '\'', '】', '》',
    )

    fun validate(
        rawContent: String,
        chapterTitle: String = "",
        referenceAvgChars: Int = 0,
    ): ContentQualityResult {
        val plain = stripHtmlAndTitle(rawContent, chapterTitle)
        if (plain.isEmpty()) {
            return ContentQualityResult(
                issue = ContentIssueType.EMPTY,
                effectiveCharCount = 0,
                puaCharCount = 0,
                reason = "正文为空",
            )
        }

        var effectiveChars = 0
        var puaChars = 0
        var replacementChars = 0
        for (ch in plain) {
            val code = ch.code
            when {
                code in 0xE000..0xF8FF -> puaChars++
                code == 0xFFFD -> replacementChars++
                ch.isLetterOrDigit() || code in 0x4E00..0x9FFF -> effectiveChars++
            }
        }

        if (effectiveChars == 0 && puaChars == 0) {
            return ContentQualityResult(
                issue = ContentIssueType.EMPTY,
                effectiveCharCount = 0,
                puaCharCount = 0,
                reason = "正文无有效文字",
            )
        }

        // 1. Undeciphered custom font / garbled characters check
        val totalMeasured = (effectiveChars + puaChars).coerceAtLeast(1)
        if (puaChars >= 5 || puaChars.toDouble() / totalMeasured > 0.01 || replacementChars >= 8) {
            return ContentQualityResult(
                issue = ContentIssueType.GARBLED_CONTENT,
                effectiveCharCount = effectiveChars,
                puaCharCount = puaChars,
                reason = "存在未解密字体或乱码(PUA=$puaChars)",
            )
        }

        // 2. VIP paywall / anti-bot / dead API notice check (guard < 800 chars to avoid false positives in dialogue)
        if (effectiveChars < 800) {
            for (pattern in adOrBlockPatterns) {
                if (pattern.containsMatchIn(plain)) {
                    return ContentQualityResult(
                        issue = ContentIssueType.AD_OR_BLOCK_PAGE,
                        effectiveCharCount = effectiveChars,
                        puaCharCount = puaChars,
                        reason = "命中广告/VIP/拦截提示",
                    )
                }
            }
        }

        // 3. Author notice / short-chapter whitelist
        val isWhitelistedTitle = chapterTitle.isNotBlank() && shortChapterWhitelistRegex.containsMatchIn(chapterTitle)
        if (isWhitelistedTitle && effectiveChars >= 20) {
            return ContentQualityResult(
                issue = ContentIssueType.OK,
                effectiveCharCount = effectiveChars,
                puaCharCount = puaChars,
            )
        }

        // 4. Hard lower bound for truncated/stub chapters
        if (effectiveChars < 180) {
            return ContentQualityResult(
                issue = ContentIssueType.TRUNCATED_PREVIEW,
                effectiveCharCount = effectiveChars,
                puaCharCount = puaChars,
                reason = "有效正文过短(${effectiveChars}字)",
            )
        }

        // 5. Medium-length preview truncation heuristics (180..599 chars)
        if (effectiveChars < 600) {
            val lastNonSpace = plain.lastOrNull { !it.isWhitespace() }
            val hasAbruptEnding = truncatedEndingRegex.containsMatchIn(plain) ||
                (lastNonSpace != null && lastNonSpace !in sentenceEndingChars)
            val farBelowBookAvg = referenceAvgChars >= 1200 && effectiveChars < (referenceAvgChars * 0.25).toInt()
            if (hasAbruptEnding || farBelowBookAvg) {
                return ContentQualityResult(
                    issue = ContentIssueType.TRUNCATED_PREVIEW,
                    effectiveCharCount = effectiveChars,
                    puaCharCount = puaChars,
                    reason = "疑似试读截断(${effectiveChars}字)",
                )
            }
        }

        return ContentQualityResult(
            issue = ContentIssueType.OK,
            effectiveCharCount = effectiveChars,
            puaCharCount = puaChars,
        )
    }

    private fun stripHtmlAndTitle(rawContent: String, chapterTitle: String): String {
        val noScripts = scriptOrStyleRegex.replace(rawContent, " ")
        val noTags = htmlTagRegex.replace(noScripts, "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        var collapsed = whitespaceRegex.replace(noTags, " ").trim()
        val cleanTitle = whitespaceRegex.replace(chapterTitle, " ").trim()
        if (cleanTitle.isNotEmpty() && collapsed.startsWith(cleanTitle)) {
            collapsed = collapsed.removePrefix(cleanTitle).trimStart()
        } else {
            val compactTitle = punctuationRegex.replace(cleanTitle, "")
            if (compactTitle.length >= 2) {
                val firstLineEnd = collapsed.indexOf(' ').let { if (it in 1..40) it else -1 }
                if (firstLineEnd > 0) {
                    val firstToken = punctuationRegex.replace(collapsed.substring(0, firstLineEnd), "")
                    if (firstToken == compactTitle) {
                        collapsed = collapsed.substring(firstLineEnd).trimStart()
                    }
                }
            }
        }
        return collapsed
    }
}
