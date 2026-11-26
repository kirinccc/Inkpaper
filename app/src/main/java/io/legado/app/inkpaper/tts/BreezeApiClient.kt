package io.legado.app.inkpaper.tts

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.SocketTimeoutException
import java.net.URLEncoder

class BreezeCreditExhaustedException(message: String) : IOException(message)
class BreezeAuthException(message: String) : IOException(message)
class BreezeApiException(val code: Int, message: String) : IOException(message)

object BreezeApiClient {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    internal fun buildSpeechRequestJson(
        text: String,
        modelId: String = BreezeTtsConfig.DEFAULT_MODEL_ID,
        instruction: String = "",
        guidanceScale: Float = 3.5f,
    ): String {
        val body = JsonObject().apply {
            addProperty("text", text)
            addProperty("model_id", modelId.ifBlank { BreezeTtsConfig.DEFAULT_MODEL_ID })
            addProperty("language_code", "zh")
            add(
                "voice_settings",
                JsonObject().apply {
                    addProperty("speed", 1.0)
                    addProperty("guidance_scale", guidanceScale)
                },
            )
            if (instruction.isNotBlank()) {
                addProperty("instructions", instruction.trim())
            }
        }
        return body.toString()
    }

    internal fun parseVoicesJson(json: String): List<BreezeVoiceProfile> {
        val root = JsonParser.parseString(json)
        val array: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                when {
                    obj.has("voices") && obj.get("voices").isJsonArray -> obj.getAsJsonArray("voices")
                    obj.has("items") && obj.get("items").isJsonArray -> obj.getAsJsonArray("items")
                    obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data")
                    else -> JsonArray()
                }
            }
            else -> JsonArray()
        }

        val profiles = ArrayList<BreezeVoiceProfile>(array.size())
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val voiceId = obj.str("voice_id").ifBlank { obj.str("id") }
            if (voiceId.isBlank()) continue
            val name = obj.str("name").ifBlank { voiceId }
            val descParts = ArrayList<String>()
            obj.str("description").takeIf { it.isNotBlank() }?.let(descParts::add)
            if (obj.has("labels") && obj.get("labels").isJsonObject) {
                obj.getAsJsonObject("labels").entrySet().forEach { (_, v) ->
                    if (v.isJsonPrimitive) descParts.add(v.asString)
                }
            }
            if (obj.has("tags") && obj.get("tags").isJsonArray) {
                obj.getAsJsonArray("tags").forEach { t ->
                    if (t.isJsonPrimitive) descParts.add(t.asString)
                }
            }
            val description = descParts.joinToString(" · ")
            val voiceType = obj.str("voice_type").ifBlank { obj.str("category").ifBlank { "default" } }
            val previewUrl = obj.str("preview_url").ifBlank { null }
            val (gender, ageGroup, archetype) = classifyVoice(name, description)
            profiles.add(
                BreezeVoiceProfile(
                    voiceId = voiceId,
                    name = name,
                    description = description,
                    voiceType = voiceType,
                    gender = gender,
                    ageGroup = ageGroup,
                    archetype = archetype,
                    previewUrl = previewUrl,
                ),
            )
        }
        return profiles
    }

    internal fun isChineseOrPersonalVoice(voice: BreezeVoiceProfile): Boolean {
        if (voice.voiceType != "default") return true
        val combined = "${voice.name} ${voice.description}"
        val hasKana = combined.any { it in '\u3040'..'\u30ff' }
        if (hasKana) return false
        if (voice.name.contains("翻译官") && !voice.name.contains("中文")) return false
        return combined.any { it in '\u4e00'..'\u9fff' }
    }

    private val dialectKeywords = listOf(
        "口音", "方言", "关中", "海派", "秦川", "京巷", "海岛", "蜀地", "中原",
        "东北", "上海", "陕西", "四川", "河南", "广东", "台湾", "北京", "吴语", "粤语", "闽南",
        "川间", "豫里",
    )

    internal fun isRegionalDialect(name: String, description: String): Boolean {
        val combined = "$name $description"
        return dialectKeywords.any { combined.contains(it) }
    }

    internal fun classifyVoice(name: String, description: String): Triple<String, String, BreezeVoiceArchetype> {
        val text = "$name $description".lowercase()
        val isFemale = listOf(
            "女声", "女性", "少女", "御姐", "小姐", "女侠", "姑娘", "妇人", "娘", "姐儿", "姐",
            "female", "woman", "girl", "lady",
        ).any { text.contains(it) }
        val isMale = listOf(
            "男声", "男性", "少年", "大叔", "先生", "老爸", "老叟", "书生", "老者", "男主",
            "male", "man", "boy", "gentleman",
        ).any { text.contains(it) }
        val isElder = listOf(
            "elder", "old", "senior", "grand", "老者", "老人", "沧桑", "长老", "老叟",
            "七十", "七十一", "七十二", "六十", "六十六", "五十八", "五十", "微哑", "稍哑",
        ).any { text.contains(it) }
        val isYouth = listOf(
            "young", "youth", "teen", "少女", "少年", "清亮", "活泼", "二十", "机灵", "迷糊", "青春",
        ).any { text.contains(it) }
        val isNarrator = listOf(
            "narrator", "story", "audiobook", "旁白", "说书", "播音", "纪录片",
            "书灯", "深思", "低中音", "沉稳", "儒雅书生",
        ).any { text.contains(it) }

        val gender = when {
            isFemale && !isMale -> "F"
            isMale && !isFemale -> "M"
            else -> "U"
        }
        val ageGroup = when {
            isElder -> "elder"
            isYouth -> "youth"
            else -> "adult"
        }
        val archetype = when {
            isNarrator -> BreezeVoiceArchetype.NARRATOR
            isElder -> BreezeVoiceArchetype.ELDER
            gender == "F" && isYouth -> BreezeVoiceArchetype.YOUTH_FEMALE
            gender == "F" -> BreezeVoiceArchetype.MATURE_FEMALE
            gender == "M" && isYouth -> BreezeVoiceArchetype.YOUTH_MALE
            gender == "M" -> BreezeVoiceArchetype.MATURE_MALE
            else -> BreezeVoiceArchetype.GENERAL
        }
        return Triple(gender, ageGroup, archetype)
    }

    suspend fun syncVoices(
        apiKey: String = BreezeTtsConfig.apiKey,
        baseUrl: String = BreezeTtsConfig.baseUrl,
    ): Result<List<BreezeVoiceProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank()) throw BreezeAuthException("请先填写 Breeze API Key")
            val base = baseUrl.trimEnd('/')
            val queries = listOf(
                "$base/voices?voice_type=personal&page_size=50",
                "$base/voices?search=${URLEncoder.encode("普通话", "UTF-8")}&page_size=100",
                "$base/voices?search=${URLEncoder.encode("中文", "UTF-8")}&page_size=50",
            )

            val merged = LinkedHashMap<String, BreezeVoiceProfile>()
            for (qUrl in queries) {
                val fetched = runCatching { fetchVoicesPage(qUrl, apiKey) }.getOrNull().orEmpty()
                for (voice in fetched) {
                    if (isChineseOrPersonalVoice(voice)) {
                        merged.putIfAbsent(voice.voiceId, voice)
                    }
                }
            }

            val voices = if (merged.isNotEmpty()) {
                merged.values.sortedWith(
                    compareBy<BreezeVoiceProfile>(
                        { if (it.voiceType != "default") 0 else 1 },
                        { if (!isRegionalDialect(it.name, it.description)) 0 else 1 },
                    ),
                )
            } else {
                fetchVoicesPage("$base/voices?page_size=100", apiKey)
            }

            if (voices.isEmpty()) {
                throw BreezeApiException(200, "未从 Breeze 获取到可用音色")
            }
            BreezeCastStore.instance.saveVoiceCatalog(voices)
            autoFillDefaultVoices(voices)
            voices
        }
    }

    private fun fetchVoicesPage(url: String, apiKey: String): List<BreezeVoiceProfile> {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("xi-api-key", apiKey)
            .header("Accept", "application/json")
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body.string()
            if (!response.isSuccessful) {
                throw mapHttpError(response.code, bodyStr)
            }
            parseVoicesJson(bodyStr)
        }
    }

    private fun autoFillDefaultVoices(voices: List<BreezeVoiceProfile>) {
        val validIds = voices.map { it.voiceId }.toHashSet()
        val standardFirst = voices.sortedBy { if (!isRegionalDialect(it.name, it.description)) 0 else 1 }
        if (BreezeTtsConfig.narratorVoiceId.isBlank() || BreezeTtsConfig.narratorVoiceId !in validIds) {
            val narrator = standardFirst.firstOrNull { it.archetype == BreezeVoiceArchetype.NARRATOR && it.gender == "M" }
                ?: standardFirst.firstOrNull { it.archetype == BreezeVoiceArchetype.NARRATOR }
                ?: standardFirst.firstOrNull { it.gender == "M" }
                ?: standardFirst.first()
            BreezeTtsConfig.narratorVoiceId = narrator.voiceId
        }
        val narratorId = BreezeTtsConfig.narratorVoiceId
        if (BreezeTtsConfig.maleVoiceId.isBlank() || BreezeTtsConfig.maleVoiceId !in validIds) {
            val male = standardFirst.firstOrNull { it.archetype == BreezeVoiceArchetype.YOUTH_MALE && it.voiceId != narratorId }
                ?: standardFirst.firstOrNull { it.gender == "M" && it.voiceId != narratorId }
                ?: standardFirst.firstOrNull { it.gender == "M" }
                ?: standardFirst.first()
            BreezeTtsConfig.maleVoiceId = male.voiceId
        }
        if (BreezeTtsConfig.femaleVoiceId.isBlank() || BreezeTtsConfig.femaleVoiceId !in validIds) {
            val female = standardFirst.firstOrNull { it.voiceType != "default" && it.gender == "F" && it.voiceId != narratorId }
                ?: standardFirst.firstOrNull { it.gender == "F" && it.voiceId != narratorId }
                ?: standardFirst.firstOrNull { it.gender == "F" }
                ?: standardFirst.firstOrNull { it.voiceId != narratorId }
                ?: standardFirst.first()
            BreezeTtsConfig.femaleVoiceId = female.voiceId
        }
        if (BreezeTtsConfig.elderVoiceId.isBlank() || BreezeTtsConfig.elderVoiceId !in validIds) {
            val elder = standardFirst.firstOrNull {
                it.gender == "M" && it.ageGroup == "elder" &&
                    it.voiceId != narratorId && it.voiceId != BreezeTtsConfig.maleVoiceId
            }
                ?: standardFirst.firstOrNull { it.gender == "M" && it.ageGroup == "elder" }
                ?: standardFirst.firstOrNull { it.ageGroup == "elder" }
                ?: standardFirst.firstOrNull { it.voiceId == BreezeTtsConfig.maleVoiceId }
                ?: standardFirst.first()
            BreezeTtsConfig.elderVoiceId = elder.voiceId
        }
    }

    suspend fun streamSpeech(
        voiceId: String,
        text: String,
        instruction: String = "",
        apiKey: String = BreezeTtsConfig.apiKey,
        baseUrl: String = BreezeTtsConfig.baseUrl,
        modelId: String = BreezeTtsConfig.modelId,
    ): InputStream = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw BreezeAuthException("请先填写 Breeze API Key")
        val targetVoiceId = voiceId.ifBlank {
            BreezeTtsConfig.narratorVoiceId.ifBlank {
                throw BreezeApiException(400, "请先同步并选择默认音色")
            }
        }
        val encodedVoice = URLEncoder.encode(targetVoiceId, "UTF-8")
        val url = "${baseUrl.trimEnd('/')}/text-to-speech/$encodedVoice/stream?output_format=mp3"
        val payload = buildSpeechRequestJson(text = text, modelId = modelId, instruction = instruction)
        val retryDelays = longArrayOf(1200L, 2500L)

        var lastError: Throwable? = null
        for (attempt in 0..retryDelays.size) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .header("xi-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/mpeg, application/octet-stream")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (!response.isSuccessful) {
                    val errBody = response.body.string()
                    response.close()
                    val mapped = mapHttpError(response.code, errBody)
                    if (response.code == 429 && attempt < retryDelays.size) {
                        delay(retryDelays[attempt])
                        continue
                    }
                    throw mapped
                }
                if (contentType.contains("application/json") || contentType.startsWith("text/")) {
                    val errBody = response.body.string()
                    response.close()
                    throw mapHttpError(response.code, errBody)
                }
                val rawStream = response.body.byteStream()
                val pushback = PushbackInputStream(rawStream, 1)
                val firstByte = pushback.read()
                if (firstByte < 0) {
                    pushback.close()
                    throw IOException("Breeze TTS 返回了空音频流")
                }
                pushback.unread(firstByte)
                return@withContext pushback
            } catch (e: SocketTimeoutException) {
                lastError = e
                if (attempt < retryDelays.size) {
                    delay(retryDelays[attempt])
                    continue
                }
                throw e
            }
        }
        throw (lastError ?: IOException("Breeze TTS 请求失败"))
    }

    suspend fun designAndSaveCharacterVoice(
        voiceName: String,
        personaDescription: String,
        sampleText: String,
        apiKey: String = BreezeTtsConfig.apiKey,
        baseUrl: String = BreezeTtsConfig.baseUrl,
    ): Result<BreezeVoiceProfile> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank()) throw BreezeAuthException("请先填写 Breeze API Key")
            val designUrl = "${baseUrl.trimEnd('/')}/voice-previews/design/stream"
            val designBody = JsonObject().apply {
                addProperty("voice_description", personaDescription)
                addProperty("text", sampleText.ifBlank { "你好，我是$voiceName。" }.take(120))
                addProperty("guidance_scale", 4.0)
            }.toString()
            val designReq = Request.Builder()
                .url(designUrl)
                .post(designBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .build()

            val generatedId = okHttpClient.newCall(designReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw mapHttpError(resp.code, resp.body.string())
                }
                val headerId = resp.header("generated-voice-id")
                    ?: resp.header("x-generated-voice-id")
                    ?: throw BreezeApiException(resp.code, "未返回 generated-voice-id")
                resp.body.byteStream().use { stream ->
                    val buf = ByteArray(4096)
                    while (stream.read(buf) >= 0) { /* drain preview stream */ }
                }
                headerId
            }

            val saveUrl = "${baseUrl.trimEnd('/')}/voice-previews/${URLEncoder.encode(generatedId, "UTF-8")}/save"
            val saveBody = JsonObject().apply {
                addProperty("name", voiceName)
                addProperty("description", personaDescription)
            }.toString()
            val saveReq = Request.Builder()
                .url(saveUrl)
                .post(saveBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .build()

            val savedVoiceId = okHttpClient.newCall(saveReq).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    throw mapHttpError(resp.code, text)
                }
                runCatching {
                    val obj = JsonParser.parseString(text).asJsonObject
                    obj.str("voice_id").ifBlank { obj.str("id") }
                }.getOrDefault("")
            }

            val refreshed = syncVoices(apiKey, baseUrl).getOrDefault(emptyList())
            val found = refreshed.firstOrNull {
                (savedVoiceId.isNotBlank() && it.voiceId == savedVoiceId) || it.name == voiceName
            }
            if (found != null) return@runCatching found

            val (gender, ageGroup, archetype) = classifyVoice(voiceName, personaDescription)
            BreezeVoiceProfile(
                voiceId = savedVoiceId.ifBlank { generatedId },
                name = voiceName,
                description = personaDescription,
                voiceType = "personal",
                gender = gender,
                ageGroup = ageGroup,
                archetype = archetype,
            )
        }
    }

    private fun mapHttpError(code: Int, body: String): IOException {
        val lower = body.lowercase()
        return when {
            code == 402 || lower.contains("credit") || lower.contains("insufficient") ->
                BreezeCreditExhaustedException("Breeze 积分不足 ($code): ${body.take(160)}")
            code == 401 || code == 403 ->
                BreezeAuthException("Breeze API Key 鉴权失败 ($code)")
            else ->
                BreezeApiException(code, "Breeze 请求失败 ($code): ${body.take(160)}")
        }
    }

    private fun JsonObject.str(key: String): String {
        val el = get(key) ?: return ""
        return if (el.isJsonPrimitive) el.asString.trim() else ""
    }
}
