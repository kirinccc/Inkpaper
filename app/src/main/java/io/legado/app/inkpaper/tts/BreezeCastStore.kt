package io.legado.app.inkpaper.tts

import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

data class CharacterCastEntry(
    val name: String,
    val aliases: List<String> = emptyList(),
    val gender: String = "U",
    val ageGroup: String = "adult",
    val persona: String = "",
    val voiceId: String = "",
    val voiceName: String = "",
    val lockedByUser: Boolean = false,
    val lineCount: Int = 0,
)

data class AiCharacterHint(
    val name: String,
    val gender: String = "U",
    val ageGroup: String = "adult",
    val persona: String = "",
    val preferredVoiceId: String = "",
)

data class ResolvedVoice(
    val voiceId: String,
    val voiceName: String,
    val speakerDisplay: String,
)

class BreezeCastStore(
    private val storeDirProvider: () -> File,
) {
    private val lock = Any()

    @Volatile
    private var cachedCatalog: List<BreezeVoiceProfile>? = null
    private val bookCastCache = HashMap<String, MutableList<CharacterCastEntry>>()

    private fun dir(): File {
        val folder = storeDirProvider()
        if (!folder.isDirectory) folder.mkdirs()
        return folder
    }

    private fun catalogFile(): File = File(dir(), "voices_catalog.json")

    private fun bookCastFile(bookUrl: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(bookUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir(), "book_${hash}.json")
    }

    fun getVoiceCatalog(): List<BreezeVoiceProfile> = synchronized(lock) {
        cachedCatalog?.let { return it }
        val file = catalogFile()
        if (!file.isFile || file.length() == 0L) return emptyList()
        val parsed = runCatching {
            val type = object : TypeToken<List<BreezeVoiceProfile>>() {}.type
            GSON.fromJson<List<BreezeVoiceProfile>>(file.readText(Charsets.UTF_8), type).orEmpty()
        }.getOrDefault(emptyList())
        cachedCatalog = parsed
        parsed
    }

    fun saveVoiceCatalog(voices: List<BreezeVoiceProfile>) = synchronized(lock) {
        cachedCatalog = voices
        runCatching {
            catalogFile().writeText(GSON.toJson(voices), Charsets.UTF_8)
        }
    }

    fun findVoiceName(voiceId: String): String {
        if (voiceId.isBlank()) return ""
        return getVoiceCatalog().firstOrNull { it.voiceId == voiceId }?.name
            ?: voiceId.take(12)
    }

    fun getBookCast(bookUrl: String): List<CharacterCastEntry> = synchronized(lock) {
        loadBookCastLocked(bookUrl).sortedByDescending { it.lineCount }
    }

    private fun loadBookCastLocked(bookUrl: String): MutableList<CharacterCastEntry> {
        bookCastCache[bookUrl]?.let { return it }
        val file = bookCastFile(bookUrl)
        val loaded = if (file.isFile && file.length() > 0L) {
            runCatching {
                val raw = file.readText(Charsets.UTF_8)
                val element = JsonParser.parseString(raw)
                if (!element.isJsonArray) mutableListOf()
                else {
                    val type = object : TypeToken<List<CharacterCastEntry>>() {}.type
                    GSON.fromJson<List<CharacterCastEntry>>(element, type)?.toMutableList() ?: mutableListOf()
                }
            }.getOrElse { mutableListOf() }
        } else {
            mutableListOf()
        }
        bookCastCache[bookUrl] = loaded
        return loaded
    }

    private fun saveBookCastLocked(bookUrl: String, entries: List<CharacterCastEntry>) {
        runCatching {
            bookCastFile(bookUrl).writeText(GSON.toJson(entries), Charsets.UTF_8)
        }
    }

    fun resolveVoiceForSegment(
        bookUrl: String,
        segment: DramaSpeechSegment,
        defaultNarratorId: String,
        defaultMaleId: String,
        defaultFemaleId: String,
        defaultElderId: String,
        persistNewCharacter: Boolean = true,
    ): ResolvedVoice = synchronized(lock) {
        val catalog = getVoiceCatalog()
        val fallbackVoiceId = defaultNarratorId.ifBlank {
            catalog.firstOrNull()?.voiceId.orEmpty()
        }

        if (!segment.isDialogue || segment.speaker == NARRATOR_SPEAKER) {
            return ResolvedVoice(
                voiceId = fallbackVoiceId,
                voiceName = findVoiceName(fallbackVoiceId),
                speakerDisplay = NARRATOR_SPEAKER,
            )
        }

        val entries = loadBookCastLocked(bookUrl)
        val cleanName = sanitizeCharacterName(segment.speaker)

        if (cleanName != null && persistNewCharacter) {
            val existingIdx = entries.indexOfFirst { entry ->
                entry.name == cleanName || entry.aliases.contains(cleanName)
            }
            if (existingIdx >= 0) {
                val existing = entries[existingIdx]
                val validVoiceId = existing.voiceId.takeIf { id ->
                    id.isNotBlank() && (catalog.isEmpty() || catalog.any { it.voiceId == id })
                } ?: pickVoiceFromCatalog(
                    catalog = catalog,
                    usedEntries = entries,
                    gender = existing.gender.ifBlank { segment.genderHint },
                    ageGroup = existing.ageGroup.ifBlank { segment.ageHint },
                    narratorId = fallbackVoiceId,
                    defaultMaleId = defaultMaleId,
                    defaultFemaleId = defaultFemaleId,
                    defaultElderId = defaultElderId,
                )
                val updated = existing.copy(
                    voiceId = validVoiceId,
                    voiceName = findVoiceName(validVoiceId),
                    lineCount = existing.lineCount + 1,
                )
                entries[existingIdx] = updated
                if (updated.lineCount == 1 || updated.lineCount % 5 == 0 || existing.voiceId != validVoiceId) {
                    saveBookCastLocked(bookUrl, entries)
                }
                return ResolvedVoice(
                    voiceId = updated.voiceId,
                    voiceName = updated.voiceName,
                    speakerDisplay = updated.name,
                )
            }

            val chosenId = pickVoiceFromCatalog(
                catalog = catalog,
                usedEntries = entries,
                gender = segment.genderHint,
                ageGroup = segment.ageHint,
                narratorId = fallbackVoiceId,
                defaultMaleId = defaultMaleId,
                defaultFemaleId = defaultFemaleId,
                defaultElderId = defaultElderId,
            )
            val created = CharacterCastEntry(
                name = cleanName,
                gender = segment.genderHint,
                ageGroup = segment.ageHint,
                voiceId = chosenId,
                voiceName = findVoiceName(chosenId),
                lineCount = 1,
            )
            entries.add(created)
            saveBookCastLocked(bookUrl, entries)
            return ResolvedVoice(
                voiceId = chosenId,
                voiceName = created.voiceName,
                speakerDisplay = cleanName,
            )
        }

        if (cleanName != null && !persistNewCharacter) {
            val existing = entries.firstOrNull { it.name == cleanName || it.aliases.contains(cleanName) }
            if (existing != null && existing.voiceId.isNotBlank()) {
                return ResolvedVoice(
                    voiceId = existing.voiceId,
                    voiceName = findVoiceName(existing.voiceId),
                    speakerDisplay = existing.name,
                )
            }
        }

        val genericId = routeGenericVoice(
            catalog = catalog,
            gender = segment.genderHint,
            ageGroup = segment.ageHint,
            narratorId = fallbackVoiceId,
            defaultMaleId = defaultMaleId,
            defaultFemaleId = defaultFemaleId,
            defaultElderId = defaultElderId,
        )
        return ResolvedVoice(
            voiceId = genericId,
            voiceName = findVoiceName(genericId),
            speakerDisplay = segment.speaker.ifBlank { "人物对白" },
        )
    }

    fun assignCharacterVoice(
        bookUrl: String,
        characterName: String,
        voice: BreezeVoiceProfile,
    ) = synchronized(lock) {
        val entries = loadBookCastLocked(bookUrl)
        val idx = entries.indexOfFirst { it.name == characterName || it.aliases.contains(characterName) }
        if (idx >= 0) {
            val old = entries[idx]
            entries[idx] = old.copy(
                voiceId = voice.voiceId,
                voiceName = voice.name,
                lockedByUser = true,
            )
        } else {
            entries.add(
                CharacterCastEntry(
                    name = characterName,
                    gender = voice.gender,
                    ageGroup = voice.ageGroup,
                    voiceId = voice.voiceId,
                    voiceName = voice.name,
                    lockedByUser = true,
                    lineCount = 1,
                ),
            )
        }
        saveBookCastLocked(bookUrl, entries)
    }

    fun mergeAlias(
        bookUrl: String,
        primaryName: String,
        aliasName: String,
    ): Boolean = synchronized(lock) {
        if (primaryName == aliasName) return false
        val entries = loadBookCastLocked(bookUrl)
        val primaryIdx = entries.indexOfFirst { it.name == primaryName }
        val aliasIdx = entries.indexOfFirst { it.name == aliasName }
        if (primaryIdx < 0 || aliasIdx < 0) return false
        val primary = entries[primaryIdx]
        val alias = entries[aliasIdx]
        val mergedAliases = (primary.aliases + aliasName + alias.aliases)
            .filter { it != primaryName }
            .distinct()
        entries[primaryIdx] = primary.copy(
            aliases = mergedAliases,
            lineCount = primary.lineCount + alias.lineCount,
        )
        entries.removeAt(aliasIdx)
        saveBookCastLocked(bookUrl, entries)
        true
    }

    fun resetUnlockedVoices(
        bookUrl: String,
        narratorId: String,
        defaultMaleId: String,
        defaultFemaleId: String,
        defaultElderId: String,
    ) = synchronized(lock) {
        val entries = loadBookCastLocked(bookUrl)
        val catalog = getVoiceCatalog()
        val lockedEntries = entries.filter { it.lockedByUser }.toMutableList()
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.lockedByUser) continue
            val newId = pickVoiceFromCatalog(
                catalog = catalog,
                usedEntries = lockedEntries,
                gender = entry.gender,
                ageGroup = entry.ageGroup,
                narratorId = narratorId,
                defaultMaleId = defaultMaleId,
                defaultFemaleId = defaultFemaleId,
                defaultElderId = defaultElderId,
            )
            val updated = entry.copy(voiceId = newId, voiceName = findVoiceName(newId))
            entries[i] = updated
            lockedEntries.add(updated)
        }
        saveBookCastLocked(bookUrl, entries)
    }

    fun buildCastingRosterForPrompt(narratorId: String): String = synchronized(lock) {
        val catalog = getVoiceCatalog()
        if (catalog.isEmpty()) return ""
        val pool = catalog.filter { it.voiceId != narratorId }.ifEmpty { catalog }
        val sortedPool = pool.sortedWith(
            compareBy<BreezeVoiceProfile>(
                { if (it.voiceType != "default") 0 else 1 },
                { if (!BreezeApiClient.isRegionalDialect(it.name, it.description)) 0 else 1 },
            ),
        )
        val males = sortedPool.filter { it.gender == "M" }
        val maleSelected = (males.filter { it.ageGroup == "elder" }.take(2) +
            males.filter { it.ageGroup != "elder" }.take(6)).distinctBy { it.voiceId }.take(8)
        val femaleSelected = sortedPool.filter { it.gender == "F" }.take(8)
        val neutralSelected = sortedPool.filter { it.gender == "U" }.take(2)

        return (maleSelected + femaleSelected + neutralSelected).joinToString("\n") { v ->
            val genderLabel = when (v.gender) {
                "M" -> "男"
                "F" -> "女"
                else -> "中性"
            }
            val desc = v.description.replace('\n', ' ').take(48)
            "- ${v.voiceId} | $genderLabel | ${v.name}：$desc"
        }
    }

    fun upsertAiCharacters(
        bookUrl: String,
        hints: List<AiCharacterHint>,
        narratorId: String,
        defaultMaleId: String,
        defaultFemaleId: String,
        defaultElderId: String,
    ) = synchronized(lock) {
        val entries = loadBookCastLocked(bookUrl)
        val catalog = getVoiceCatalog()
        var changed = false
        for (hint in hints) {
            val clean = sanitizeCharacterName(hint.name) ?: continue
            val idx = entries.indexOfFirst { it.name == clean || it.aliases.contains(clean) }
            val validPreferred = hint.preferredVoiceId.takeIf { id ->
                id.isNotBlank() && id != narratorId && catalog.any { v ->
                    v.voiceId == id && (hint.gender == "U" || v.gender == hint.gender)
                }
            }
            if (idx >= 0) {
                val existing = entries[idx]
                val nextGender = if (existing.gender == "U" && hint.gender != "U") hint.gender else existing.gender
                val nextAge = if (existing.ageGroup == "adult" && hint.ageGroup != "adult") hint.ageGroup else existing.ageGroup
                val nextPersona = existing.persona.ifBlank { hint.persona }
                val currentVoiceProfile = catalog.firstOrNull { it.voiceId == existing.voiceId }
                val currentGenderMismatch = nextGender != "U" &&
                    currentVoiceProfile != null &&
                    currentVoiceProfile.gender != nextGender
                val nextVoiceId = when {
                    !existing.lockedByUser && validPreferred != null && existing.voiceId != validPreferred -> validPreferred
                    !existing.lockedByUser && currentGenderMismatch -> pickVoiceFromCatalog(
                        catalog = catalog,
                        usedEntries = entries,
                        gender = nextGender,
                        ageGroup = nextAge,
                        narratorId = narratorId,
                        defaultMaleId = defaultMaleId,
                        defaultFemaleId = defaultFemaleId,
                        defaultElderId = defaultElderId,
                        persona = nextPersona,
                    )
                    else -> existing.voiceId
                }
                if (nextGender != existing.gender ||
                    nextAge != existing.ageGroup ||
                    nextPersona != existing.persona ||
                    nextVoiceId != existing.voiceId
                ) {
                    entries[idx] = existing.copy(
                        gender = nextGender,
                        ageGroup = nextAge,
                        persona = nextPersona,
                        voiceId = nextVoiceId,
                        voiceName = findVoiceName(nextVoiceId),
                    )
                    changed = true
                }
            } else {
                val voiceId = validPreferred ?: pickVoiceFromCatalog(
                    catalog = catalog,
                    usedEntries = entries,
                    gender = hint.gender,
                    ageGroup = hint.ageGroup,
                    narratorId = narratorId,
                    defaultMaleId = defaultMaleId,
                    defaultFemaleId = defaultFemaleId,
                    defaultElderId = defaultElderId,
                    persona = hint.persona,
                )
                entries.add(
                    CharacterCastEntry(
                        name = clean,
                        gender = hint.gender,
                        ageGroup = hint.ageGroup,
                        persona = hint.persona,
                        voiceId = voiceId,
                        voiceName = findVoiceName(voiceId),
                    ),
                )
                changed = true
            }
        }
        if (changed) saveBookCastLocked(bookUrl, entries)
    }

    private fun routeGenericVoice(
        catalog: List<BreezeVoiceProfile>,
        gender: String,
        ageGroup: String,
        narratorId: String,
        defaultMaleId: String,
        defaultFemaleId: String,
        defaultElderId: String,
    ): String {
        if (ageGroup == "elder" && defaultElderId.isNotBlank()) return defaultElderId
        if (gender == "F" && defaultFemaleId.isNotBlank()) return defaultFemaleId
        if (gender == "M" && defaultMaleId.isNotBlank()) return defaultMaleId
        val nonNarrator = catalog.firstOrNull { it.voiceId != narratorId }?.voiceId
        return defaultMaleId.ifBlank { defaultFemaleId }.ifBlank { nonNarrator.orEmpty() }.ifBlank { narratorId }
    }

    private fun pickVoiceFromCatalog(
        catalog: List<BreezeVoiceProfile>,
        usedEntries: List<CharacterCastEntry>,
        gender: String,
        ageGroup: String,
        narratorId: String,
        defaultMaleId: String,
        defaultFemaleId: String,
        defaultElderId: String,
        persona: String = "",
    ): String {
        if (catalog.isEmpty()) {
            return routeGenericVoice(catalog, gender, ageGroup, narratorId, defaultMaleId, defaultFemaleId, defaultElderId)
        }
        val usageCounts = usedEntries.groupingBy { it.voiceId }.eachCount()
        val nonNarratorPool = catalog.filter { it.voiceId != narratorId }.ifEmpty { catalog }

        // Strict gender guard: M never falls back to U/F, F never falls back to U/M unless pool is empty.
        val genderPool = when (gender) {
            "M" -> nonNarratorPool.filter { it.gender == "M" }.ifEmpty { nonNarratorPool }
            "F" -> nonNarratorPool.filter { it.gender == "F" }.ifEmpty { nonNarratorPool }
            else -> nonNarratorPool.filter { it.gender == "U" }.ifEmpty { nonNarratorPool }
        }

        val personaTraits = listOf(
            listOf("清冷", "冷静", "疏离", "强势", "御姐", "冷峻", "高冷"),
            listOf("温柔", "亲切", "暖", "柔和", "关怀", "邻家"),
            listOf("活泼", "机灵", "俏皮", "甜", "少女", "迷糊", "奶气", "元气"),
            listOf("沉稳", "稳重", "低沉", "成熟", "儒雅", "厚实"),
            listOf("苍老", "老者", "长者", "大爷", "老叟", "老爷子"),
            listOf("系统", "机械", "电子", "理性", "客服", "助手"),
        )
        val allowDialect = persona.contains("方言") || persona.contains("口音")

        val best = genderPool.maxByOrNull { voice ->
            var score = 0
            if (voice.voiceType != "default") score += 20
            if (!allowDialect && !BreezeApiClient.isRegionalDialect(voice.name, voice.description)) {
                score += 40
            }
            if (ageGroup == "elder") {
                score += if (voice.ageGroup == "elder") 25 else -10
            } else if (ageGroup == "youth") {
                if (voice.ageGroup == "youth") score += 15
                if (voice.ageGroup == "elder") score -= 25
            } else {
                if (voice.ageGroup == "elder") score -= 20
                if (voice.description.contains("奶气") || voice.description.contains("猫娘")) score -= 18
            }
            val voiceText = "${voice.name} ${voice.description}"
            if (persona.isNotBlank()) {
                for (group in personaTraits) {
                    if (group.any { persona.contains(it) } && group.any { voiceText.contains(it) }) {
                        score += 18
                    }
                }
            }
            score -= (usageCounts[voice.voiceId] ?: 0) * 12
            score
        }
        return best?.voiceId ?: routeGenericVoice(
            catalog, gender, ageGroup, narratorId, defaultMaleId, defaultFemaleId, defaultElderId,
        )
    }

    companion object {
        const val NARRATOR_SPEAKER = "旁白"

        private val INVALID_SPEAKERS = setOf(
            "旁白", "叙述", "叙述者", "作者", "未知", "匿名", "众人", "大家", "有人", "某人",
            "两人", "二人", "众人齐声", "他", "她", "它", "我", "你", "他们", "她们", "男人",
            "女人", "男子", "女子", "少年", "少女", "老者", "老人", "孩童", "青年", "大汉",
            "壮汉", "妇人", "路人", "声音", "男声", "女声", "心声", "人物对白", "对白",
        )

        private val SPEECH_VERB_SUFFIXES = listOf(
            "说道", "笑道", "怒道", "叹道", "喝道", "问道", "答道", "冷笑", "开口", "低语", "喃喃",
        )

        private val NON_NAME_FRAGMENTS = listOf(
            "的", "用", "着", "了", "把", "被", "在", "从", "向", "对",
            "口音", "语气", "神色", "表情", "声音", "电视", "广播", "手机", "内心", "心里",
        )

        fun sanitizeCharacterName(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim()
                .trim('"', '“', '”', '「', '」', '『', '』', '：', ':', '，', ',', '。', ' ', '\t', '(', ')', '（', '）')
            if (cleaned.length !in 1..5) return null
            if (cleaned in INVALID_SPEAKERS) return null
            if (SPEECH_VERB_SUFFIXES.any { cleaned.endsWith(it) }) return null
            if (NON_NAME_FRAGMENTS.any { cleaned.contains(it) }) return null
            if (cleaned.all { it.isDigit() || !it.isLetter() }) return null
            return cleaned
        }

        val instance: BreezeCastStore by lazy {
            BreezeCastStore { File(appCtx.filesDir, "breeze_cast") }
        }
    }
}
