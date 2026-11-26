package io.legado.app.inkpaper.ui.me

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.service.McpService
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.autoTask.AutoTaskActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.utils.putPrefBoolean

@Composable
fun InkAdvancedToolsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current

    var webServiceRunning by remember { mutableStateOf(WebService.isRun) }
    var mcpServiceRunning by remember { mutableStateOf(McpService.isRun) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(title = "高级工具与关于", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "服务与自动化",
                color = colors.inkSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
            InkCardGroup {
                InkListRow(
                    title = "Web 服务",
                    subtitle = if (webServiceRunning) WebService.hostAddress else "局域网浏览器阅读与写源",
                    trailing = {
                        InkSwitch(
                            checked = webServiceRunning,
                            onCheckedChange = { enable ->
                                webServiceRunning = enable
                                context.putPrefBoolean(PreferKey.webService, enable)
                                if (enable) WebService.start(context) else WebService.stop(context)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "MCP 服务",
                    subtitle = if (mcpServiceRunning) McpService.hostAddress else "对外提供 MCP 协议接口",
                    trailing = {
                        InkSwitch(
                            checked = mcpServiceRunning,
                            onCheckedChange = { enable ->
                                mcpServiceRunning = enable
                                context.putPrefBoolean(PreferKey.mcpService, enable)
                                if (enable) McpService.start(context) else McpService.stop(context)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "自动任务",
                    trailing = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary)
                    },
                    showDivider = false,
                    onClick = { context.startActivity(Intent(context, AutoTaskActivity::class.java)) },
                )
            }

            Text(
                text = "进阶规则",
                color = colors.inkSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
            InkCardGroup {
                InkListRow(
                    title = "TXT 目录规则",
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = { context.startActivity(Intent(context, TxtTocRuleActivity::class.java)) },
                )
                InkListRow(
                    title = "字典规则",
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = { context.startActivity(Intent(context, DictRuleActivity::class.java)) },
                )
                InkListRow(
                    title = "RSS 源管理",
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = { context.startActivity(Intent(context, RssSourceActivity::class.java)) },
                )
                InkListRow(
                    title = "其他高级配置（经典）",
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    showDivider = false,
                    onClick = {
                        context.startActivity(
                            Intent(context, ConfigActivity::class.java).apply {
                                putExtra("configTag", ConfigTag.OTHER_CONFIG)
                            },
                        )
                    },
                )
            }

            InkCardGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.ink)
                            .border(0.5.dp, colors.line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Ink",
                            color = colors.inkOnSolid,
                            fontFamily = serifFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "InkPaper",
                            color = colors.ink,
                            fontFamily = serifFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${BuildConfig.VERSION_NAME} · 黑白灰纸墨阅读器",
                            color = colors.inkSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                InkListRow(
                    title = "开源许可、日志与更新",
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    showDivider = false,
                    onClick = { context.startActivity(Intent(context, AboutActivity::class.java)) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
