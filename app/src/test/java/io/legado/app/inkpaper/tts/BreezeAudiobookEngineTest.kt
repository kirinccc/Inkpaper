package io.legado.app.inkpaper.tts

import io.legado.app.model.resolveReadAloudEngineName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class BreezeAudiobookEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `script parser splits mixed narration and dialogue with exact offsets and emotion tags`() {
        val raw = "林凡眼神微冷，嗤笑一声：“就凭你们几个，也想拦我？”黑衣人瞳孔微缩。"
        val segments = BreezeScriptParser.parseParagraph(
            paragraphIndex = 0,
            rawText = raw,
            mode = BreezeDramaMode.LOCAL_MULTI_VOICE,
            stylePreset = BreezeStylePreset.DEFAULT,
            knownCharacters = listOf("林凡"),
        )

        assertEquals(3, segments.size)
        assertFalse(segments[0].isDialogue)
        assertEquals("旁白", segments[0].speaker)
        assertEquals("林凡眼神微冷，嗤笑一声：", segments[0].text)
        assertEquals("林凡眼神微冷，嗤笑一声：", raw.substring(segments[0].charOffsetInParagraph, segments[0].charOffsetInParagraph + segments[0].charLength))

        assertTrue(segments[1].isDialogue)
        assertEquals("林凡", segments[1].speaker)
        assertTrue(segments[1].text.startsWith("[轻笑] "))
        assertTrue(segments[1].text.contains("就凭你们几个，也想拦我？"))
        assertEquals("“就凭你们几个，也想拦我？”", raw.substring(segments[1].charOffsetInParagraph, segments[1].charOffsetInParagraph + segments[1].charLength))
        assertTrue(segments[1].emotionInstruction.isNotBlank())

        assertFalse(segments[2].isDialogue)
        assertEquals("黑衣人瞳孔微缩。", segments[2].text)
    }

    @Test
    fun `script parser infers female and elder traits across multiple quotes`() {
        val raw = "苏清雪轻轻叹了口气：“算了。”老者抚须道：“善。”"
        val segments = BreezeScriptParser.parseParagraph(
            paragraphIndex = 0,
            rawText = raw,
            mode = BreezeDramaMode.LOCAL_MULTI_VOICE,
            stylePreset = BreezeStylePreset.DEFAULT,
        )

        val dialogues = segments.filter { it.isDialogue }
        assertEquals(2, dialogues.size)
        assertEquals("苏清雪", dialogues[0].speaker)
        assertTrue(dialogues[0].text.startsWith("[叹气] "))
        assertEquals("善。", dialogues[1].text)
        assertEquals("elder", dialogues[1].ageHint)
    }

    @Test
    fun `script parser splits oversized paragraph under 720 chars without breaking surrogate pairs`() {
        val raw = "甲".repeat(718) + "😀" + "这是后面的长句内容。".repeat(100)
        val segments = BreezeScriptParser.parseParagraph(
            paragraphIndex = 0,
            rawText = raw,
            mode = BreezeDramaMode.SINGLE_NARRATOR,
            stylePreset = BreezeStylePreset.SUSPENSE,
        )

        assertTrue(segments.size >= 2)
        assertTrue(segments.all { it.text.length <= BreezeScriptParser.MAX_BREEZE_TEXT_CHARS })
        assertFalse(segments.any { Character.isHighSurrogate(it.text.last()) || Character.isLowSurrogate(it.text.first()) })
        assertEquals(raw, segments.joinToString("") { it.text })
    }

    @Test
    fun `cast store filters pronouns and persists consistent character voices across reloads`() {
        val dir = tempFolder.newFolder("cast")
        val catalog = listOf(
            BreezeVoiceProfile("v_narrator", "沉稳旁白", "audiobook narrator", gender = "M", archetype = BreezeVoiceArchetype.NARRATOR),
            BreezeVoiceProfile("v_male1", "清朗青年", "young male", gender = "M", ageGroup = "youth", archetype = BreezeVoiceArchetype.YOUTH_MALE),
            BreezeVoiceProfile("v_female1", "清冷女声", "female", gender = "F", archetype = BreezeVoiceArchetype.MATURE_FEMALE),
        )
        val store1 = BreezeCastStore { dir }
        store1.saveVoiceCatalog(catalog)

        assertNull(BreezeCastStore.sanitizeCharacterName("他"))
        assertNull(BreezeCastStore.sanitizeCharacterName("众人"))
        assertNull(BreezeCastStore.sanitizeCharacterName("冷笑道"))
        assertNull(BreezeCastStore.sanitizeCharacterName("用古怪的口音"))
        assertEquals("林凡", BreezeCastStore.sanitizeCharacterName("林凡："))

        val segMale = DramaSpeechSegment(0, 0, 0, 4, "你好", true, "林凡", genderHint = "M")
        val segFemale = DramaSpeechSegment(1, 1, 0, 4, "且慢", true, "苏清雪", genderHint = "F")
        val rMale = store1.resolveVoiceForSegment("book://1", segMale, "v_narrator", "v_male1", "v_female1", "v_male1")
        val rFemale = store1.resolveVoiceForSegment("book://1", segFemale, "v_narrator", "v_male1", "v_female1", "v_male1")

        assertEquals("v_male1", rMale.voiceId)
        assertEquals("v_female1", rFemale.voiceId)
        assertNotEquals(rMale.voiceId, rFemale.voiceId)

        // Reload in a fresh store instance -> must return identical voice IDs
        val store2 = BreezeCastStore { dir }
        val reloadedMale = store2.resolveVoiceForSegment("book://1", segMale, "v_narrator", "v_male1", "v_female1", "v_male1")
        assertEquals("v_male1", reloadedMale.voiceId)

        // Alias merge
        val segAlias = DramaSpeechSegment(2, 2, 0, 4, "走吧", true, "林师兄", genderHint = "M")
        store2.resolveVoiceForSegment("book://1", segAlias, "v_narrator", "v_male1", "v_female1", "v_male1")
        assertTrue(store2.mergeAlias("book://1", "林凡", "林师兄"))
        assertEquals(2, store2.getBookCast("book://1").size)
    }

    @Test
    fun `ai director applies quote attributions cleanly and api client builds valid payloads`() {
        val raw = "“你是谁？”他冷冷问道。“我叫苏清雪。”"
        val base = BreezeScriptParser.parseChapter(
            paragraphs = listOf(raw),
            mode = BreezeDramaMode.AI_DIRECTOR,
            stylePreset = BreezeStylePreset.DEFAULT,
        )
        val parsedJson = BreezeAiDirector.parseAttributionsJson(
            """```json
            [{"q":0,"speaker":"林凡","gender":"M","age":"youth","emotion":"警惕冷淡","vocal":""},
             {"q":1,"speaker":"苏清雪","gender":"F","age":"youth","emotion":"平静从容","vocal":"轻笑"}]
            ```""",
        ).associateBy { it.q }

        val enriched = BreezeAiDirector.applyAttributions(base, parsedJson).first()
        val dialogues = enriched.filter { it.isDialogue }
        assertEquals(2, dialogues.size)
        assertEquals("林凡", dialogues[0].speaker)
        assertEquals("警惕冷淡", dialogues[0].emotionInstruction)
        assertEquals("苏清雪", dialogues[1].speaker)
        assertTrue(dialogues[1].text.startsWith("[轻笑] "))

        val reqJson = BreezeApiClient.buildSpeechRequestJson("测试文本", "breeze-tts-2", "语气沉稳")
        assertTrue(reqJson.contains("\"speed\":1.0"))
        assertTrue(reqJson.contains("\"instructions\":\"语气沉稳\""))
        assertFalse(reqJson.contains("\"instruction\":"))

        val voices = BreezeApiClient.parseVoicesJson(
            """{"voices":[
                {"voice_id":"voc_1","name":"云溪","description":"温柔少女"},
                {"voice_id":"voc_2","name":"说书人","description":"沉稳旁白"},
                {"voice_id":"voc_jp","name":"和人","description":"やや低めで厚みのある男性の声"},
                {"voice_id":"voc_fr","name":"专业翻译官（法语）","description":"French voice"},
                {"voice_id":"voc_me","name":"大肥鱼3","voice_type":"personal","description":"温柔女声"}
            ]}""",
        )
        assertEquals(5, voices.size)
        assertEquals("F", voices[0].gender)
        assertEquals(BreezeVoiceArchetype.NARRATOR, voices[1].archetype)
        val filtered = voices.filter { BreezeApiClient.isChineseOrPersonalVoice(it) }
        assertEquals(listOf("voc_1", "voc_2", "voc_me"), filtered.map { it.voiceId })

        assertEquals("F", BreezeApiClient.classifyVoice("蜀地暖声", "川间暖谈是一款使用四川口音普通话的青年女声").first)
        assertEquals("M", BreezeApiClient.classifyVoice("新手老爸", "中音区轻快，透着一种笨拙而真诚的温柔").first)
        val elderPair = BreezeApiClient.classifyVoice("弈林老叟", "音色苍老且富有生活气息，智慧长者")
        assertEquals("M", elderPair.first)
        assertEquals("elder", elderPair.second)
    }

    @Test
    fun `cast store enforces strict gender guard and validates ai preferredVoiceId`() {
        val dir = tempFolder.newFolder("cast_strict")
        val catalog = listOf(
            BreezeVoiceProfile("v_nar", "深思先生", "成年男声", gender = "M", archetype = BreezeVoiceArchetype.NARRATOR),
            BreezeVoiceProfile("v_u_female", "蜀地暖声", "四川口音普通话", gender = "U"),
            BreezeVoiceProfile("v_f1", "香农小姐", "清冷疏离的普通话年轻女声", gender = "F", archetype = BreezeVoiceArchetype.MATURE_FEMALE),
            BreezeVoiceProfile("v_m1", "严谨", "稳重扎实的男声", gender = "M", archetype = BreezeVoiceArchetype.MATURE_MALE),
        )
        val store = BreezeCastStore { dir }
        store.saveVoiceCatalog(catalog)

        // Male character must never receive U or F voice when an M voice exists
        val maleSeg = DramaSpeechSegment(0, 0, 0, 4, "你好", true, "张少岚", genderHint = "M")
        val resolvedMale = store.resolveVoiceForSegment("book://strict", maleSeg, "v_nar", "v_m1", "v_f1", "v_m1")
        assertEquals("v_m1", resolvedMale.voiceId)

        // AI hint with gender-mismatched preferredVoiceId must be rejected in favor of matching gender
        store.upsertAiCharacters(
            bookUrl = "book://strict",
            hints = listOf(
                AiCharacterHint("贺令仪", gender = "F", persona = "清冷御姐", preferredVoiceId = "v_m1"),
            ),
            narratorId = "v_nar",
            defaultMaleId = "v_m1",
            defaultFemaleId = "v_f1",
            defaultElderId = "v_m1",
        )
        val cast = store.getBookCast("book://strict")
        assertEquals("v_f1", cast.first { it.name == "贺令仪" }.voiceId)
    }

    @Test
    fun `audio cache writes atomically and trims oldest files to quota and engine name resolves`() {
        val cacheDir = tempFolder.newFolder("audio_cache")
        val cache = BreezeAudioCache { cacheDir }
        val key1 = cache.cacheKey("breeze-tts-2", "voc_1", "", "第一句")
        val key2 = cache.cacheKey("breeze-tts-2", "voc_1", "", "第二句")

        val f1 = cache.writeStream(key1, ByteArrayInputStream(ByteArray(120) { 1 })) {}
        f1.setLastModified(1000L)
        val f2 = cache.writeStream(key2, ByteArrayInputStream(ByteArray(120) { 2 })) {}
        f2.setLastModified(2000L)

        assertEquals(2, cache.stats().first)
        cache.trimToMaxBytes(150L)
        assertEquals(1, cache.stats().first)
        assertNull(cache.getReusableFile(key1))
        assertTrue(cache.getReusableFile(key2) != null)

        assertEquals(
            BreezeTtsConfig.ENGINE_DISPLAY_NAME,
            resolveReadAloudEngineName(BreezeTtsConfig.ENGINE_ID, "系统默认") { null },
        )
    }
}
