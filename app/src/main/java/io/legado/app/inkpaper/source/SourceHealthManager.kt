package io.legado.app.inkpaper.source

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SourceHealth

/**
 * Manages runtime source health scoring, circuit-breaker cooldowns,
 * and health-aware SearchBook merging.
 */
object SourceHealthManager {

    const val SHORT_CIRCUIT_MS: Long = 6L * 3600_000L
    const val LONG_CIRCUIT_MS: Long = 7L * 24L * 3600_000L

    /**
     * Computes effective ranking score combining static weight, custom order,
     * Bayesian-smoothed content success rate, quality penalties, and latency.
     * Sources currently in circuit-breaker cooldown are pushed to the bottom.
     */
    fun computeEffectiveScore(
        staticWeight: Int,
        customOrder: Int,
        health: SourceHealth?,
        nowMs: Long = System.currentTimeMillis(),
    ): Double {
        if (health != null && nowMs < health.circuitOpenUntil) {
            return -10_000.0 - customOrder.coerceIn(-500, 5000) * 0.01
        }
        val base = staticWeight.coerceIn(-100, 500).toDouble() +
            (100 - customOrder.coerceIn(0, 100)) * 0.5
        val ok = health?.contentOkCount ?: 0
        val fail = health?.contentFailCount ?: 0
        val healthRate = (ok + 2.0) / (ok + fail + 3.0)
        val bonus = ok.coerceAtMost(30) * 3.0
        val penalty = (health?.truncatedCount ?: 0) * 25.0 +
            (health?.adErrorCount ?: 0) * 30.0 +
            (health?.garbledCount ?: 0) * 25.0 +
            (health?.consecutiveFailCount ?: 0) * 20.0
        val avgLatency = health?.avgContentLatencyMs ?: 0L
        val latencyPenalty = if (avgLatency > 0L) {
            (avgLatency.coerceAtMost(10_000L) / 1000.0) * 2.0
        } else {
            0.0
        }
        return (base + 100.0) * healthRate + bonus - penalty - latencyPenalty
    }

    fun isCircuitOpen(health: SourceHealth?, nowMs: Long = System.currentTimeMillis()): Boolean {
        return health != null && nowMs < health.circuitOpenUntil
    }

    fun statusBadge(health: SourceHealth?, nowMs: Long = System.currentTimeMillis()): String? {
        if (health == null) return null
        if (nowMs < health.circuitOpenUntil) return "熔断冷却中"
        if (health.consecutiveFailCount > 0 && health.lastFailReason.isNotBlank()) {
            return "近期异常: ${health.lastFailReason}"
        }
        if (health.contentOkCount >= 3 && health.consecutiveFailCount == 0) {
            return "正文稳定"
        }
        return null
    }

    /**
     * Merges an incoming SearchBook into an existing same-book candidate,
     * promoting the higher-scoring source as the primary SearchBook while
     * preserving origins and filling missing cover/intro metadata.
     */
    fun mergeSearchCandidate(
        existing: SearchBook,
        incoming: SearchBook,
        scoreOf: (String) -> Double,
    ): SearchBook {
        val existingScore = scoreOf(existing.origin)
        val incomingScore = scoreOf(incoming.origin)
        return if (incomingScore > existingScore) {
            incoming.addOrigin(existing.origin)
            existing.origins.forEach { originUrl ->
                incoming.addOrigin(originUrl)
            }
            if (incoming.coverUrl.isNullOrBlank() && !existing.coverUrl.isNullOrBlank()) {
                incoming.coverUrl = existing.coverUrl
            }
            if (incoming.intro.isNullOrBlank() && !existing.intro.isNullOrBlank()) {
                incoming.intro = existing.intro
            }
            if (incoming.latestChapterTitle.isNullOrBlank() && !existing.latestChapterTitle.isNullOrBlank()) {
                incoming.latestChapterTitle = existing.latestChapterTitle
            }
            incoming
        } else {
            existing.addOrigin(incoming.origin)
            if (existing.coverUrl.isNullOrBlank() && !incoming.coverUrl.isNullOrBlank()) {
                existing.coverUrl = incoming.coverUrl
            }
            if (existing.intro.isNullOrBlank() && !incoming.intro.isNullOrBlank()) {
                existing.intro = incoming.intro
            }
            if (existing.latestChapterTitle.isNullOrBlank() && !incoming.latestChapterTitle.isNullOrBlank()) {
                existing.latestChapterTitle = incoming.latestChapterTitle
            }
            existing
        }
    }

