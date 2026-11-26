package io.legado.app.inkpaper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.design.InkThemeController
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import kotlin.math.roundToInt

private val keepLightOptions = listOf(
    "0" to "跟随系统",
    "60" to "1 分钟",
    "300" to "5 分钟",
    "600" to "10 分钟",
    "-1" to "常亮",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkReadHabitsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current
    val uiPrefs by InkThemeController.prefs.collectAsStateWithLifecycle()

    var defaultToRead by remember { mutableStateOf(context.getPrefBoolean(PreferKey.defaultToRead)) }
    var autoRefresh by remember { mutableStateOf(context.getPrefBoolean(PreferKey.autoRefresh)) }
    var volumeKeyPage by remember { mutableStateOf(context.getPrefBoolean(PreferKey.volumeKeyPage, true)) }
    var disableReturnKey by remember { mutableStateOf(context.getPrefBoolean("disableReturnKey")) }
    var showBrightnessView by remember { mutableStateOf(context.getPrefBoolean(PreferKey.showBrightnessView, true)) }
    var replaceEnableDefault by remember { mutableStateOf(context.getPrefBoolean(PreferKey.replaceEnableDefault, true)) }
    var precisionSearch by remember { mutableStateOf(context.getPrefBoolean(PreferKey.precisionSearch)) }
    var keepLight by remember { mutableStateOf(context.getPrefString(PreferKey.keepLight) ?: "0") }
    var preDownloadNum by remember { mutableIntStateOf(AppConfig.preDownloadNum.coerceIn(0, 20)) }
    var threadCount by remember { mutableIntStateOf(AppConfig.threadCount.coerceIn(4, 32)) }

    var showKeepLightSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(title = "阅读与通用习惯", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("启动与书架")
            InkCardGroup {
                InkListRow(
                    title = "启动时进入上次阅读",
                    trailing = {
                        InkSwitch(
                            checked = defaultToRead,
                            onCheckedChange = {
                                defaultToRead = it
                                context.putPrefBoolean(PreferKey.defaultToRead, it)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "打开书架自动刷新",
                    trailing = {
                        InkSwitch(
                            checked = autoRefresh,
                            onCheckedChange = {
                                autoRefresh = it
                                context.putPrefBoolean(PreferKey.autoRefresh, it)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "书架显示「继续阅读」卡片",
                    showDivider = false,
                    trailing = {
                        InkSwitch(
                            checked = uiPrefs.showContinueCard,
                            onCheckedChange = InkThemeController::setShowContinueCard,
                        )
                    },
                )
            }

            SectionTitle("阅读交互")
            InkCardGroup {
                InkListRow(
                    title = "音量键翻页",
                    trailing = {
                        InkSwitch(
                            checked = volumeKeyPage,
                            onCheckedChange = {
                                volumeKeyPage = it
                                context.putPrefBoolean(PreferKey.volumeKeyPage, it)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "防误触返回键",
                    subtitle = "菜单隐藏时禁用系统返回退出",
                    trailing = {
                        InkSwitch(
                            checked = disableReturnKey,
                            onCheckedChange = {
                                disableReturnKey = it
                                context.putPrefBoolean("disableReturnKey", it)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "阅读菜单显示亮度条",
                    trailing = {
                        InkSwitch(
                            checked = showBrightnessView,
                            onCheckedChange = {
                                showBrightnessView = it
                                context.putPrefBoolean(PreferKey.showBrightnessView, it)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "新书默认开启净化替换",
                    trailing = {
                        InkSwitch(
                            checked = replaceEnableDefault,
                            onCheckedChange = {
                                replaceEnableDefault = it
                                context.putPrefBoolean(PreferKey.replaceEnableDefault, it)
                            },
                        )
                    },
                )
                val keepLabel = keepLightOptions.firstOrNull { it.first == keepLight }?.second ?: "跟随系统"
                InkListRow(
                    title = "屏幕常亮时长",
                    subtitle = keepLabel,
                    showDivider = false,
                    trailing = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                    },
                    onClick = { showKeepLightSheet = true },
                )
            }

            SectionTitle("搜索与预缓存")
            InkCardGroup {
                InkListRow(
                    title = "精准搜索",
                    subtitle = "仅保留书名或作者包含关键词的结果",
                    trailing = {
                        InkSwitch(
                            checked = precisionSearch,
                            onCheckedChange = {
                                precisionSearch = it
                                context.putPrefBoolean(PreferKey.precisionSearch, it)
                            },
                        )
                    },
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("预下载章节数", color = colors.ink, fontSize = 14.5.sp)
                        Text("${preDownloadNum} 章", color = colors.inkSecondary, fontFamily = serifFont, fontSize = 13.sp)
                    }
                    Slider(
                        value = preDownloadNum.toFloat(),
                        onValueChange = { preDownloadNum = it.roundToInt() },
                        onValueChangeFinished = { AppConfig.preDownloadNum = preDownloadNum },
                        valueRange = 0f..20f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.ink,
                            activeTrackColor = colors.ink,
                            inactiveTrackColor = colors.line,
                        ),
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = colors.line, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("并发搜索线程数", color = colors.ink, fontSize = 14.5.sp)
                        Text(threadCount.toString(), color = colors.inkSecondary, fontFamily = serifFont, fontSize = 13.sp)
                    }
                    Slider(
                        value = threadCount.toFloat(),
                        onValueChange = { threadCount = it.roundToInt() },
                        onValueChangeFinished = { AppConfig.threadCount = threadCount },
                        valueRange = 4f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.ink,
                            activeTrackColor = colors.ink,
                            inactiveTrackColor = colors.line,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showKeepLightSheet) {
        ModalBottomSheet(
            onDismissRequest = { showKeepLightSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "屏幕常亮时长",
                    color = colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                keepLightOptions.forEachIndexed { idx, (value, label) ->
                    InkListRow(
                        title = label,
                        trailing = {
                            if (keepLight == value) {
                                Text("当前", color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        showDivider = idx < keepLightOptions.lastIndex,
                        onClick = {
                            keepLight = value
                            context.putPrefString(PreferKey.keepLight, value)
                            showKeepLightSheet = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = InkTheme.colors.inkSecondary,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp),
    )
}
