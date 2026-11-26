package io.legado.app.inkpaper.source

import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SourceHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthManagerTest {

    @Test
    fun `healthy source outranks broken or circuit-open source`() {
        val now = 1_000_000L
        val healthy = SourceHealth(
            sourceUrl = "https://good.example.com",
            contentOkCount = 10,
            avgContentLatencyMs = 400L,
        )
        val circuitBroken = SourceHealth(
            sourceUrl = "https://bad.example.com",
            contentFailCount = 3,
            consecutiveFailCount = 2,
            adErrorCount = 2,
            circuitOpenUntil = now + SourceHealthManager.SHORT_CIRCUIT_MS,
        )

        val goodScore = SourceHealthManager.computeEffectiveScore(staticWeight = 20, customOrder = 5, health = healthy, nowMs = now)
        val badScore = SourceHealthManager.computeEffectiveScore(staticWeight = 100, customOrder = 0, health = circuitBroken, nowMs = now)

        assertTrue(goodScore > 0.0)
        assertTrue(badScore < -5000.0)
        assertTrue(goodScore > badScore)
    }

    @Test
    fun `consecutive failures open 6h then 7d circuit and success resets it`() {
        val t0 = 10_000_000L
        val url = "https://test.example.com"

        val fail1 = SourceHealthManager.applyFailure(null, url, ContentIssueType.TRUNCATED_PREVIEW, "试读", t0)
        assertEquals(1, fail1.consecutiveFailCount)
        assertEquals(0L, fail1.circuitOpenUntil)

        val fail2 = SourceHealthManager.applyFailure(fail1, url, ContentIssueType.TRUNCATED_PREVIEW, "试读", t0 + 1000L)
        assertEquals(2, fail2.consecutiveFailCount)
        assertEquals(t0 + 1000L + SourceHealthManager.SHORT_CIRCUIT_MS, fail2.circuitOpenUntil)

        var cur = fail2
        cur = SourceHealthManager.applyFailure(cur, url, ContentIssueType.AD_OR_BLOCK_PAGE, "VIP", t0 + 2000L)
        cur = SourceHealthManager.applyFailure(cur, url, ContentIssueType.AD_OR_BLOCK_PAGE, "VIP", t0 + 3000L)
        cur = SourceHealthManager.applyFailure(cur, url, ContentIssueType.AD_OR_BLOCK_PAGE, "VIP", t0 + 4000L)
        assertEquals(5, cur.consecutiveFailCount)
        assertEquals(t0 + 4000L + SourceHealthManager.LONG_CIRCUIT_MS, cur.circuitOpenUntil)
        assertTrue(SourceHealthManager.isCircuitOpen(cur, t0 + 10_000L))

        val recovered = SourceHealthManager.applySuccess(cur, url, latencyMs = 350L, nowMs = t0 + 20_000L)
        assertEquals(0, recovered.consecutiveFailCount)
        assertEquals(0L, recovered.circuitOpenUntil)
        assertFalse(SourceHealthManager.isCircuitOpen(recovered, t0 + 20_000L))
    }

    @Test
    fun `mergeSearchCandidate promotes higher-score source and inherits cover and origins`() {
        val fastHalfDead = SearchBook(
            bookUrl = "https://halfdead.com/book/1",
            origin = "https://halfdead.com",
            originName = "半残快源",
            name = "诡秘之主",
            author = "爱潜水的乌贼",
            coverUrl = "https://halfdead.com/cover.jpg",
            intro = "官方高清简介",
        )
        val slowerFullSource = SearchBook(
            bookUrl = "https://full.com/book/99",
            origin = "https://full.com",
            originName = "全本健康源",
            name = "诡秘之主",
            author = "爱潜水的乌贼",
            coverUrl = "",
            intro = "",
        )

        val scores = mapOf(
            "https://halfdead.com" to -10_000.0,
            "https://full.com" to 150.0,
        )
        val merged = SourceHealthManager.mergeSearchCandidate(fastHalfDead, slowerFullSource) { scores[it] ?: 0.0 }

        assertEquals("https://full.com", merged.origin)
        assertEquals("https://full.com/book/99", merged.bookUrl)
        assertEquals("https://halfdead.com/cover.jpg", merged.coverUrl)
        assertEquals("官方高清简介", merged.intro)
        assertTrue(merged.origins.contains("https://halfdead.com"))
    }
}