    /**
     * Computes updated SourceHealth after a successful content fetch.
     */
    fun applySuccess(
        existing: SourceHealth?,
        sourceUrl: String,
        latencyMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): SourceHealth {
        val prev = existing ?: SourceHealth(sourceUrl = sourceUrl)
        val boundedLatency = latencyMs.coerceIn(1L, 60_000L)
        val newLatency = if (prev.avgContentLatencyMs <= 0L) {
            boundedLatency
        } else {
            ((prev.avgContentLatencyMs * 7L) + (boundedLatency * 3L)) / 10L
        }
        return prev.copy(
            contentOkCount = prev.contentOkCount + 1,
            consecutiveFailCount = 0,
            avgContentLatencyMs = newLatency,
            circuitOpenUntil = 0L,
            updatedAt = nowMs,
        )
    }

    /**
     * Computes updated SourceHealth after a content quality/fetch failure
     * and opens short (6h) or long (7d) circuit breaker when thresholds are reached.
     */
    fun applyFailure(
        existing: SourceHealth?,
        sourceUrl: String,
        issue: ContentIssueType,
        reason: String,
        nowMs: Long = System.currentTimeMillis(),
    ): SourceHealth {
        val prev = existing ?: SourceHealth(sourceUrl = sourceUrl)
        val nextConsecutive = prev.consecutiveFailCount + 1
        val nextCircuitUntil = when {
            nextConsecutive >= 5 -> nowMs + LONG_CIRCUIT_MS
            nextConsecutive >= 2 -> maxOf(prev.circuitOpenUntil, nowMs + SHORT_CIRCUIT_MS)
            else -> prev.circuitOpenUntil
        }
        return prev.copy(
            contentFailCount = prev.contentFailCount + 1,
            consecutiveFailCount = nextConsecutive,
            truncatedCount = prev.truncatedCount + if (issue == ContentIssueType.TRUNCATED_PREVIEW) 1 else 0,
            adErrorCount = prev.adErrorCount + if (issue == ContentIssueType.AD_OR_BLOCK_PAGE) 1 else 0,
            garbledCount = prev.garbledCount + if (issue == ContentIssueType.GARBLED_CONTENT) 1 else 0,
            circuitOpenUntil = nextCircuitUntil,
            lastFailReason = reason.take(64),
            updatedAt = nowMs,
        )
    }

    fun loadScoreSnapshot(sourceParts: List<BookSourcePart>): Map<String, Double> {
        val nowMs = System.currentTimeMillis()
        val healthMap = runCatching {
            appDb.sourceHealthDao.getAll().associateBy { it.sourceUrl }
        }.getOrDefault(emptyMap())
        return sourceParts.associate { part ->
            part.bookSourceUrl to computeEffectiveScore(
                staticWeight = part.weight,
                customOrder = part.customOrder,
                health = healthMap[part.bookSourceUrl],
                nowMs = nowMs,
            )
        }
    }

    fun loadHealthMap(): Map<String, SourceHealth> {
        return runCatching {
            appDb.sourceHealthDao.getAll().associateBy { it.sourceUrl }
        }.getOrDefault(emptyMap())
    }

    fun recordContentSuccess(sourceUrl: String, latencyMs: Long) {
        if (sourceUrl.isBlank()) return
        runCatching {
            val prev = appDb.sourceHealthDao.get(sourceUrl)
            appDb.sourceHealthDao.upsert(applySuccess(prev, sourceUrl, latencyMs))
        }
    }

    fun recordContentFailure(sourceUrl: String, issue: ContentIssueType, reason: String) {
        if (sourceUrl.isBlank()) return
        runCatching {
            val prev = appDb.sourceHealthDao.get(sourceUrl)
            appDb.sourceHealthDao.upsert(applyFailure(prev, sourceUrl, issue, reason))
        }
    }
}
