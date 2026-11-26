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
            "https://reading.snssdk.com/reading/bookapi/detail/v/?book_id=$id",
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
}
