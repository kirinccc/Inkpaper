package io.legado.app.inkpaper.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentQualityValidatorTest {

    @Test
    fun `healthy full chapter passes validation`() {
        val body = "第一章 风起青萍之末\n" +
            "夜风拂过青石板路，远处的灯火在雨幕中显得有些朦胧。" +
            "少年站在屋檐下，轻轻呼出一口白气，目光穿过重重雨帘，望向长街尽头那座高耸的钟楼。".repeat(12)

        val result = ContentQualityValidator.validate(body, chapterTitle = "第一章 风起青萍之末")

        assertTrue(result.isHealthy)
        assertEquals(ContentIssueType.OK, result.issue)
        assertTrue(result.effectiveCharCount >= 400)
    }

    @Test
    fun `short author leave notice passes via whitelist`() {
        val body = "今天家里临时有事，外出处理一下，明天恢复两更，感谢各位书友理解。"
        val result = ContentQualityValidator.validate(body, chapterTitle = "请假一天")

        assertTrue(result.isHealthy)
        assertEquals(ContentIssueType.OK, result.issue)
    }

    @Test
    fun `very short stub chapter is flagged as truncated preview`() {
        val body = "这是只有三十几个字的试读内容。"
        val result = ContentQualityValidator.validate(body, chapterTitle = "第一百二十章 决战")

        assertFalse(result.isHealthy)
        assertEquals(ContentIssueType.TRUNCATED_PREVIEW, result.issue)
    }

    @Test
    fun `medium preview ending with ellipsis and read-more prompt is flagged as truncated`() {
        val body = ("清晨的阳光透过窗棂洒在桌案上，少年缓缓睁开双眼，感受到体内澎湃的力量正在流转。" +
            "他深吸一口气，推开房门走了出去，院子里早已站满了前来拜访的同门师兄弟。").repeat(3) +
            "未完待续，请点击阅读全文..."
        val result = ContentQualityValidator.validate(body, chapterTitle = "第二百章 突破")

        assertFalse(result.isHealthy)
        assertEquals(ContentIssueType.TRUNCATED_PREVIEW, result.issue)
    }

    @Test
    fun `vip paywall and cloudflare block pages are rejected`() {
        val vipNotice = "本章为VIP章节，请下载官方App客户端后继续阅读完整内容。"
        val vipResult = ContentQualityValidator.validate(vipNotice, chapterTitle = "第201章")
        assertFalse(vipResult.isHealthy)
        assertEquals(ContentIssueType.AD_OR_BLOCK_PAGE, vipResult.issue)

        val cfBlock = "Just a moment... Attention Required! | Cloudflare"
        val cfResult = ContentQualityValidator.validate(cfBlock, chapterTitle = "第202章")
        assertFalse(cfResult.isHealthy)
        assertEquals(ContentIssueType.AD_OR_BLOCK_PAGE, cfResult.issue)
    }

    @Test
    fun `undeciphered PUA custom font characters above 1 percent trigger garbled content`() {
        val normalPart = "他缓缓抬起头，看着眼前的景象，心中涌起一股难以言喻的震撼。".repeat(8)
        val puaPart = "\uE3E8\uE3E9\uE3EA\uE3EB\uE3EC\uE3ED\uE3EE"
        val result = ContentQualityValidator.validate(normalPart + puaPart, chapterTitle = "第88章")

        assertFalse(result.isHealthy)
        assertEquals(ContentIssueType.GARBLED_CONTENT, result.issue)
        assertEquals(7, result.puaCharCount)
    }
}
