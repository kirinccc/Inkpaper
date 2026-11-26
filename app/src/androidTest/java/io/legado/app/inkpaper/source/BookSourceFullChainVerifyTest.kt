package io.legado.app.inkpaper.source

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.entities.BookSource
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.selectDeepCheckSourceChapters
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Track-B full-chain verification runner using the real Legado WebBook engine
 * (Search -> BookInfo -> TOC -> Opening + Deep/VIP Content + ContentQualityValidator).
 *
 * Pass instrumentation arguments when running in CI:
 *   -e sourceJsonPath /sdcard/sources.json -e outputJsonPath /sdcard/verified_sources.json
 */
@RunWith(AndroidJUnit4::class)
class BookSourceFullChainVerifyTest {

    enum class SourceTier {
        TIER_A,
        TIER_B,
        HALF_DEAD,
        DEAD,
    }

    private val benchmarkKeywords = listOf("宿命之环", "斗破苍穹", "十日终焉")

    @Test
    fun verifyBookSourcesFullChain() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val sourceJsonPath = args.getString("sourceJsonPath").orEmpty()
        Assume.assumeTrue("Skipping network full-chain verify without -e sourceJsonPath", sourceJsonPath.isNotBlank())

        val inputFile = File(sourceJsonPath)
        Assume.assumeTrue("sourceJsonPath does not exist: $sourceJsonPath", inputFile.isFile)
        val sources = GSON.fromJsonArray<BookSource>(inputFile.readText()).getOrThrow()

        val verified = mutableListOf<BookSource>()
        for (source in sources) {
            val tier = evaluateSource(source)
            if (tier == SourceTier.TIER_A || tier == SourceTier.TIER_B) {
                verified.add(
                    source.copy(
                        enabled = true,
                        weight = if (tier == SourceTier.TIER_A) 100 else 30,
                        customOrder = verified.size,
                    ),
                )
            }
        }

        val outputJsonPath = args.getString("outputJsonPath").orEmpty()
        if (outputJsonPath.isNotBlank()) {
            val outFile = File(outputJsonPath)
            outFile.parentFile?.mkdirs()
            outFile.writeText(GSON.toJson(verified))
        }
    }

    private suspend fun evaluateSource(source: BookSource): SourceTier {
        var passedSamples = 0
        var sawHalfDead = false
        for (keyword in benchmarkKeywords) {
            val outcome = runCatching {
                withTimeout(25_000L) {
                    val searchResults = WebBook.searchBookAwait(source, keyword, 1)
                    val first = searchResults.firstOrNull() ?: return@withTimeout SourceTier.DEAD
                    var book = first.toBook()
                    if (book.tocUrl.isBlank()) {
                        book = WebBook.getBookInfoAwait(source, book)
                    }
                    val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
                    val selections = selectDeepCheckSourceChapters(toc, "目录为空")
                    for (sel in selections) {
                        val content = WebBook.getContentAwait(
                            bookSource = source,
                            book = book,
                            bookChapter = sel.chapter,
                            nextChapterUrl = sel.nextChapterUrl,
                            needSave = false,
                        )
                        val quality = ContentQualityValidator.validate(content, sel.chapter.title)
                        if (!quality.isHealthy) {
                            return@withTimeout SourceTier.HALF_DEAD
                        }
                    }
                    SourceTier.TIER_A
                }
            }.getOrElse {
                if (it.message?.contains("正文质量") == true) SourceTier.HALF_DEAD else SourceTier.DEAD
            }

            when (outcome) {
                SourceTier.TIER_A -> passedSamples++
                SourceTier.HALF_DEAD -> sawHalfDead = true
                SourceTier.TIER_B, SourceTier.DEAD -> Unit
            }
        }
        return when {
            passedSamples >= 2 -> SourceTier.TIER_A
            passedSamples == 1 && !sawHalfDead -> SourceTier.TIER_B
            sawHalfDead -> SourceTier.HALF_DEAD
            else -> SourceTier.DEAD
        }
    }
}
