package io.legado.app.inkpaper.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.constant.PageAnim
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.font.BuiltinFonts
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSegmentedControl
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import kotlin.math.roundToInt

data class InkReaderOverlayState(
    val visible: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val isNight: Boolean = false,
    val showQuickSettings: Boolean = false,
    val showMoreActions: Boolean = false,
    val brightnessAuto: Boolean = true,
    val brightness: Float = 0.5f,
)

interface InkReaderActions {
    fun onDismiss()
    fun onBack()
    fun onToggleBookmark()
    fun onPrevChapter()
    fun onNextChapter()
    fun onSeekChapter(index: Int)
    fun onOpenToc()
    fun onOpenAloud()
    fun onToggleNight()
    fun onOpenQuickSettings()
    fun onCloseQuickSettings()
    fun onOpenMoreActions()
    fun onCloseMoreActions()
    fun onOpenChangeSource()
    fun onOpenSearchContent()
    fun onRefreshChapter()
    fun onToggleAutoPage()
    fun onOpenBookDetail()
    fun onOpenMoreTypography()
    fun onTypographyChanged()
    fun onBackgroundChanged(bgHex: String, textHex: String, darkStatusIcon: Boolean)
    fun onPageAnimChanged(anim: Int)
    fun onBrightnessChanged(auto: Boolean, value: Float)
}

private data class PaperBgPreset(
    val label: String,
    val bgHex: String,
    val textHex: String,
    val darkStatusIcon: Boolean,
)

