package io.legado.app.inkpaper.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.getValue
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
import io.legado.app.help.config.AppConfig
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkReadAloudSheet(
    onDismiss: () -> Unit,
    onOpenEngineDialog: () -> Unit,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(BaseReadAloudService.isPlay()) }
    var followSys by remember { mutableStateOf(AppConfig.ttsFlowSys) }
    var speechRate by remember { mutableIntStateOf(AppConfig.ttsSpeechRate.coerceIn(1, 25)) }
    var timerMinutes by remember { mutableIntStateOf(BaseReadAloudService.timeMinute) }
    val engineName = remember { ReadAloud.getEngineName(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "听书",
                        color = colors.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val chapterTitle = ReadBook.curTextChapter?.title.orEmpty()
                    if (chapterTitle.isNotBlank()) {
                        Text(
                            text = chapterTitle,
                            color = colors.inkSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = "退出听书",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            }

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
                    text = if (followSys) "系统" else "${speechRate}",
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("定时停止", color = colors.inkSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "关闭", 15 to "15 分", 30 to "30 分", 60 to "60 分").forEach { (mins, label) ->
                        InkFilterChip(
                            text = label,
                            selected = timerMinutes == mins,
                            onClick = {
                                timerMinutes = mins
                                ReadAloud.setTimer(context, mins)
                            },
                        )
                    }
                }
            }

            InkCardGroup {
                InkListRow(
                    title = "朗读引擎",
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
}
