package io.legado.app.inkpaper.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.help.config.AppConfig
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.tts.BreezeCastStore
import io.legado.app.inkpaper.tts.BreezePlaybackTracker
import io.legado.app.inkpaper.tts.BreezeTtsConfig
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkReadAloudSheet(
    onDismiss: () -> Unit,
    onOpenEngineDialog: () -> Unit,
    onOpenReaderMenu: (() -> Unit)? = null,
    onBackToSpeechPosition: (() -> Unit)? = null,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current

    val breezeStatus by BreezePlaybackTracker.status.collectAsStateWithLifecycle()
    val configVersion by BreezeTtsConfig.configVersion.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(BaseReadAloudService.isPlay()) }
    var followSys by remember { mutableStateOf(AppConfig.ttsFlowSys) }
    var speechRate by remember { mutableIntStateOf(AppConfig.ttsSpeechRate.coerceIn(1, 25)) }
    var breezeSpeed by remember(configVersion) { mutableFloatStateOf(BreezeTtsConfig.speedMultiplier) }
    var timerMinutes by remember { mutableIntStateOf(BaseReadAloudService.timeMinute) }
    var chapterStop by remember { mutableIntStateOf(BaseReadAloudService.chapterToStop) }
    var followingPosition by remember { mutableStateOf(ReadAloud.followReadAloudPosition) }
    var chapterTitle by remember { mutableStateOf(ReadBook.curTextChapter?.title.orEmpty()) }

    val isBreezeEngine = remember(configVersion) {
        ReadAloud.ttsEngine == BreezeTtsConfig.ENGINE_ID
    }
    val engineName = remember(configVersion) { ReadAloud.getEngineName(context) }

    var showBreezeStudio by remember { mutableStateOf(false) }
    var showBookCast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = BaseReadAloudService.isPlay()
            timerMinutes = BaseReadAloudService.timeMinute
            chapterStop = BaseReadAloudService.chapterToStop
            followingPosition = ReadAloud.followReadAloudPosition
            chapterTitle = ReadBook.curTextChapter?.title.orEmpty()
            delay(400L)
        }
    }

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
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBreezeEngine) "AI 有声剧" else "听书",
                        color = colors.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (chapterTitle.isNotBlank()) {
                        Text(
                            text = chapterTitle,
                            color = colors.inkSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isBreezeEngine && (breezeStatus.speaker.isNotBlank() || breezeStatus.errorMessage != null)) {
                        val badgeText = breezeStatus.errorMessage ?: buildString {
                            append(breezeStatus.speaker)
                            if (breezeStatus.voiceName.isNotBlank()) {
                                append(" · ").append(breezeStatus.voiceName)
                            }
                            if (breezeStatus.emotion.isNotBlank()) {
                                append("（").append(breezeStatus.emotion).append("）")
                            }
                            if (breezeStatus.isBuffering) append(" · 缓冲中")
                        }
                        Text(
                            text = badgeText,
                            color = colors.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onOpenReaderMenu != null) {
                        Text(
                            text = "阅读菜单",
                            color = colors.ink,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onDismiss()
                                    onOpenReaderMenu()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Text(
                        text = "退出",
                        color = colors.inkSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                ReadAloud.stop(context)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { ReadAloud.prevChapter(context) }) {
                    Icon(
                        imageVector = Icons.Outlined.FastRewind,
                        contentDescription = "上一章",
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = {
                        ReadAloud.prevParagraph(context)
                        isPlaying = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "上一段",
                        tint = colors.ink,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.ink)
                        .clickable {
                            if (!BaseReadAloudService.isRun) {
                                ReadBook.readAloud()
                                isPlaying = true
                            } else if (isPlaying) {
                                ReadAloud.pause(context)
                                isPlaying = false
                            } else {
                                ReadAloud.resume(context)
                                isPlaying = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = colors.inkOnSolid,
                        modifier = Modifier.size(28.dp),
                    )
                }

                IconButton(
                    onClick = {
                        ReadAloud.nextParagraph(context)
                        isPlaying = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "下一段",
                        tint = colors.ink,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = { ReadAloud.nextChapter(context) }) {
                    Icon(
                        imageVector = Icons.Outlined.FastForward,
                        contentDescription = "下一章",
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (!followingPosition) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onBackToSpeechPosition != null) {
                        InkFilterChip(
                            text = "回到朗读位置",
                            selected = true,
                            onClick = {
                                onDismiss()
                                onBackToSpeechPosition()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    InkFilterChip(
                        text = "从当前页朗读",
                        selected = false,
                        onClick = {
                            ReadBook.readAloud()
                            isPlaying = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (isBreezeEngine) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("本地无损倍速", color = colors.inkSecondary, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.getDefault(), "%.2fx · 不耗积分", breezeSpeed),
                            color = colors.ink,
                            fontFamily = serifFont,
                            fontSize = 12.5.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                            val label = if (speed == 1.0f) "1.0x" else "${speed}x"
                            InkFilterChip(
                                text = label,
                                selected = kotlin.math.abs(breezeSpeed - speed) < 0.03f,
                                onClick = {
                                    breezeSpeed = speed
                                    BreezeTtsConfig.speedMultiplier = speed
                                    ReadAloud.upTtsSpeechRate(context)
                                },
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("语速", color = colors.inkSecondary, fontSize = 13.sp)
                    Slider(
                        value = speechRate.toFloat(),
                        enabled = !followSys,
                        onValueChange = { speechRate = it.roundToInt() },
                        onValueChangeFinished = {
                            AppConfig.ttsSpeechRate = speechRate
                            ReadAloud.upTtsSpeechRate(context)
                        },
                        valueRange = 1f..25f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.ink,
                            activeTrackColor = colors.ink,
                            inactiveTrackColor = colors.line,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (followSys) "系统" else "$speechRate",
                        color = colors.ink,
                        fontFamily = serifFont,
                        fontSize = 12.5.sp,
                    )
                    InkSwitch(
                        checked = followSys,
                        onCheckedChange = { enabled ->
                            followSys = enabled
                            AppConfig.ttsFlowSys = enabled
                            ReadAloud.upTtsSpeechRate(context)
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val timerHeader = when {
                    chapterStop > 0 -> "定时停止 · 剩余 $chapterStop 章"
                    timerMinutes > 0 -> "定时停止 · 剩余 $timerMinutes 分"
                    else -> "定时停止"
                }
                Text(timerHeader, color = colors.inkSecondary, fontSize = 13.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "关闭", 15 to "15 分", 30 to "30 分", 60 to "60 分").forEach { (mins, label) ->
                        InkFilterChip(
                            text = label,
                            selected = chapterStop == 0 && timerMinutes == mins,
                            onClick = {
                                chapterStop = 0
                                timerMinutes = mins
                                ReadAloud.setTimer(context, mins)
                            },
                        )
                    }
                    listOf(1 to "读完本章", 2 to "2 章后", 5 to "5 章后").forEach { (count, label) ->
                        InkFilterChip(
                            text = label,
                            selected = chapterStop == count,
                            onClick = {
                                timerMinutes = 0
                                chapterStop = count
                                ReadAloud.setChapterStop(context, count)
                            },
                        )
                    }
                }
            }

            val currentBook = ReadBook.book
            InkCardGroup {
                val breezeSubtitle = if (BreezeTtsConfig.hasApiKey()) {
                    "${BreezeTtsConfig.dramaMode.label} · ${BreezeTtsConfig.stylePreset.label}"
                } else {
                    "配置 API Key 与多角色声优"
                }
                InkListRow(
                    title = "Breeze TTS 2 有声剧工作室",
                    subtitle = breezeSubtitle,
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = { showBreezeStudio = true },
                )

                if (isBreezeEngine && currentBook != null) {
                    val castCount = remember(currentBook.bookUrl, configVersion) {
                        BreezeCastStore.instance.getBookCast(currentBook.bookUrl).size
                    }
                    InkListRow(
                        title = "本书角色配音表",
                        subtitle = if (castCount > 0) "已绑定 $castCount 个角色 · 支持试听/换声优/捏音色" else "朗读后自动识别角色并固定声优",
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                            )
                        },
                        onClick = { showBookCast = true },
                    )
                }

                InkListRow(
                    title = "切换朗读引擎",
                    subtitle = engineName.ifBlank { "系统默认引擎" },
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    showDivider = false,
                    onClick = {
                        onDismiss()
                        onOpenEngineDialog()
                    },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showBreezeStudio) {
        InkBreezeConfigSheet(
            onDismiss = { showBreezeStudio = false },
            onOpenLegacyEngines = {
                onDismiss()
                onOpenEngineDialog()
            },
        )
    }

    val bookForCast = ReadBook.book
    if (showBookCast && bookForCast != null) {
        InkBookCastSheet(
            bookUrl = bookForCast.bookUrl,
            bookTitle = bookForCast.name,
            onDismiss = { showBookCast = false },
        )
    }
}
