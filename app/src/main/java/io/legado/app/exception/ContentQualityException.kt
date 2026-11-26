package io.legado.app.exception

import io.legado.app.inkpaper.source.ContentQualityResult

/**
 * Thrown when fetched chapter content fails semantic quality validation
 * (e.g. trial preview truncation, VIP/anti-bot block page, or garbled font).
 */
class ContentQualityException(
    val qualityResult: ContentQualityResult,
    msg: String = qualityResult.reason.ifBlank { "正文质量校验未通过" },
) : NoStackTraceException(msg)
