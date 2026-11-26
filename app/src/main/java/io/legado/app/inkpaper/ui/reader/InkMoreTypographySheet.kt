package io.legado.app.inkpaper.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSegmentedControl
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.model.ReadBook
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkMoreTypographySheet(
    book: Book?,
    onDismiss: () -> Unit,
    onTypographyChanged: () -> Unit,
    onParagraphModeChanged: (Boolean) -> Unit,
    onToggleAutoPage: () -> Unit,
    onOpenLegacyMore: () -> Unit,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var marginH by remember { mutableIntStateOf(ReadBookConfig.paddingLeft.coerceIn(8, 40)) }
    var marginV by remember { mutableIntStateOf(ReadBookConfig.paddingTop.coerceIn(0, 32)) }
    var letterSpacing by remember { mutableFloatStateOf(ReadBookConfig.letterSpacing.coerceIn(-0.05f, 0.30f)) }
    var indentIndex by remember {
        mutableIntStateOf(
            when (ReadBookConfig.paragraphIndent.length) {
                0 -> 0
                1 -> 1
                else -> 2
            },
        )
    }
    var boldIndex by remember {
        mutableIntStateOf(
            when (ReadBookConfig.textBold) {
                2 -> 0
                1 -> 2
                else -> 1
            },
        )
    }
    var titleModeIndex by remember {
        mutableIntStateOf(
            when (ReadBookConfig.titleMode) {
                1 -> 1
                2 -> 2
                else -> 0
            },
        )
    }
    var paragraphMode by remember { mutableStateOf(AppConfig.paragraphReadMode) }
    var useReplaceRule by remember { mutableStateOf(book?.getUseReplaceRule() ?: AppConfig.replaceEnableDefault) }
    var hideStatusBar by remember { mutableStateOf(ReadBookConfig.hideStatusBar) }
    var autoReadSpeed by remember { mutableIntStateOf(ReadBookConfig.autoReadSpeed.coerceIn(1, 60)) }

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "更多排版设置",
                color = colors.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )

            // 左右边距
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("左右边距", color = colors.inkSecondary, fontSize = 13.sp)
                Slider(
                    value = marginH.toFloat(),
                    onValueChange = { marginH = it.roundToInt() },
                    onValueChangeFinished = {
                        ReadBookConfig.paddingLeft = marginH
                        ReadBookConfig.paddingRight = marginH
                        onTypographyChanged()
                    },
                    valueRange = 8f..40f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.ink,
                        activeTrackColor = colors.ink,
                        inactiveTrackColor = colors.line,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(marginH.toString(), color = colors.ink, fontFamily = serifFont, fontSize = 13.sp)
            }

            // 上下边距
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("上下边距", color = colors.inkSecondary, fontSize = 13.sp)
                Slider(
                    value = marginV.toFloat(),
                    onValueChange = { marginV = it.roundToInt() },
                    onValueChangeFinished = {
                        ReadBookConfig.paddingTop = marginV
                        ReadBookConfig.paddingBottom = marginV
                        onTypographyChanged()
                    },
                    valueRange = 0f..32f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.ink,
                        activeTrackColor = colors.ink,
                        inactiveTrackColor = colors.line,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(marginV.toString(), color = colors.ink, fontFamily = serifFont, fontSize = 13.sp)
            }

            // 字距
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("文字间距", color = colors.inkSecondary, fontSize = 13.sp)
                Slider(
                    value = letterSpacing,
                    onValueChange = { letterSpacing = it },
                    onValueChangeFinished = {
                        ReadBookConfig.letterSpacing = (letterSpacing * 100f).roundToInt() / 100f
                        onTypographyChanged()
                    },
                    valueRange = -0.05f..0.30f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.ink,
                        activeTrackColor = colors.ink,
                        inactiveTrackColor = colors.line,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    String.format("%.2f", letterSpacing),
                    color = colors.ink,
                    fontFamily = serifFont,
                    fontSize = 12.5.sp,
                )
            }

            // 缩进
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("首行缩进", color = colors.inkSecondary, fontSize = 13.sp)
                InkSegmentedControl(
                    options = listOf("无", "一格", "两格"),
                    selectedIndex = indentIndex,
                    onSelect = { idx ->
                        indentIndex = idx
                        ReadBookConfig.paragraphIndent = when (idx) {
                            0 -> ""
                            1 -> "　"
                            else -> "　　"
                        }
                        onTypographyChanged()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // 字重
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("正文字重", color = colors.inkSecondary, fontSize = 13.sp)
                InkSegmentedControl(
                    options = listOf("细", "正常", "粗"),
                    selectedIndex = boldIndex,
                    onSelect = { idx ->
                        boldIndex = idx
                        ReadBookConfig.textBold = when (idx) {
                            0 -> 2
                            2 -> 1
                            else -> 0
                        }
                        onTypographyChanged()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // 标题位置
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("章节标题", color = colors.inkSecondary, fontSize = 13.sp)
                InkSegmentedControl(
                    options = listOf("居左", "居中", "隐藏"),
                    selectedIndex = titleModeIndex,
                    onSelect = { idx ->
                        titleModeIndex = idx
                        ReadBookConfig.titleMode = idx
                        onTypographyChanged()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            InkCardGroup {
                InkListRow(
                    title = "段落阅读模式",
                    trailing = {
                        InkSwitch(
                            checked = paragraphMode,
                            onCheckedChange = { enabled ->
                                paragraphMode = enabled
                                onParagraphModeChanged(enabled)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "启用净化替换",
                    trailing = {
                        InkSwitch(
                            checked = useReplaceRule,
                            onCheckedChange = { enabled ->
                                useReplaceRule = enabled
                                book?.let { target ->
                                    target.setUseReplaceRule(enabled)
                                    scope.launch(Dispatchers.IO) {
                                        appDb.bookDao.update(target)
                                    }
                                    ReadBook.loadContent(resetPageOffset = true)
                                }
                            },
                        )
                    },
                )
                InkListRow(
                    title = "隐藏状态栏",
                    showDivider = false,
                    trailing = {
                        InkSwitch(
                            checked = hideStatusBar,
                            onCheckedChange = { enabled ->
                                hideStatusBar = enabled
                                ReadBookConfig.hideStatusBar = enabled
                                context.putPrefBoolean(PreferKey.hideStatusBar, enabled)
                                onTypographyChanged()
                            },
                        )
                    },
                )
            }

            // 自动翻页速度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("自动翻页", color = colors.inkSecondary, fontSize = 13.sp)
                Slider(
                    value = autoReadSpeed.toFloat(),
                    onValueChange = { autoReadSpeed = it.roundToInt() },
                    onValueChangeFinished = {
                        ReadBookConfig.autoReadSpeed = autoReadSpeed
                        ReadBookConfig.save()
                    },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.ink,
                        activeTrackColor = colors.ink,
                        inactiveTrackColor = colors.line,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text("${autoReadSpeed}s", color = colors.ink, fontFamily = serifFont, fontSize = 12.5.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onToggleAutoPage()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text("开启自动翻页", fontSize = 13.5.sp)
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenLegacyMore()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkSecondary),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text("全部经典参数", fontSize = 13.5.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