private val paperBgPresets = listOf(
    PaperBgPreset("素纸", "#F7F6F3", "#1B1B18", true),
    PaperBgPreset("暖纸", "#F2EBD9", "#2C261E", true),
    PaperBgPreset("浅灰", "#E9E9E6", "#242422", true),
    PaperBgPreset("墨夜", "#141413", "#C9C7C0", false),
    PaperBgPreset("纯黑", "#000000", "#BDBBB4", false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkReaderOverlay(
    state: InkReaderOverlayState,
    actions: InkReaderActions,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = actions::onDismiss,
                ),
        ) {
            AnimatedVisibility(
                visible = state.visible,
                enter = slideInVertically { -it / 2 } + fadeIn(),
                exit = slideOutVertically { -it / 2 } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(0.5.dp, colors.line),
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = actions::onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                tint = colors.ink,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = state.bookTitle.ifBlank { "阅读" },
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.chapterTitle.isNotBlank()) {
                                Text(
                                    text = state.chapterTitle,
                                    color = colors.inkSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = actions::onToggleBookmark) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkBorder,
                                contentDescription = "书签",
                                tint = colors.ink,
                            )
                        }
                        IconButton(onClick = actions::onOpenMoreActions) {
                            Icon(
                                imageVector = Icons.Outlined.MoreHoriz,
                                contentDescription = "更多",
                                tint = colors.ink,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.visible,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(0.5.dp, colors.line),
                    shadowElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        val maxIndex = (state.totalChapters - 1).coerceAtLeast(1)
                        var sliderPos by remember(state.chapterIndex, state.totalChapters) {
                            mutableFloatStateOf(state.chapterIndex.coerceIn(0, maxIndex).toFloat())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "上一章",
                                color = colors.inkSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = actions::onPrevChapter)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                            Slider(
                                value = sliderPos,
                                onValueChange = { sliderPos = it },
                                onValueChangeFinished = {
                                    actions.onSeekChapter(sliderPos.roundToInt())
                                },
                                valueRange = 0f..maxIndex.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.ink,
                                    activeTrackColor = colors.ink,
                                    inactiveTrackColor = colors.line,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "下一章",
                                color = colors.inkSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = actions::onNextChapter)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = colors.line,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            DockToolButton(
                                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                label = "目录",
                                onClick = actions::onOpenToc,
                                modifier = Modifier.weight(1f),
                            )
                            DockToolButton(
                                icon = Icons.Outlined.Headphones,
                                label = "听书",
                                onClick = actions::onOpenAloud,
                                modifier = Modifier.weight(1f),
                            )
                            DockToolButton(
                                icon = if (state.isNight) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                label = if (state.isNight) "日间" else "夜间",
                                onClick = actions::onToggleNight,
                                modifier = Modifier.weight(1f),
                            )
                            DockToolButton(
                                icon = Icons.Outlined.Tune,
                                label = "设置",
                                onClick = actions::onOpenQuickSettings,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showQuickSettings) {
        ModalBottomSheet(
            onDismissRequest = actions::onCloseQuickSettings,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            var textSize by remember(state.showQuickSettings) {
                mutableIntStateOf(ReadBookConfig.textSize.coerceIn(12, 38))
            }
            var selectedFont by remember(state.showQuickSettings) {
                mutableIntStateOf(
                    when {
                        BuiltinFonts.isBuiltin(ReadBookConfig.textFont) -> 2
                        AppConfig.systemTypefaces == 1 -> 1
                        else -> 0
                    },
                )
            }
            var selectedBgHex by remember(state.showQuickSettings) {
                mutableStateOf(ReadBookConfig.durConfig.curBgStr())
            }
            var selectedPageAnim by remember(state.showQuickSettings) {
                mutableIntStateOf(
                    when (ReadBookConfig.pageAnim) {
                        PageAnim.coverPageAnim -> 1
                        PageAnim.slidePageAnim -> 2
                        PageAnim.scrollPageAnim -> 3
                        else -> 0
                    },
                )
            }
            var spacingIndex by remember(state.showQuickSettings) {
                mutableIntStateOf(
                    when {
                        ReadBookConfig.lineSpacingExtra <= 8 -> 0
                        ReadBookConfig.lineSpacingExtra >= 16 -> 2
                        else -> 1
                    },
                )
            }
            var brightnessAuto by remember(state.showQuickSettings) {
                mutableStateOf(state.brightnessAuto)
            }
            var brightnessVal by remember(state.showQuickSettings) {
                mutableFloatStateOf(state.brightness)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 字号
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("字号", color = colors.inkSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = "A-",
                        color = colors.ink,
                        fontFamily = serifFont,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (textSize > 12) {
                                    textSize -= 1
                                    ReadBookConfig.textSize = textSize
                                    actions.onTypographyChanged()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Slider(
                        value = textSize.toFloat(),
                        onValueChange = { textSize = it.roundToInt() },
                        onValueChangeFinished = {
                            ReadBookConfig.textSize = textSize
                            actions.onTypographyChanged()
                        },
                        valueRange = 12f..38f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.ink,
                            activeTrackColor = colors.ink,
                            inactiveTrackColor = colors.line,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "A+",
                        color = colors.ink,
                        fontFamily = serifFont,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (textSize < 38) {
                                    textSize += 1
                                    ReadBookConfig.textSize = textSize
                                    actions.onTypographyChanged()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(
                        text = textSize.toString(),
                        color = colors.ink,
                        fontFamily = serifFont,
                        fontSize = 13.5.sp,
                    )
                }

                // 字体
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("字体", color = colors.inkSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                    val wenKaiPath = BuiltinFonts.items.firstOrNull()?.value.orEmpty()
                    InkFilterChip(
                        text = "系统默认",
                        selected = selectedFont == 0,
                        onClick = {
                            selectedFont = 0
                            AppConfig.systemTypefaces = 0
                            ReadBookConfig.textFont = ""
                            actions.onTypographyChanged()
                        },
                    )
                    InkFilterChip(
                        text = "系统衬线",
                        selected = selectedFont == 1,
                        onClick = {
                            selectedFont = 1
                            AppConfig.systemTypefaces = 1
                            ReadBookConfig.textFont = ""
                            actions.onTypographyChanged()
                        },
                    )
                    if (wenKaiPath.isNotEmpty()) {
                        InkFilterChip(
                            text = "霞鹜文楷",
                            selected = selectedFont == 2,
                            onClick = {
                                selectedFont = 2
                                ReadBookConfig.textFont = wenKaiPath
                                actions.onTypographyChanged()
                            },
                        )
                    }
                }

                // 纸色背景
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("背景", color = colors.inkSecondary, fontSize = 13.sp)
                    paperBgPresets.forEach { preset ->
                        val isSelected = selectedBgHex.equals(preset.bgHex, ignoreCase = true)
                        val swatchColor = remember(preset.bgHex) { Color(preset.bgHex.toColorInt()) }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, colors.ink, CircleShape)
                                    } else {
                                        Modifier.border(0.5.dp, colors.line, CircleShape)
                                    },
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .clickable {
                                    selectedBgHex = preset.bgHex
                                    actions.onBackgroundChanged(
                                        preset.bgHex,
                                        preset.textHex,
                                        preset.darkStatusIcon,
                                    )
                                },
                        )
                    }
                }

                // 翻页
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("翻页", color = colors.inkSecondary, fontSize = 13.sp)
                    InkSegmentedControl(
                        options = listOf("仿真", "覆盖", "滑动", "滚动"),
                        selectedIndex = selectedPageAnim,
                        onSelect = { index ->
                            selectedPageAnim = index
                            val anim = when (index) {
                                1 -> PageAnim.coverPageAnim
                                2 -> PageAnim.slidePageAnim
                                3 -> PageAnim.scrollPageAnim
                                else -> PageAnim.simulationPageAnim
                            }
                            actions.onPageAnimChanged(anim)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 排版间距
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("间距", color = colors.inkSecondary, fontSize = 13.sp)
                    InkSegmentedControl(
                        options = listOf("紧凑", "适中", "宽松"),
                        selectedIndex = spacingIndex,
                        onSelect = { index ->
                            spacingIndex = index
                            val (lineExtra, paraExtra) = when (index) {
                                0 -> 6 to 2
                                2 -> 18 to 12
                                else -> 12 to 6
                            }
                            ReadBookConfig.lineSpacingExtra = lineExtra
                            ReadBookConfig.paragraphSpacing = paraExtra
                            actions.onTypographyChanged()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 屏幕亮度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("亮度", color = colors.inkSecondary, fontSize = 13.sp)
                    Slider(
                        value = brightnessVal,
                        onValueChange = {
                            brightnessAuto = false
                            brightnessVal = it
                            actions.onBrightnessChanged(false, it)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colors.ink,
                            activeTrackColor = colors.ink,
                            inactiveTrackColor = colors.line,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text("自动", color = colors.inkSecondary, fontSize = 12.sp)
                    InkSwitch(
                        checked = brightnessAuto,
                        onCheckedChange = { auto ->
                            brightnessAuto = auto
                            actions.onBrightnessChanged(auto, brightnessVal)
                        },
                    )
                }

                OutlinedButton(
                    onClick = {
                        actions.onCloseQuickSettings()
                        actions.onOpenMoreTypography()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Text("更多排版设置", fontSize = 13.5.sp)
                }
            }
        }
    }

    if (state.showMoreActions) {
        ModalBottomSheet(
            onDismissRequest = actions::onCloseMoreActions,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                InkListRow(
                    title = "切换书源",
                    leading = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = {
                        actions.onCloseMoreActions()
                        actions.onOpenChangeSource()
                    },
                )
                InkListRow(
                    title = "全文搜索",
                    leading = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = {
                        actions.onCloseMoreActions()
                        actions.onOpenSearchContent()
                    },
                )
                InkListRow(
                    title = "刷新当前章节",
                    leading = { Icon(Icons.Outlined.Refresh, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = {
                        actions.onCloseMoreActions()
                        actions.onRefreshChapter()
                    },
                )
                InkListRow(
                    title = "自动翻页",
                    leading = { Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = {
                        actions.onCloseMoreActions()
                        actions.onToggleAutoPage()
                    },
                )
                InkListRow(
                    title = "书籍详情",
                    showDivider = false,
                    onClick = {
                        actions.onCloseMoreActions()
                        actions.onOpenBookDetail()
                    },
                )
            }
        }
    }
}

@Composable
private fun DockToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.ink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = colors.ink,
            fontSize = 11.sp,
        )
    }
}
