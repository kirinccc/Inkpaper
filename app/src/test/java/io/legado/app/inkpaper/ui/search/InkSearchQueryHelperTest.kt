package io.legado.app.inkpaper.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkSearchQueryHelperTest {

    @Test
    fun normalizeQuery_stripsBracketsAndCollapsesWhitespace() {
        assertEquals("我的末世修罗场果然有问题", InkSearchQueryHelper.normalizeQuery("  《我的末世修罗场果然有问题》 "))
        assertEquals("末世 避难所", InkSearchQueryHelper.normalizeQuery("「末世」\u3000【避难所】"))
    }

    @Test
    fun generateFallbackHints_handlesPrefixesSuffixesAndDelimiters() {
        val fanqieHints = InkSearchQueryHelper.generateFallbackHints("我的末世修罗场果然有问题")
        assertTrue(fanqieHints.contains("末世修罗场"))
        assertFalse(fanqieHints.contains("我的末世修罗场果然有问题"))

        val colonHints = InkSearchQueryHelper.generateFallbackHints("末世：避难所太挤，女神请自重")
        assertTrue(colonHints.contains("末世"))
        assertTrue(colonHints.contains("避难所太挤"))
        assertTrue(colonHints.contains("女神请自重"))

        assertTrue(InkSearchQueryHelper.generateFallbackHints("我").isEmpty())
    }

    @Test
    fun extractBookUrlOrId_recognizesFanqieIdShareTextAndRejectsPlainTitles() {
        val id = "7285555567827880975"
        assertEquals(
            "https://fanqienovel.com/api/book/info?bookId=$id",
            InkSearchQueryHelper.extractBookUrlOrId(id),
        )
        assertTrue(InkSearchQueryHelper.isFanqieUrlOrId(id))

        val shareText = "我在番茄小说看《xxx》https://fanqienovel.com/page/$id，快来看！"
        assertEquals(
            "https://fanqienovel.com/page/$id",
            InkSearchQueryHelper.extractBookUrlOrId(shareText),
        )
        assertTrue(InkSearchQueryHelper.isFanqieUrlOrId(shareText))

        assertNull(InkSearchQueryHelper.extractBookUrlOrId("我的末世修罗场果然有问题"))
        assertFalse(InkSearchQueryHelper.isFanqieUrlOrId("我的末世修罗场果然有问题"))
    }

    @Test
    fun isSameBookTitle_and_isSameAuthor_handlePunctuationAndContainment() {
        assertTrue(InkSearchQueryHelper.isSameBookTitle("《末世：避难所太挤》", "末世 避难所太挤"))
        assertFalse(InkSearchQueryHelper.isSameBookTitle("我的末世修罗场果然有问题", "末世：避难所太挤"))

        assertTrue(InkSearchQueryHelper.isSameAuthor("弊不可避", "弊不可避"))
        assertTrue(InkSearchQueryHelper.isSameAuthor("弊不可避 著", "弊不可避"))
        assertTrue(InkSearchQueryHelper.isSameAuthor("", "弊不可避"))
        assertFalse(InkSearchQueryHelper.isSameAuthor("天蚕土豆", "弊不可避"))
    }

    @Test
    fun isStrongSearchMatch_filtersNoisySingleCharMatches() {
        assertFalse(
            InkSearchQueryHelper.isStrongSearchMatch(
                "我身上有一个不可战胜的夏天",
                "(法)加缪",
                "我的末世修罗场果然有问题",
            ),
        )
        assertTrue(
            InkSearchQueryHelper.isStrongSearchMatch(
                "我的末世修罗场果然有问题",
                "弊不可避",
                "我的末世修罗场果然有问题",
            ),
        )
        assertTrue(
            InkSearchQueryHelper.isStrongSearchMatch(
                "末世：避难所太挤，女神请自重",
                "弊不可避",
                "弊不可避",
            ),
        )
    }

    @Test
    fun parseFanqieHintsJson_extractsBookInfoAndStripsEmTags() {
        val sample = """
            {
              "code": 0,
              "data": {
                "ret_data": [
                  {
                    "book_id": "7592266630982290494",
                    "title": "<em>末世</em>：避难所太挤，女神请自重",
                    "author": "<em>弊不可避</em>",
                    "category": "科幻末世",
                    "score": "8.5",
                    "abstract": "全球冰封，万物死寂。",
                    "thumb_url": "https://example.com/cover.jpg"
                  }
                ]
              }
            }
        """.trimIndent()
        val parsed = InkPlatformHintFetcher.parseFanqieHintsJson(sample, limit = 3)
        assertEquals(1, parsed.size)
        val first = parsed.first()
        assertEquals("7592266630982290494", first.bookId)
        assertEquals("末世：避难所太挤，女神请自重", first.title)
        assertEquals("弊不可避", first.author)
        assertEquals("https://fanqienovel.com/page/7592266630982290494", first.webUrl)
    }
}
