package io.legado.app.inkpaper.tts

data class DramaSpeechSegment(
    val paragraphIndex: Int,
    val quoteGlobalIndex: Int = -1,
    val charOffsetInParagraph: Int,
    val charLength: Int,
    val text: String,
    val isDialogue: Boolean,
    val speaker: String,
    val genderHint: String = "U",
    val ageHint: String = "adult",
    val emotionInstruction: String = "",
)

object BreezeScriptParser {
    const val MAX_BREEZE_TEXT_CHARS = 720

    private val silentRegex = Regex("""^[\s\p{C}\p{P}\p{Z}]+$""")
    private val openQuoteMap = mapOf(
        '“' to '”',
        '「' to '」',
        '『' to '』',
    )

    private val femaleCues = listOf(
        "她", "少女", "女子", "姑娘", "妇人", "美妇", "师姐", "师妹", "公主", "小姐", "娘", "妈", "姐", "女帝", "仙子", "丫鬟",
    )
    private val maleCues = listOf(
        "他", "少年", "青年", "男子", "大汉", "壮汉", "老夫", "师兄", "师弟", "公子", "少爷", "爹", "爸", "哥", "老者", "长老",
    )
    private val elderCues = listOf("老者", "老人", "老道", "老妪", "长老", "老夫", "老太", "太奶", "老爷子")

    private val speechVerbsPattern = Regex(
        """([\u4e00-\u9fa5]{1,8}?)(?:缓缓|轻轻|淡淡|冷冷|沉声|低声|大声|怒声|笑着|冷笑|嗤笑|咬牙|忍不住|突然|连忙|忙)?(?:说道|笑道|怒道|叹道|喝道|问道|答道|低声道|沉声道|冷笑道|嗤笑道|淡淡道|轻声道|朗声道|高声道|开口道|喊道|叫道|叹了口气|冷笑一声|嗤笑一声|微微一笑|叹息道|叹息|道|说|问|喊|喝)"""
    )

    private data class EmotionCue(
        val keywords: List<String>,
        val instruction: String,
        val vocalTag: String = "",
    )

    private val emotionCues = listOf(
        EmotionCue(listOf("嗤笑", "冷笑", "嘲讽", "讥笑"), "语气带着一丝冷意与嘲讽，略显不屑。", "[轻笑]"),
        EmotionCue(listOf("轻笑", "浅笑", "失笑", "微笑", "笑道"), "语气温和带笑，轻松自然。", "[轻笑]"),
        EmotionCue(listOf("叹了口气", "长叹", "轻叹", "叹息", "叹道", "幽幽一叹"), "语气低缓，略带无奈与叹息。", "[叹气]"),
        EmotionCue(listOf("怒道", "怒吼", "暴喝", "喝道", "咆哮", "咬牙"), "情绪激动愤怒，语气强硬有力。"),
        EmotionCue(listOf("低声", "轻声", "耳语", "呢喃", "喃喃"), "压低声音轻声诉说，节奏缓慢。"),
        EmotionCue(listOf("颤声", "惊恐", "骇然", "咽了口唾沫"), "声音微微发颤，带着紧张与惊慌。"),
        EmotionCue(listOf("淡淡", "平静", "面无表情"), "语调平缓克制，冷静沉稳。"),
    )

    fun parseChapter(
        paragraphs: List<String>,
        mode: BreezeDramaMode,
        stylePreset: BreezeStylePreset,
        knownCharacters: Collection<String> = emptySet(),
    ): List<List<DramaSpeechSegment>> {
        var quoteIndex = 0
        return paragraphs.mapIndexed { pIndex, raw ->
            val segments = parseParagraph(
                paragraphIndex = pIndex,
                rawText = raw,
                mode = mode,
                stylePreset = stylePreset,
                knownCharacters = knownCharacters,
                startingQuoteIndex = quoteIndex,
            )
            quoteIndex += segments.count { it.isDialogue && it.quoteGlobalIndex >= 0 }
            segments
        }
    }

