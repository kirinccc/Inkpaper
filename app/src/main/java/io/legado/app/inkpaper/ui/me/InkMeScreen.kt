package io.legado.app.inkpaper.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.appDb
import io.legado.app.help.book.isNotShelf
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkPageHeader
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.design.InkThemeController
import io.legado.app.inkpaper.design.InkThemePreset
import io.legado.app.inkpaper.design.toColors
import io.legado.app.inkpaper.tts.BreezeTtsConfig
import io.legado.app.inkpaper.ui.reader.InkBreezeConfigSheet
import io.legado.app.inkpaper.ui.settings.InkLocalImportSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun InkMeScreen(
    onOpenReadHabits: () -> Unit,
    onOpenBookSources: () -> Unit,
    onOpenReplaceRules: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReadRecords: () -> Unit,
    onOpenAllBookmarks: () -> Unit,
    onOpenAdvancedTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val uiPrefs by InkThemeController.prefs.collectAsStateWithLifecycle()

    val shelfBooks by appDb.bookDao.flowAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val enabledSources by appDb.bookSourceDao.flowEnabled().collectAsStateWithLifecycle(initialValue = emptyList())
    val totalReadHours by produceState(initialValue = "0") {
        val ms = withContext(Dispatchers.IO) {
            runCatching { appDb.readRecordDao.all.sumOf { it.readTime } }.getOrDefault(0L)
        }
        val hours = ms / 3_600_000.0
        value = if (hours >= 10) hours.toInt().toString() else String.format(Locale.getDefault(), "%.1f", hours)
    }

    var showLocalImportSheet by remember { mutableStateOf(false) }
    var showBreezeConfigSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkPageHeader(title = "我的")

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                InkCardGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatCell(
                            value = totalReadHours,
                            label = "阅读 / 小时",
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            thickness = 0.5.dp,
                            color = colors.line,
                            modifier = Modifier.height(32.dp),
                        )
                        StatCell(
                            value = shelfBooks.count { !it.isNotShelf }.toString(),
                            label = "书架藏书",
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            thickness = 0.5.dp,
                            color = colors.line,
                            modifier = Modifier.height(32.dp),
                        )
                        StatCell(
                            value = enabledSources.size.toString(),
                            label = "启用书源",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                InkCardGroup {
                    InkListRow(
                        title = "跟随系统深色",
                        trailing = {
                            InkSwitch(
                                checked = uiPrefs.followSystem,
                                onCheckedChange = InkThemeController::setFollowSystem,
                            )
                        },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "纸墨主题预设",
                            color = colors.ink,
                            fontSize = 14.5.sp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            InkThemePreset.entries.forEach { preset ->
                                val presetColors = preset.toColors()
                                val selected = !uiPrefs.followSystem && uiPrefs.selectedPreset == preset
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) colors.ink else colors.chip)
                                        .clickable { InkThemeController.selectPreset(preset) }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(presetColors.bg)
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) colors.inkOnSolid else colors.line,
                                                shape = CircleShape,
                                            ),
                                    )
                                    Text(
                                        text = preset.label,
                                        color = if (selected) colors.inkOnSolid else colors.inkSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                InkCardGroup {
                    InkListRow(
                        title = "阅读与通用习惯",
                        leading = {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        onClick = onOpenReadHabits,
                    )
                    InkListRow(
                        title = "Breeze AI 有声书",
                        subtitle = if (BreezeTtsConfig.hasApiKey()) {
                            "${BreezeTtsConfig.dramaMode.label} · 多角色演播"
                        } else {
                            "配置 Breeze API Key 与多角色声优"
                        },
                        leading = {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        showDivider = false,
                        onClick = { showBreezeConfigSheet = true },
                    )
                }
            }

            item {
                InkCardGroup {
                    InkListRow(
                        title = "书源管理",
                        leading = {
                            Icon(Icons.Outlined.Source, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        onClick = onOpenBookSources,
                    )
                    InkListRow(
                        title = "替换净化规则",
                        leading = {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        onClick = onOpenReplaceRules,
                    )
                    InkListRow(
                        title = "导入本地书籍",
                        leading = {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        showDivider = false,
                        onClick = { showLocalImportSheet = true },
                    )
                }
            }

            item {
                InkCardGroup {
                    InkListRow(
                        title = "备份与恢复",
                        subtitle = "WebDAV 云同步与本地导出",
                        leading = {
                            Icon(Icons.Outlined.Backup, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        onClick = onOpenBackup,
                    )
                    InkListRow(
                        title = "阅读记录",
                        leading = {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        onClick = onOpenReadRecords,
                    )
                    InkListRow(
                        title = "书签与笔记",
                        leading = {
                            Icon(Icons.Outlined.Bookmarks, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        onClick = onOpenAllBookmarks,
                    )
                    InkListRow(
                        title = "高级工具与关于",
                        leading = {
                            Icon(Icons.Outlined.Build, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                        },
                        showDivider = false,
                        onClick = onOpenAdvancedTools,
                    )
                }
            }
        }
    }

    if (showLocalImportSheet) {
        InkLocalImportSheet(onDismiss = { showLocalImportSheet = false })
    }
    if (showBreezeConfigSheet) {
        InkBreezeConfigSheet(onDismiss = { showBreezeConfigSheet = false })
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = colors.ink,
            fontFamily = serifFont,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = colors.inkSecondary,
            fontSize = 11.5.sp,
        )
    }
}
