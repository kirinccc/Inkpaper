package io.legado.app.service

import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.TocEmptyException
import kotlin.math.min

internal data class CheckSourceChapterSelection(
    val chapter: BookChapter,
    val nextChapterUrl: String,
)

internal fun selectCheckSourceChapter(
    chapters: List<BookChapter>,
    emptyMessage: String,
): CheckSourceChapterSelection {
    val readableChapters = chapters.asSequence()
        .filterNot { it.isVolume && it.url.startsWith(it.title) }
        .take(2)
        .toList()
    val chapter = readableChapters.firstOrNull()
        ?: throw TocEmptyException(emptyMessage)
    return CheckSourceChapterSelection(
        chapter = chapter,
        nextChapterUrl = readableChapters.getOrNull(1)?.url ?: chapter.url,
    )
}

/**
 * Selects both the opening chapter and a deep/VIP chapter (when available)
 * so check-source verification catches sources whose free chapters work
 * while deep VIP chapters are broken or truncated.
 */
internal fun selectDeepCheckSourceChapters(
    chapters: List<BookChapter>,
    emptyMessage: String,
): List<CheckSourceChapterSelection> {
    val readable = chapters.filterNot { it.isVolume && it.url.startsWith(it.title) }
    if (readable.isEmpty()) {
        throw TocEmptyException(emptyMessage)
    }
    val selections = mutableListOf<CheckSourceChapterSelection>()
    val first = readable[0]
    selections.add(
        CheckSourceChapterSelection(
            chapter = first,
            nextChapterUrl = readable.getOrNull(1)?.url ?: first.url,
        ),
    )
    if (readable.size >= 25) {
        val deepIdx = readable.indexOfFirstWithMinIndex(10) { it.isVip }
            ?: min((readable.size * 0.7).toInt(), min(readable.lastIndex, 200))
        if (deepIdx in 1..readable.lastIndex) {
            val deep = readable[deepIdx]
            selections.add(
                CheckSourceChapterSelection(
                    chapter = deep,
                    nextChapterUrl = readable.getOrNull(deepIdx + 1)?.url ?: deep.url,
                ),
            )
        }
    }
    return selections
}

private inline fun <T> List<T>.indexOfFirstWithMinIndex(
    minIndex: Int,
    predicate: (T) -> Boolean,
): Int? {
    for (i in minIndex until size) {
        if (predicate(this[i])) return i
    }
    return null
}
