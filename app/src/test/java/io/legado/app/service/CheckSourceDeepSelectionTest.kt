package io.legado.app.service

import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckSourceDeepSelectionTest {

    @Test
    fun `short toc selects only opening chapter`() {
        val chapters = (0 until 10).map { idx ->
            BookChapter(title = "第${idx + 1}章", url = "/c/$idx", index = idx)
        }
        val selections = selectDeepCheckSourceChapters(chapters, "目录为空")

        assertEquals(1, selections.size)
        assertEquals("/c/0", selections[0].chapter.url)
    }

    @Test
    fun `long toc without vip flags selects opening and 70 percent deep chapter`() {
        val chapters = (0 until 200).map { idx ->
            BookChapter(title = "第${idx + 1}章", url = "/c/$idx", index = idx)
        }
        val selections = selectDeepCheckSourceChapters(chapters, "目录为空")

        assertEquals(2, selections.size)
        assertEquals("/c/0", selections[0].chapter.url)
        assertEquals("/c/140", selections[1].chapter.url)
    }

    @Test
    fun `long toc with vip flags prefers first vip chapter after opening`() {
        val chapters = (0 until 100).map { idx ->
            BookChapter(
                title = "第${idx + 1}章",
                url = "/c/$idx",
                index = idx,
                isVip = idx >= 35,
            )
        }
        val selections = selectDeepCheckSourceChapters(chapters, "目录为空")

        assertEquals(2, selections.size)
        assertEquals("/c/0", selections[0].chapter.url)
        assertEquals("/c/35", selections[1].chapter.url)
    }
}
