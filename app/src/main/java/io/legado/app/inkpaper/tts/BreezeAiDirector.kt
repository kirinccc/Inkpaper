package io.legado.app.inkpaper.tts

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

data class QuoteAttribution(
    val q: Int,
    val speaker: String,
    val gender: String = "U",
    val age: String = "adult",
    val persona: String = "",
    val voice_id: String = "",
    val emotion: String = "",
    val vocal: String = "",
)

class BreezeAiDirector(
    private val scriptsDirProvider: () -> File,
) {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun dir(): File {
        val folder = scriptsDirProvider()
        if (!folder.isDirectory) folder.mkdirs()
        return folder
    }

    private fun scriptCacheFile(
        bookUrl: String,
        chapterUrl: String,
        paragraphs: List<String>,
        model: String,
    ): File {
        val contentDigest = MessageDigest.getInstance("SHA-256")
            .digest(paragraphs.joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val keyRaw = "$bookUrl|$chapterUrl|$contentDigest|$model|v2"
        val key = MessageDigest.getInstance("SHA-256")
            .digest(keyRaw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(dir(), "script_${key}.json")
    }

    fun loadCachedAttributions(
        bookUrl: String,
        chapterUrl: String,
        paragraphs: List<String>,
        model: String = BreezeTtsConfig.llmModel,
    ): Map<Int, QuoteAttribution>? {
        val file = scriptCacheFile(bookUrl, chapterUrl, paragraphs, model)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            val type = object : TypeToken<List<QuoteAttribution>>() {}.type
            val list = GSON.fromJson<List<QuoteAttribution>>(file.readText(Charsets.UTF_8), type).orEmpty()
            list.associateBy { it.q }
        }.getOrNull()
    }

    private fun saveCachedAttributions(
        bookUrl: String,
        chapterUrl: String,
        paragraphs: List<String>,
        model: String,
        attributions: Map<Int, QuoteAttribution>,
    ) {
        runCatching {
            val file = scriptCacheFile(bookUrl, chapterUrl, paragraphs, model)
            file.writeText(GSON.toJson(attributions.values.sortedBy { it.q }), Charsets.UTF_8)
        }
    }

    suspend fun analyzeChapter(
        bookUrl: String,
        chapterUrl: String,
        paragraphs: List<String>,
        baseChapterSegments: List<List<DramaSpeechSegment>>,
        onBatchReady: (Map<Int, QuoteAttribution>) -> Unit,
    ): Map<Int, QuoteAttribution> = withContext(Dispatchers.IO) {
        val model = BreezeTtsConfig.llmModel
        loadCachedAttributions(bookUrl, chapterUrl, paragraphs, model)?.let { cached ->
            onBatchReady(cached)
            return@withContext cached
        }

        val quoteParagraphIndices = baseChapterSegments.indices.filter { idx ->
            baseChapterSegments[idx].any { it.isDialogue && it.quoteGlobalIndex >= 0 }
        }
        if (quoteParagraphIndices.isEmpty()) return@withContext emptyMap()

        val allAttributions = LinkedHashMap<Int, QuoteAttribution>()
        val batches = buildQuoteBatches(paragraphs, baseChapterSegments, quoteParagraphIndices)

        for (batch in batches) {
            currentCoroutineContext().ensureActive()
            val knownNames = BreezeCastStore.instance.getBookCast(bookUrl)
                .take(24)
                .map { "${it.name}(${it.gender}/${it.ageGroup})" }
            val result = runCatching {
                callLlmForBatch(batch.promptText, knownNames)
            }.getOrNull().orEmpty()

            if (result.isNotEmpty()) {
                val hints = result.mapNotNull { attr ->
                    val clean = BreezeCastStore.sanitizeCharacterName(attr.speaker) ?: return@mapNotNull null
                    AiCharacterHint(
                        name = clean,
                        gender = attr.gender,
                        ageGroup = attr.age,
                        persona = attr.persona,
                        preferredVoiceId = attr.voice_id,
                    )
                }.distinctBy { it.name }
                if (hints.isNotEmpty()) {
                    BreezeCastStore.instance.upsertAiCharacters(
                        bookUrl = bookUrl,
                        hints = hints,
                        narratorId = BreezeTtsConfig.narratorVoiceId,
                        defaultMaleId = BreezeTtsConfig.maleVoiceId,
                        defaultFemaleId = BreezeTtsConfig.femaleVoiceId,
                        defaultElderId = BreezeTtsConfig.elderVoiceId,
                    )
                }
                result.forEach { allAttributions[it.q] = it }
                onBatchReady(allAttributions.toMap())
            }
        }

        if (allAttributions.isNotEmpty()) {
            saveCachedAttributions(bookUrl, chapterUrl, paragraphs, model, allAttributions)
        }
        allAttributions
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!BreezeTtsConfig.hasLlmConfig()) {
                throw IllegalStateException("请先填写大模型 Base URL、API Key 和模型名称")
            }
            val url = "${BreezeTtsConfig.llmBaseUrl.trimEnd('/')}/chat/completions"
            val body = JsonObject().apply {
                addProperty("model", BreezeTtsConfig.llmModel)
                addProperty("temperature", 0.0)
                addProperty("max_tokens", 20)
                add(
                    "messages",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("role", "user")
                                addProperty("content", "请只回复：OK")
                            },
                        )
                    },
                )
            }.toString()
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer ${BreezeTtsConfig.llmApiKey}")
                .header("Content-Type", "application/json")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("连接失败 (${resp.code}): ${text.take(120)}")
                }
                "连接成功 (${BreezeTtsConfig.llmModel})"
            }
        }
    }

    private data class QuoteBatch(val promptText: String)

    private fun buildQuoteBatches(
        paragraphs: List<String>,
        baseSegments: List<List<DramaSpeechSegment>>,
        quoteParagraphIndices: List<Int>,
    ): List<QuoteBatch> {
        val batches = ArrayList<QuoteBatch>()
        val sb = StringBuilder()
        var quotesInBatch = 0
        var lastAppendedParagraph = -1

        for (pIdx in quoteParagraphIndices) {
            val prevIdx = (pIdx - 1).coerceAtLeast(0)
            if (prevIdx > lastAppendedParagraph && prevIdx < pIdx) {
                sb.append(paragraphs[prevIdx]).append('\n')
                lastAppendedParagraph = prevIdx
            }
            val line = renderTaggedParagraph(baseSegments[pIdx])
            val count = baseSegments[pIdx].count { it.isDialogue && it.quoteGlobalIndex >= 0 }
            if (quotesInBatch > 0 && (quotesInBatch + count > 24 || sb.length + line.length > 2200)) {
                batches.add(QuoteBatch(sb.toString()))
                sb.clear()
                quotesInBatch = 0
            }
            sb.append(line).append('\n')
            lastAppendedParagraph = pIdx
            quotesInBatch += count
        }
        if (sb.isNotEmpty()) {
            batches.add(QuoteBatch(sb.toString()))
        }
        return batches
    }

    private fun renderTaggedParagraph(segments: List<DramaSpeechSegment>): String {
        val seenQuotes = HashSet<Int>()
        return buildString {
            for (seg in segments) {
                if (seg.isDialogue && seg.quoteGlobalIndex >= 0) {
                    if (seenQuotes.add(seg.quoteGlobalIndex)) {
                        append("[Q${seg.quoteGlobalIndex}]“").append(seg.text).append("”")
                    }
                } else {
                    append(seg.text)
                }
            }
        }
    }

    private fun callLlmForBatch(
        taggedText: String,
        knownCharacters: List<String>,
    ): List<QuoteAttribution> {
        val roster = BreezeCastStore.instance.buildCastingRosterForPrompt(BreezeTtsConfig.narratorVoiceId)
        val systemPrompt = buildString {
            append("你是有声书剧本导演与选角总监。请为文本中带编号的对白 [Q编号] 判断：speaker(角色名)、gender(严格 M/F/U)、age(youth/adult/elder)、persona(6字内人设声线，如“清冷御姐”“沉稳青年男”)、voice_id(从候选音色库中严格按性别选最贴合人设的voice_id，严禁男选女或女选男)、emotion(10字内语气指令)、vocal(仅限 \"\" / \"轻笑\" / \"叹气\")。\n")
            if (knownCharacters.isNotEmpty()) {
                append("本书已知角色（优先复用原名）：").append(knownCharacters.joinToString("、")).append("\n")
            }
            if (roster.isNotBlank()) {
                append("候选音色库：\n").append(roster).append("\n")
            }
            append("严格输出 JSON 数组，不要输出任何额外解释：\n")
            append("[{\"q\":0,\"speaker\":\"林凡\",\"gender\":\"M\",\"age\":\"youth\",\"persona\":\"沉稳青年男\",\"voice_id\":\"voc_xxx\",\"emotion\":\"冷淡试探\",\"vocal\":\"轻笑\"}]")
        }

        val payload = JsonObject().apply {
            addProperty("model", BreezeTtsConfig.llmModel)
            addProperty("temperature", 0.1)
            add(
                "messages",
                JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", systemPrompt)
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", taggedText)
                    })
                },
            )
        }.toString()

        val url = "${BreezeTtsConfig.llmBaseUrl.trimEnd('/')}/chat/completions"
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${BreezeTtsConfig.llmApiKey}")
            .header("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) return emptyList()
            val root = JsonParser.parseString(raw).asJsonObject
            val content = root.getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString.orEmpty()
            return parseAttributionsJson(content)
        }
    }

    companion object {
        val instance: BreezeAiDirector by lazy {
            BreezeAiDirector { File(appCtx.filesDir, "breeze_cast/scripts") }
        }

        internal fun parseAttributionsJson(rawContent: String): List<QuoteAttribution> {
            val cleaned = rawContent.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val startIdx = cleaned.indexOf('[')
            val endIdx = cleaned.lastIndexOf(']')
            if (startIdx < 0 || endIdx <= startIdx) return emptyList()
            val jsonSlice = cleaned.substring(startIdx, endIdx + 1)
            return runCatching {
                val type = object : TypeToken<List<QuoteAttribution>>() {}.type
                GSON.fromJson<List<QuoteAttribution>>(jsonSlice, type).orEmpty()
            }.getOrDefault(emptyList())
        }

        fun applyAttributions(
            baseChapterSegments: List<List<DramaSpeechSegment>>,
            attributions: Map<Int, QuoteAttribution>,
        ): List<List<DramaSpeechSegment>> {
            if (attributions.isEmpty()) return baseChapterSegments
            return baseChapterSegments.map { paragraphSegments ->
                paragraphSegments.map { seg ->
                    val attr = if (seg.isDialogue && seg.quoteGlobalIndex >= 0) {
                        attributions[seg.quoteGlobalIndex]
                    } else null
                    if (attr == null) seg
                    else {
                        val speakerName = attr.speaker.trim().ifBlank { seg.speaker }
                        val gender = attr.gender.uppercase().takeIf { it in setOf("M", "F", "U") } ?: seg.genderHint
                        val age = attr.age.lowercase().takeIf { it in setOf("youth", "adult", "elder") } ?: seg.ageHint
                        val emotion = attr.emotion.trim().ifBlank { seg.emotionInstruction }
                        val vocalTag = when (attr.vocal.trim()) {
                            "轻笑", "笑" -> "[轻笑]"
                            "叹气", "叹息" -> "[叹气]"
                            else -> ""
                        }
                        val nextText = if (vocalTag.isNotEmpty() && !seg.text.contains(vocalTag)) {
                            "$vocalTag ${seg.text}"
                        } else {
                            seg.text
                        }
                        seg.copy(
                            text = nextText,
                            speaker = speakerName,
                            genderHint = gender,
                            ageHint = age,
                            emotionInstruction = emotion,
                        )
                    }
                }
            }
        }
    }
}