    fun parseParagraph(
        paragraphIndex: Int,
        rawText: String,
        mode: BreezeDramaMode,
        stylePreset: BreezeStylePreset,
        knownCharacters: Collection<String> = emptySet(),
        startingQuoteIndex: Int = 0,
    ): List<DramaSpeechSegment> {
        if (rawText.isBlank() || silentRegex.matches(rawText)) return emptyList()

        if (mode == BreezeDramaMode.SINGLE_NARRATOR) {
            return splitOversizedSegment(
                paragraphIndex = paragraphIndex,
                quoteGlobalIndex = -1,
                charOffsetInParagraph = 0,
                rawSpan = rawText,
                isDialogue = false,
                speaker = BreezeCastStore.NARRATOR_SPEAKER,
                genderHint = "U",
                ageHint = "adult",
                emotionInstruction = stylePreset.instruction,
            )
        }

        val quoteSpans = findQuoteSpans(rawText)
        if (quoteSpans.isEmpty()) {
            return splitOversizedSegment(
                paragraphIndex = paragraphIndex,
                quoteGlobalIndex = -1,
                charOffsetInParagraph = 0,
                rawSpan = rawText,
                isDialogue = false,
                speaker = BreezeCastStore.NARRATOR_SPEAKER,
                genderHint = "U",
                ageHint = "adult",
                emotionInstruction = stylePreset.instruction,
            )
        }

        val result = ArrayList<DramaSpeechSegment>()
        var cursor = 0
        var nextQuoteIndex = startingQuoteIndex

        for (span in quoteSpans) {
            if (span.openIndex > cursor) {
                val narration = rawText.substring(cursor, span.openIndex)
                result += splitOversizedSegment(
                    paragraphIndex = paragraphIndex,
                    quoteGlobalIndex = -1,
                    charOffsetInParagraph = cursor,
                    rawSpan = narration,
                    isDialogue = false,
                    speaker = BreezeCastStore.NARRATOR_SPEAKER,
                    genderHint = "U",
                    ageHint = "adult",
                    emotionInstruction = stylePreset.instruction,
                )
            }

            val innerStart = span.openIndex + 1
            val innerEnd = span.closeIndex
            if (innerEnd > innerStart) {
                val innerText = rawText.substring(innerStart, innerEnd)
                if (innerText.isNotBlank() && !silentRegex.matches(innerText)) {
                    val beforeContext = rawText.substring(
                        maxOf(cursor, span.openIndex - 45),
                        span.openIndex,
                    )
                    val afterContext = rawText.substring(
                        (span.closeIndex + 1).coerceAtMost(rawText.length),
                        (span.closeIndex + 26).coerceAtMost(rawText.length),
                    )
                    val contextWindow = "$beforeContext $afterContext"
                    val (speaker, gender, age) = inferSpeakerAndTraits(
                        beforeContext = beforeContext,
                        afterContext = afterContext,
                        knownCharacters = knownCharacters,
                    )
                    val cue = inferEmotionCue(contextWindow)
                    val spokenText = if (cue != null && cue.vocalTag.isNotEmpty() && !innerText.contains(cue.vocalTag)) {
                        "${cue.vocalTag} $innerText"
                    } else {
                        innerText
                    }
                    val chunks = splitOversizedSegment(
                        paragraphIndex = paragraphIndex,
                        quoteGlobalIndex = nextQuoteIndex,
                        charOffsetInParagraph = span.openIndex,
                        rawSpan = spokenText,
                        isDialogue = true,
                        speaker = speaker,
                        genderHint = gender,
                        ageHint = age,
                        emotionInstruction = cue?.instruction.orEmpty(),
                        overrideCharLength = (span.closeIndex + 1 - span.openIndex),
                    )
                    if (chunks.isNotEmpty()) {
                        result += chunks
                        nextQuoteIndex++
                    }
                }
            }
            cursor = (span.closeIndex + 1).coerceAtMost(rawText.length)
        }

        if (cursor < rawText.length) {
            val tail = rawText.substring(cursor)
            result += splitOversizedSegment(
                paragraphIndex = paragraphIndex,
                quoteGlobalIndex = -1,
                charOffsetInParagraph = cursor,
                rawSpan = tail,
                isDialogue = false,
                speaker = BreezeCastStore.NARRATOR_SPEAKER,
                genderHint = "U",
                ageHint = "adult",
                emotionInstruction = stylePreset.instruction,
            )
        }

        return result
    }

    private data class QuoteSpan(val openIndex: Int, val closeIndex: Int)

