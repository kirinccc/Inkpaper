package io.legado.app.inkpaper.ui.reader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.help.config.AppConfig
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSegmentedControl
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.tts.BreezeAiDirector
import io.legado.app.inkpaper.tts.BreezeApiClient
import io.legado.app.inkpaper.tts.BreezeAudioCache
import io.legado.app.inkpaper.tts.BreezeCastStore
import io.legado.app.inkpaper.tts.BreezeDramaMode
import io.legado.app.inkpaper.tts.BreezeStylePreset
import io.legado.app.inkpaper.tts.BreezeTtsConfig
import io.legado.app.inkpaper.tts.BreezeVoiceProfile
import io.legado.app.inkpaper.tts.CharacterCastEntry
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.generateSilentWavBytes
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class VoiceSlotTarget(val title: String) {
    NARRATOR("旁白音色"),
    MALE("默认男声"),
    FEMALE("默认女声"),
    ELDER("老者音色"),
}

private class VoicePreviewPlayer(private val context: Context) {
    private var player: ExoPlayer? = null

    suspend fun playPreviewUrlOrSample(voice: BreezeVoiceProfile, sampleText: String) {
        if (!voice.previewUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                val exo = player ?: ExoPlayer.Builder(context).build().also { player = it }
                exo.stop()
                exo.clearMediaItems()
                exo.setMediaItem(MediaItem.fromUri(voice.previewUrl))
                exo.prepare()
                exo.play()
            }
            return
        }
        playSample(voice.voiceId, sampleText)
    }

    suspend fun playSample(voiceId: String, sampleText: String, instructions: String = "") {
        if (voiceId.isBlank()) {
            context.toastOnUi("请先同步并选择音色")
            return
        }
        val key = BreezeAudioCache.instance.cacheKey(BreezeTtsConfig.modelId, voiceId, instructions, sampleText)
        val file = BreezeAudioCache.instance.getReusableFile(key) ?: withContext(Dispatchers.IO) {
            BreezeApiClient.streamSpeech(
                voiceId = voiceId,
                text = sampleText,
                instruction = instructions,
            ).use { input ->
                BreezeAudioCache.instance.writeStream(key, input) {}
            }
        }
        withContext(Dispatchers.Main) {
            val exo = player ?: ExoPlayer.Builder(context).build().also { player = it }
            exo.stop()
            exo.clearMediaItems()
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exo.prepare()
            exo.play()
        }
    }

    suspend fun playMultiRoleDemo(narratorId: String, maleId: String, femaleId: String) {
        val nId = narratorId.ifBlank { maleId }.ifBlank { femaleId }
        val mId = maleId.ifBlank { nId }
        val fId = femaleId.ifBlank { nId }
        if (nId.isBlank()) {
            context.toastOnUi("请先点击上方按钮同步中文音色")
            return
        }

        val lines = listOf(
            Triple(nId, "夜风微凉，林凡嗤笑一声：", BreezeTtsConfig.stylePreset.instruction),
            Triple(mId, "[轻笑] 就凭你们几个，也想拦我？", "冷淡嘲讽，带着一丝不屑"),
            Triple(fId, "[叹气] 算了，别把事情闹大。", "温柔低声劝解，略带无奈"),
        )
        val pauseFile = withContext(Dispatchers.IO) {
            BreezeAudioCache.instance.getOrCreateSilenceFile(120, generateSilentWavBytes(120))
        }
        val mediaItems = ArrayList<MediaItem>(lines.size * 2)
        for ((index, spec) in lines.withIndex()) {
            val (vid, text, inst) = spec
            val key = BreezeAudioCache.instance.cacheKey(BreezeTtsConfig.modelId, vid, inst, text)
            val file = BreezeAudioCache.instance.getReusableFile(key) ?: withContext(Dispatchers.IO) {
                BreezeApiClient.streamSpeech(voiceId = vid, text = text, instruction = inst).use { input ->
                    BreezeAudioCache.instance.writeStream(key, input) {}
                }
            }
            mediaItems.add(MediaItem.fromUri(Uri.fromFile(file)))
            if (index < lines.lastIndex) {
                mediaItems.add(MediaItem.fromUri(Uri.fromFile(pauseFile)))
            }
        }
        withContext(Dispatchers.Main) {
            val exo = player ?: ExoPlayer.Builder(context).build().also { player = it }
            exo.stop()
            exo.clearMediaItems()
            exo.setMediaItems(mediaItems)
            exo.prepare()
            exo.play()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkBreezeConfigSheet(
    onDismiss: () -> Unit,
    onOpenLegacyEngines: (() -> Unit)? = null,
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configVersion by BreezeTtsConfig.configVersion.collectAsStateWithLifecycle()

    val previewPlayer = remember(context) { VoicePreviewPlayer(context) }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    var breezeEnabled by remember(configVersion) {
        mutableStateOf(ReadAloud.ttsEngine == BreezeTtsConfig.ENGINE_ID)
    }
    var apiKey by remember { mutableStateOf(BreezeTtsConfig.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(BreezeTtsConfig.baseUrl) }
    var dramaMode by remember(configVersion) { mutableStateOf(BreezeTtsConfig.dramaMode) }
    var stylePreset by remember(configVersion) { mutableStateOf(BreezeTtsConfig.stylePreset) }
    var autoFallback by remember(configVersion) { mutableStateOf(BreezeTtsConfig.autoFallbackOnCreditExhausted) }

    var llmBaseUrl by remember { mutableStateOf(BreezeTtsConfig.llmBaseUrl) }
    var llmApiKey by remember { mutableStateOf(BreezeTtsConfig.llmApiKey) }
    var showLlmKey by remember { mutableStateOf(false) }
    var llmModel by remember { mutableStateOf(BreezeTtsConfig.llmModel) }
    var showAdvancedUrls by remember { mutableStateOf(false) }

    var voices by remember(configVersion) { mutableStateOf(BreezeCastStore.instance.getVoiceCatalog()) }
    var isSyncing by remember { mutableStateOf(false) }
    var isDemoPlaying by remember { mutableStateOf(false) }
    var isTestingLlm by remember { mutableStateOf(false) }
    var pickingSlot by remember { mutableStateOf<VoiceSlotTarget?>(null) }
    var cacheRefreshTick by remember { mutableIntStateOf(0) }
    val cacheStats = remember(cacheRefreshTick) { BreezeAudioCache.instance.stats() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Breeze TTS 2 有声剧工作室",
                        color = colors.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val readiness = buildString {
                        append(if (breezeEnabled) "引擎已启用" else "未启用")
                        append(" · ")
                        append(if (voices.isNotEmpty()) "${voices.size} 个中文音色" else "待同步音色")
                        if (BreezeTtsConfig.hasLlmConfig()) append(" · AI导演就绪")
                    }
                    Text(
                        text = readiness,
                        color = colors.inkSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (onOpenLegacyEngines != null) {
                    Text(
                        text = "其他引擎",
                        color = colors.inkSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                onDismiss()
                                onOpenLegacyEngines()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            InkCardGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("1. Breeze 密钥与一键配置", color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            BreezeTtsConfig.apiKey = it
                        },
                        label = { Text("Breeze API Key (xi-api-key)") },
                        placeholder = { Text("brz_...") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (isSyncing) return@Button
                            isSyncing = true
                            ReadBook.book?.setTtsEngine(null)
                            AppConfig.ttsEngine = BreezeTtsConfig.ENGINE_ID
                            breezeEnabled = true
                            ReadAloud.upReadAloudClass()
                            scope.launch {
                                val res = BreezeApiClient.syncVoices(apiKey = apiKey, baseUrl = baseUrl)
                                isSyncing = false
                                res.onSuccess { list ->
                                    voices = list
                                    context.toastOnUi("已启用 Breeze 并同步 ${list.size} 个中文/个人音色")
                                }.onFailure { err ->
                                    context.toastOnUi(err.localizedMessage ?: "同步失败")
                                }
                            }
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.inkOnSolid,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.inkOnSolid,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (voices.isEmpty()) "一键启用并拉取中文音色" else "重新同步中文音色（已就绪 ${voices.size} 个）")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. 演播模式与氛围", color = colors.inkSecondary, fontSize = 13.sp)
                val modes = BreezeDramaMode.entries
                InkSegmentedControl(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(dramaMode).coerceAtLeast(0),
                    onSelect = { idx ->
                        val picked = modes[idx]
                        dramaMode = picked
                        BreezeTtsConfig.dramaMode = picked
                    },
                )
                Text(text = dramaMode.subtitle, color = colors.inkSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BreezeStylePreset.entries.forEach { preset ->
                        InkFilterChip(
                            text = preset.label,
                            selected = stylePreset == preset,
                            onClick = {
                                stylePreset = preset
                                BreezeTtsConfig.stylePreset = preset
                            },
                        )
                    }
                }
            }

            InkCardGroup {
                VoiceSlotTarget.entries.forEachIndexed { idx, slot ->
                    val currentVoiceId = when (slot) {
                        VoiceSlotTarget.NARRATOR -> BreezeTtsConfig.narratorVoiceId
                        VoiceSlotTarget.MALE -> BreezeTtsConfig.maleVoiceId
                        VoiceSlotTarget.FEMALE -> BreezeTtsConfig.femaleVoiceId
                        VoiceSlotTarget.ELDER -> BreezeTtsConfig.elderVoiceId
                    }
                    val profile = voices.firstOrNull { it.voiceId == currentVoiceId }
                    val voiceName = profile?.name ?: BreezeCastStore.instance.findVoiceName(currentVoiceId)
                        .ifBlank { "未选择（点击上方按钮同步）" }
                    InkListRow(
                        title = slot.title,
                        subtitle = voiceName,
                        showDivider = true,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentVoiceId.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    if (profile != null) {
                                                        previewPlayer.playPreviewUrlOrSample(
                                                            profile,
                                                            "夜风卷起残叶，且听这段书声。",
                                                        )
                                                    } else {
                                                        previewPlayer.playSample(
                                                            currentVoiceId,
                                                            "夜风卷起残叶，且听这段书声。",
                                                        )
                                                    }
                                                }.onFailure { context.toastOnUi(it.localizedMessage ?: "试听失败") }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.PlayArrow,
                                            contentDescription = "试听",
                                            tint = colors.inkSecondary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.inkSecondary,
                                )
                            }
                        },
                        onClick = {
                            if (voices.isEmpty()) {
                                context.toastOnUi("请先点击上方「一键启用并拉取中文音色」")
                            } else {
                                pickingSlot = slot
                            }
                        },
                    )
                }
                InkListRow(
                    title = if (isDemoPlaying) "正在合成三段多角色试听..." else "试听「旁白 + 男女对白」效果",
                    subtitle = "立即用当前选定音色连播旁白、男声[轻笑]、女声[叹气]",
                    showDivider = false,
                    trailing = {
                        if (isDemoPlaying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = colors.ink)
                        }
                    },
                    onClick = {
                        if (isDemoPlaying) return@InkListRow
                        isDemoPlaying = true
                        scope.launch {
                            runCatching {
                                previewPlayer.playMultiRoleDemo(
                                    narratorId = BreezeTtsConfig.narratorVoiceId,
                                    maleId = BreezeTtsConfig.maleVoiceId,
                                    femaleId = BreezeTtsConfig.femaleVoiceId,
                                )
                            }.onFailure {
                                context.toastOnUi(it.localizedMessage ?: "试听失败")
                            }
                            isDemoPlaying = false
                        }
                    },
                )
            }

            InkCardGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("3. AI 导演大模型（自动识别具名角色）", color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("填入 Key 后自动切入多角色精准分角", color = colors.inkSecondary, fontSize = 11.5.sp)
                        }
                        InkSwitch(
                            checked = dramaMode == BreezeDramaMode.AI_DIRECTOR,
                            onCheckedChange = { enable ->
                                val next = if (enable) BreezeDramaMode.AI_DIRECTOR else BreezeDramaMode.LOCAL_MULTI_VOICE
                                dramaMode = next
                                BreezeTtsConfig.dramaMode = next
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presets = listOf(
                            Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
                            Triple("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo"),
                            Triple("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
                        )
                        presets.forEach { (label, url, model) ->
                            InkFilterChip(
                                text = label,
                                selected = llmBaseUrl.contains(url.substringAfter("://").substringBefore("/")),
                                onClick = {
                                    llmBaseUrl = url
                                    llmModel = model
                                    BreezeTtsConfig.llmBaseUrl = url
                                    BreezeTtsConfig.llmModel = model
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = llmApiKey,
                        onValueChange = {
                            llmApiKey = it
                            BreezeTtsConfig.llmApiKey = it
                            if (it.isNotBlank() && dramaMode != BreezeDramaMode.AI_DIRECTOR) {
                                dramaMode = BreezeDramaMode.AI_DIRECTOR
                                BreezeTtsConfig.dramaMode = BreezeDramaMode.AI_DIRECTOR
                            }
                        },
                        label = { Text("LLM API Key (如 DeepSeek sk-...)") },
                        singleLine = true,
                        visualTransformation = if (showLlmKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showLlmKey = !showLlmKey }) {
                                Icon(
                                    imageVector = if (showLlmKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            if (isTestingLlm) return@OutlinedButton
                            isTestingLlm = true
                            scope.launch {
                                val res = BreezeAiDirector.instance.testConnection()
                                isTestingLlm = false
                                res.onSuccess { context.toastOnUi(it) }
                                    .onFailure { context.toastOnUi(it.localizedMessage ?: "连接失败") }
                            }
                        },
                        enabled = !isTestingLlm,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, colors.line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isTestingLlm) "测试中..." else "测试 AI 导演连通性（$llmModel）")
                    }

                    Text(
                        text = if (showAdvancedUrls) "收起高级接口地址" else "高级：自定义接口地址与模型名",
                        color = colors.inkSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showAdvancedUrls = !showAdvancedUrls }
                            .padding(vertical = 4.dp),
                    )
                    if (showAdvancedUrls) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = {
                                baseUrl = it
                                BreezeTtsConfig.baseUrl = it
                            },
                            label = { Text("Breeze Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = llmBaseUrl,
                            onValueChange = {
                                llmBaseUrl = it
                                BreezeTtsConfig.llmBaseUrl = it
                            },
                            label = { Text("LLM Base URL (OpenAI 兼容)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = llmModel,
                            onValueChange = {
                                llmModel = it
                                BreezeTtsConfig.llmModel = it
                            },
                            label = { Text("模型名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            InkCardGroup {
                InkListRow(
                    title = "启用 Breeze TTS 2 引擎",
                    subtitle = if (breezeEnabled) "已设为全局默认朗读引擎" else "关闭则切回系统朗读",
                    trailing = {
                        InkSwitch(
                            checked = breezeEnabled,
                            onCheckedChange = { enable ->
                                breezeEnabled = enable
                                ReadBook.book?.setTtsEngine(null)
                                AppConfig.ttsEngine = if (enable) BreezeTtsConfig.ENGINE_ID else ""
                                ReadAloud.upReadAloudClass()
                            },
                        )
                    },
                )
                InkListRow(
                    title = "积分用尽自动切回系统朗读",
                    subtitle = "遇到 402 积分不足时不断播",
                    trailing = {
                        InkSwitch(
                            checked = autoFallback,
                            onCheckedChange = {
                                autoFallback = it
                                BreezeTtsConfig.autoFallbackOnCreditExhausted = it
                            },
                        )
                    },
                )
                val mb = String.format(Locale.getDefault(), "%.1f MB", cacheStats.second / (1024f * 1024f))
                InkListRow(
                    title = "持久化音频缓存",
                    subtitle = "已缓存 ${cacheStats.first} 段音频（$mb），回听与调倍速 0 积分",
                    leading = {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = colors.inkSecondary)
                    },
                    showDivider = false,
                    trailing = {
                        Text("清理", color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    },
                    onClick = {
                        BreezeAudioCache.instance.clearAll()
                        cacheRefreshTick++
                        context.toastOnUi("已清理 Breeze 音频缓存")
                    },
                )
            }
        }
    }

    val activeSlot = pickingSlot
    if (activeSlot != null) {
        BreezeVoicePickerDialog(
            title = "选择${activeSlot.title}",
            voices = voices,
            selectedVoiceId = when (activeSlot) {
                VoiceSlotTarget.NARRATOR -> BreezeTtsConfig.narratorVoiceId
                VoiceSlotTarget.MALE -> BreezeTtsConfig.maleVoiceId
                VoiceSlotTarget.FEMALE -> BreezeTtsConfig.femaleVoiceId
                VoiceSlotTarget.ELDER -> BreezeTtsConfig.elderVoiceId
            },
            onPreview = { voice ->
                scope.launch {
                    runCatching {
                        previewPlayer.playPreviewUrlOrSample(voice, "你好，这是${voice.name}的试听片段。")
                    }.onFailure { context.toastOnUi(it.localizedMessage ?: "试听失败") }
                }
            },
            onSelect = { voice ->
                when (activeSlot) {
                    VoiceSlotTarget.NARRATOR -> BreezeTtsConfig.narratorVoiceId = voice.voiceId
                    VoiceSlotTarget.MALE -> BreezeTtsConfig.maleVoiceId = voice.voiceId
                    VoiceSlotTarget.FEMALE -> BreezeTtsConfig.femaleVoiceId = voice.voiceId
                    VoiceSlotTarget.ELDER -> BreezeTtsConfig.elderVoiceId = voice.voiceId
                }
                pickingSlot = null
            },
            onDismiss = { pickingSlot = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkBookCastSheet(
    bookUrl: String,
    bookTitle: String,
    onDismiss: () -> Unit,
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewPlayer = remember(context) { VoicePreviewPlayer(context) }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    val cast = remember(bookUrl, refreshTick) { BreezeCastStore.instance.getBookCast(bookUrl) }
    val voices = remember(refreshTick) { BreezeCastStore.instance.getVoiceCatalog() }
    var editingEntry by remember { mutableStateOf<CharacterCastEntry?>(null) }
    var designingEntry by remember { mutableStateOf<CharacterCastEntry?>(null) }
    var mergingEntry by remember { mutableStateOf<CharacterCastEntry?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本书角色配音表", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$bookTitle · ${cast.size} 个已绑定角色",
                        color = colors.inkSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (cast.isNotEmpty() && voices.isNotEmpty()) {
                    Text(
                        text = "重配中文音色",
                        color = colors.ink,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                BreezeCastStore.instance.resetUnlockedVoices(
                                    bookUrl = bookUrl,
                                    narratorId = BreezeTtsConfig.narratorVoiceId,
                                    defaultMaleId = BreezeTtsConfig.maleVoiceId,
                                    defaultFemaleId = BreezeTtsConfig.femaleVoiceId,
                                    defaultElderId = BreezeTtsConfig.elderVoiceId,
                                )
                                refreshTick++
                                context.toastOnUi("已为未锁定角色重新分配中文音色")
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (cast.isEmpty()) {
                InkEmptyState(
                    title = "暂未发现具名角色",
                    subtitle = "开启多角色模式朗读几段后，识别到的角色与专属声优会自动固定在这里。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(cast, key = { it.name }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bg,
                            border = BorderStroke(0.5.dp, colors.line),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            Icons.Outlined.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = colors.inkSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = entry.name,
                                            color = colors.ink,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        val trait = buildString {
                                            append(
                                                when (entry.gender) {
                                                    "F" -> "女"
                                                    "M" -> "男"
                                                    else -> "通用"
                                                },
                                            )
                                            if (entry.ageGroup == "elder") append("·长者")
                                        }
                                        Text(
                                            text = "$trait · ${entry.lineCount}句",
                                            color = colors.inkSecondary,
                                            fontSize = 11.5.sp,
                                        )
                                    }
                                    Text(
                                        text = entry.voiceName.ifBlank { "默认" },
                                        color = colors.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    InkFilterChip(
                                        text = "试听",
                                        selected = false,
                                        onClick = {
                                            val profile = voices.firstOrNull { it.voiceId == entry.voiceId }
                                            scope.launch {
                                                runCatching {
                                                    if (profile != null) {
                                                        previewPlayer.playPreviewUrlOrSample(
                                                            profile,
                                                            "我是${entry.name}，阁下有何指教？",
                                                        )
                                                    } else {
                                                        previewPlayer.playSample(
                                                            entry.voiceId,
                                                            "我是${entry.name}，阁下有何指教？",
                                                        )
                                                    }
                                                }.onFailure { context.toastOnUi(it.localizedMessage ?: "试听失败") }
                                            }
                                        },
                                    )
                                    InkFilterChip(
                                        text = "换音色",
                                        selected = false,
                                        onClick = {
                                            if (voices.isEmpty()) {
                                                context.toastOnUi("请先在 Breeze 设置同步音色库")
                                            } else {
                                                editingEntry = entry
                                            }
                                        },
                                    )
                                    InkFilterChip(
                                        text = "AI 捏专属音",
                                        selected = false,
                                        onClick = { designingEntry = entry },
                                    )
                                    if (cast.size >= 2) {
                                        InkFilterChip(
                                            text = "合并别名",
                                            selected = false,
                                            onClick = { mergingEntry = entry },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val targetEdit = editingEntry
    if (targetEdit != null) {
        BreezeVoicePickerDialog(
            title = "为「${targetEdit.name}」更换音色",
            voices = voices,
            selectedVoiceId = targetEdit.voiceId,
            onPreview = { voice ->
                scope.launch {
                    runCatching {
                        previewPlayer.playPreviewUrlOrSample(voice, "我是${targetEdit.name}。")
                    }.onFailure { context.toastOnUi(it.localizedMessage ?: "试听失败") }
                }
            },
            onSelect = { voice ->
                BreezeCastStore.instance.assignCharacterVoice(bookUrl, targetEdit.name, voice)
                editingEntry = null
                refreshTick++
            },
            onDismiss = { editingEntry = null },
        )
    }

    val targetDesign = designingEntry
    if (targetDesign != null) {
        var persona by remember(targetDesign) {
            mutableStateOf(
                targetDesign.persona.ifBlank {
                    val genderLabel = if (targetDesign.gender == "F") "女性" else "男性"
                    "一位性格鲜明的${genderLabel}角色，咬字清晰，富有画面感。"
                },
            )
        }
        var designing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!designing) designingEntry = null },
            title = { Text("为「${targetDesign.name}」AI 捏音色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "通过 Breeze Voice Design 用文字直接生成并保存专属声线：",
                        fontSize = 12.5.sp,
                        color = colors.inkSecondary,
                    )
                    OutlinedTextField(
                        value = persona,
                        onValueChange = { persona = it },
                        label = { Text("音色描述 (中/英文)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !designing && persona.isNotBlank(),
                    onClick = {
                        designing = true
                        scope.launch {
                            val res = BreezeApiClient.designAndSaveCharacterVoice(
                                voiceName = targetDesign.name,
                                personaDescription = persona,
                                sampleText = "你好，我是${targetDesign.name}。",
                            )
                            designing = false
                            res.onSuccess { newVoice ->
                                BreezeCastStore.instance.assignCharacterVoice(bookUrl, targetDesign.name, newVoice)
                                designingEntry = null
                                refreshTick++
                                context.toastOnUi("已生成并绑定专属音色：${newVoice.name}")
                            }.onFailure { err ->
                                context.toastOnUi(err.localizedMessage ?: "捏音色失败")
                            }
                        }
                    },
                ) {
                    Text(if (designing) "生成中..." else "生成并绑定")
                }
            },
            dismissButton = {
                TextButton(enabled = !designing, onClick = { designingEntry = null }) {
                    Text("取消")
                }
            },
        )
    }

    val targetMerge = mergingEntry
    if (targetMerge != null) {
        val candidates = cast.filter { it.name != targetMerge.name }
        AlertDialog(
            onDismissRequest = { mergingEntry = null },
            title = { Text("将「${targetMerge.name}」合并为谁的别名？") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(candidates) { primary ->
                        InkListRow(
                            title = primary.name,
                            subtitle = "当前音色：${primary.voiceName}",
                            onClick = {
                                BreezeCastStore.instance.mergeAlias(bookUrl, primary.name, targetMerge.name)
                                mergingEntry = null
                                refreshTick++
                                context.toastOnUi("已将「${targetMerge.name}」合并到「${primary.name}」")
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mergingEntry = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun BreezeVoicePickerDialog(
    title: String,
    voices: List<BreezeVoiceProfile>,
    selectedVoiceId: String,
    onPreview: (BreezeVoiceProfile) -> Unit,
    onSelect: (BreezeVoiceProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = InkTheme.colors
    var filterGender by remember { mutableStateOf("ALL") }
    val filtered = remember(voices, filterGender) {
        when (filterGender) {
            "M" -> voices.filter { it.gender == "M" }
            "F" -> voices.filter { it.gender == "F" }
            "PERSONAL" -> voices.filter { it.voiceType != "default" }
            else -> voices
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("ALL" to "全部", "M" to "男声", "F" to "女声", "PERSONAL" to "个人/捏音").forEach { (k, label) ->
                        InkFilterChip(
                            text = label,
                            selected = filterGender == k,
                            onClick = { filterGender = k },
                        )
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.voiceId }) { voice ->
                        val selected = voice.voiceId == selectedVoiceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.chip else colors.surface)
                                .clickable { onSelect(voice) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.name,
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                val sub = listOf(voice.archetype.label, voice.description)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        color = colors.inkSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { onPreview(voice) }) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "试听", tint = colors.inkSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
