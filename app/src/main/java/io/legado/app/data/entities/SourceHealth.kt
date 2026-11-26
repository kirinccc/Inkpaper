package io.legado.app.data.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Runtime health metrics for a book source, used for dynamic search ranking,
 * source switching priority, and circuit-breaker cooldowns.
 */
@Keep
@Entity(
    tableName = "source_health",
    foreignKeys = [
        ForeignKey(
            entity = BookSource::class,
            parentColumns = ["bookSourceUrl"],
            childColumns = ["sourceUrl"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SourceHealth(
    @PrimaryKey val sourceUrl: String,
    val contentOkCount: Int = 0,
    val contentFailCount: Int = 0,
    val consecutiveFailCount: Int = 0,
    val truncatedCount: Int = 0,
    val adErrorCount: Int = 0,
    val garbledCount: Int = 0,
    val avgContentLatencyMs: Long = 0L,
    val circuitOpenUntil: Long = 0L,
    val lastFailReason: String = "",
    val updatedAt: Long = 0L,
)