    private fun findQuoteSpans(text: String): List<QuoteSpan> {
        val spans = ArrayList<QuoteSpan>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val closing = openQuoteMap[ch]
            if (closing != null) {
                val closeIdx = text.indexOf(closing, i + 1)
                if (closeIdx > i + 1) {
                    spans.add(QuoteSpan(i, closeIdx))
                    i = closeIdx + 1
                    continue
                }
            }
            i++
        }
        return spans
    }

    private fun inferSpeakerAndTraits(
        beforeContext: String,
        afterContext: String,
        knownCharacters: Collection<String>,
    ): Triple<String, String, String> {
        val searchOrder = listOf(beforeContext, afterContext)
        var detectedSpeaker = "人物对白"

        for (ctx in searchOrder) {
            if (ctx.isBlank()) continue
            val knownMatch = knownCharacters
                .filter { it.isNotBlank() && ctx.contains(it) }
                .maxByOrNull { ctx.lastIndexOf(it) }
            if (knownMatch != null) {
                detectedSpeaker = knownMatch
                break
            }
            val verbMatch = speechVerbsPattern.findAll(ctx).lastOrNull()
            val candidate = verbMatch?.groupValues?.getOrNull(1)
            val sanitized = BreezeCastStore.sanitizeCharacterName(candidate)
            if (sanitized != null) {
                detectedSpeaker = sanitized
                break
            }
        }

        val combined = "$beforeContext $afterContext"
        val age = if (elderCues.any { combined.contains(it) }) "elder" else "adult"
        val gender = when {
            femaleCues.any { combined.contains(it) } -> "F"
            maleCues.any { combined.contains(it) } -> "M"
            else -> "U"
        }
        return Triple(detectedSpeaker, gender, age)
    }

    private fun inferEmotionCue(context: String): EmotionCue? {
        return emotionCues.firstOrNull { cue ->
            cue.keywords.any { context.contains(it) }
        }
    }

    internal fun splitOversizedSegment(
        paragraphIndex: Int,
        quoteGlobalIndex: Int,
        charOffsetInParagraph: Int,
        rawSpan: String,
        isDialogue: Boolean,
        speaker: String,
        genderHint: String,
        ageHint: String,
        emotionInstruction: String,
        overrideCharLength: Int? = null,
    ): List<DramaSpeechSegment> {
        if (rawSpan.isBlank() || silentRegex.matches(rawSpan)) return emptyList()
        if (rawSpan.length <= MAX_BREEZE_TEXT_CHARS) {
            return listOf(
                DramaSpeechSegment(
                    paragraphIndex = paragraphIndex,
                    quoteGlobalIndex = quoteGlobalIndex,
                    charOffsetInParagraph = charOffsetInParagraph,
                    charLength = overrideCharLength ?: rawSpan.length,
                    text = rawSpan.trim(),
                    isDialogue = isDialogue,
                    speaker = speaker,
                    genderHint = genderHint,
                    ageHint = ageHint,
                    emotionInstruction = emotionInstruction,
                ),
            )
        }

        val chunks = ArrayList<DramaSpeechSegment>()
        var start = 0
        while (start < rawSpan.length) {
            var end = (start + MAX_BREEZE_TEXT_CHARS).coerceAtMost(rawSpan.length)
            if (end < rawSpan.length) {
                if (Character.isHighSurrogate(rawSpan[end - 1])) end--
                val sentenceBreak = (end - 1 downTo start).firstOrNull {
                    rawSpan[it] in "。！？.!?；;\n"
                }
                val commaBreak = if (sentenceBreak == null) {
                    (end - 1 downTo start).firstOrNull { rawSpan[it] in "，,、" }
                } else null
                val chosen = sentenceBreak ?: commaBreak
                if (chosen != null && chosen > start) {
                    end = chosen + 1
                }
            }
            val chunkText = rawSpan.substring(start, end).trim()
            if (chunkText.isNotEmpty() && !silentRegex.matches(chunkText)) {
                chunks.add(
                    DramaSpeechSegment(
                        paragraphIndex = paragraphIndex,
                        quoteGlobalIndex = quoteGlobalIndex,
                        charOffsetInParagraph = charOffsetInParagraph + if (overrideCharLength != null) 0 else start,
                        charLength = overrideCharLength ?: (end - start),
                        text = chunkText,
                        isDialogue = isDialogue,
                        speaker = speaker,
                        genderHint = genderHint,
                        ageHint = ageHint,
                        emotionInstruction = emotionInstruction,
                    ),
                )
            }
            start = end
        }
        return chunks
    }
}
