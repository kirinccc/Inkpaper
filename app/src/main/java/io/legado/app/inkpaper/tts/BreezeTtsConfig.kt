package io.legado.app.inkpaper.tts

import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx

enum class BreezeDramaMode(
    val id: String,
    val label: String,
    val subtitle: String,
) {
    SINGLE_NARRATOR("single", "单人说书", "整段连贯朗读，最省请求"),
    LOCAL_MULTI_VOICE("local_multi", "旁白+对白", "本地引号与语气词规则，零延迟免LLM"),
    AI_DIRECTOR("ai_director", "AI 多角色广播剧", "LLM 识别具名角色与情绪，跨章固定声优");

    companion object {
        fun fromId(id: String?): BreezeDramaMode {
            return entries.firstOrNull { it.id == id } ?: LOCAL_MULTI_VOICE
        }
    }
}

enum class BreezeStylePreset(
    val id: String,
    val label: String,
    val instruction: String,
) {
    DEFAULT("default", "自然叙事", ""),
    SUSPENSE("suspense", "悬疑低沉", "语调低沉克制，节奏沉稳，略带悬疑张力。"),
    XIANXIA("xianxia", "古风说书", "温润沉稳的说书人语气，咬字清晰，古韵悠长。"),
    LIVELY("lively", "轻松明快", "语气轻松自然，富有生机与亲和力。"),
    BEDTIME("bedtime", "睡前轻柔", "声音轻柔舒缓，语速略慢，温暖平静。");

    companion object {
        fun fromId(id: String?): BreezeStylePreset {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}

enum class BreezeVoiceArchetype(val label: String) {
    NARRATOR("旁白"),
    YOUTH_MALE("青年男"),
    MATURE_MALE("沉稳男"),
    ELDER("长者"),
    YOUTH_FEMALE("少女"),
    MATURE_FEMALE("御姐"),
    GENERAL("通用"),
}

data class BreezeVoiceProfile(
    val voiceId: String,
    val name: String,
    val description: String = "",
    val voiceType: String = "default",
    val gender: String = "U",
    val ageGroup: String = "adult",
    val archetype: BreezeVoiceArchetype = BreezeVoiceArchetype.GENERAL,
    val previewUrl: String? = null,
)

data class BreezeLiveStatus(
    val isRunning: Boolean = false,
    val isBuffering: Boolean = false,
    val speaker: String = "",
    val emotion: String = "",
    val voiceName: String = "",
    val directorBadge: String = "",
    val errorMessage: String? = null,
)

object BreezePlaybackTracker {
    private val _status = MutableStateFlow(BreezeLiveStatus())
    val status: StateFlow<BreezeLiveStatus> = _status.asStateFlow()

    fun update(transform: (BreezeLiveStatus) -> BreezeLiveStatus) {
        while (true) {
            val current = _status.value
            val next = transform(current)
            if (_status.compareAndSet(current, next)) break
        }
    }

    fun reset() {
        _status.value = BreezeLiveStatus()
    }
}

object BreezeTtsConfig {
    const val ENGINE_ID = "breeze_tts_2"
    const val ENGINE_DISPLAY_NAME = "Breeze TTS 2 · 多角色有声剧"
    const val DEFAULT_BASE_URL = "https://api.breeze.blue/v1"
    const val DEFAULT_MODEL_ID = "breeze-tts-2"
    const val DEFAULT_LLM_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_LLM_MODEL = "deepseek-chat"

    private const val KEY_API_KEY = "breeze_tts_api_key"
    private const val KEY_BASE_URL = "breeze_tts_base_url"
    private const val KEY_MODEL_ID = "breeze_tts_model_id"
    private const val KEY_SPEED_MULTIPLIER = "breeze_tts_speed_multiplier"
    private const val KEY_DRAMA_MODE = "breeze_tts_drama_mode"
    private const val KEY_STYLE_PRESET = "breeze_tts_style_preset"
    private const val KEY_NARRATOR_VOICE_ID = "breeze_tts_narrator_voice_id"
    private const val KEY_MALE_VOICE_ID = "breeze_tts_male_voice_id"
    private const val KEY_FEMALE_VOICE_ID = "breeze_tts_female_voice_id"
    private const val KEY_ELDER_VOICE_ID = "breeze_tts_elder_voice_id"
    private const val KEY_LLM_BASE_URL = "breeze_tts_llm_base_url"
    private const val KEY_LLM_API_KEY = "breeze_tts_llm_api_key"
    private const val KEY_LLM_MODEL = "breeze_tts_llm_model"
    private const val KEY_AUTO_FALLBACK = "breeze_tts_auto_fallback"

    private val prefs by lazy { appCtx.defaultSharedPreferences }
    private val _configVersion = MutableStateFlow(0)
    val configVersion: StateFlow<Int> = _configVersion.asStateFlow()

    private fun bump() {
        _configVersion.value = _configVersion.value + 1
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
            bump()
        }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_BASE_URL
        set(value) {
            val normalized = value.trim().ifEmpty { DEFAULT_BASE_URL }
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
            bump()
        }

    var modelId: String
        get() = prefs.getString(KEY_MODEL_ID, DEFAULT_MODEL_ID)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_MODEL_ID
        set(value) {
            val normalized = value.trim().ifEmpty { DEFAULT_MODEL_ID }
            prefs.edit().putString(KEY_MODEL_ID, normalized).apply()
            bump()
        }

    var speedMultiplier: Float
        get() = prefs.getFloat(KEY_SPEED_MULTIPLIER, 1.0f).coerceIn(0.75f, 2.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEED_MULTIPLIER, value.coerceIn(0.75f, 2.0f)).apply()
            bump()
        }

    var dramaMode: BreezeDramaMode
        get() = BreezeDramaMode.fromId(prefs.getString(KEY_DRAMA_MODE, BreezeDramaMode.LOCAL_MULTI_VOICE.id))
        set(value) {
            prefs.edit().putString(KEY_DRAMA_MODE, value.id).apply()
            bump()
        }

    var stylePreset: BreezeStylePreset
        get() = BreezeStylePreset.fromId(prefs.getString(KEY_STYLE_PRESET, BreezeStylePreset.DEFAULT.id))
        set(value) {
            prefs.edit().putString(KEY_STYLE_PRESET, value.id).apply()
            bump()
        }

    var narratorVoiceId: String
        get() = prefs.getString(KEY_NARRATOR_VOICE_ID, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_NARRATOR_VOICE_ID, value.trim()).apply()
            bump()
        }

    var maleVoiceId: String
        get() = prefs.getString(KEY_MALE_VOICE_ID, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_MALE_VOICE_ID, value.trim()).apply()
            bump()
        }

    var femaleVoiceId: String
        get() = prefs.getString(KEY_FEMALE_VOICE_ID, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FEMALE_VOICE_ID, value.trim()).apply()
            bump()
        }

    var elderVoiceId: String
        get() = prefs.getString(KEY_ELDER_VOICE_ID, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ELDER_VOICE_ID, value.trim()).apply()
            bump()
        }

    var llmBaseUrl: String
        get() = prefs.getString(KEY_LLM_BASE_URL, DEFAULT_LLM_BASE_URL)?.trim()
            .takeUnless { it.isNullOrEmpty() } ?: DEFAULT_LLM_BASE_URL
        set(value) {
            val normalized = value.trim().ifEmpty { DEFAULT_LLM_BASE_URL }
            prefs.edit().putString(KEY_LLM_BASE_URL, normalized).apply()
            bump()
        }

    var llmApiKey: String
        get() = prefs.getString(KEY_LLM_API_KEY, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LLM_API_KEY, value.trim()).apply()
            bump()
        }

    var llmModel: String
        get() = prefs.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL)?.trim()
            .takeUnless { it.isNullOrEmpty() } ?: DEFAULT_LLM_MODEL
        set(value) {
            val normalized = value.trim().ifEmpty { DEFAULT_LLM_MODEL }
            prefs.edit().putString(KEY_LLM_MODEL, normalized).apply()
            bump()
        }

    var autoFallbackOnCreditExhausted: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FALLBACK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_FALLBACK, value).apply()
            bump()
        }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    fun hasLlmConfig(): Boolean = llmApiKey.isNotBlank() && llmBaseUrl.isNotBlank() && llmModel.isNotBlank()
}
