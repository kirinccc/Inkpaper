package io.legado.app.inkpaper.ui.me

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.inkpaper.design.InkBookCover
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkSegmentedControl
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InkReadRecordsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sortTab by remember { mutableIntStateOf(0) } // 0: 最近阅读, 1: 阅读时长
    var refreshKey by remember { mutableIntStateOf(0) }

    val records by produceState(initialValue = emptyList<ReadRecordShow>(), refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { appDb.readRecordDao.allShow }.getOrDefault(emptyList())
        }
    }

    val sortedRecords = remember(records, sortTab) {
        if (sortTab == 1) {
            records.sortedByDescending { it.readTime }
        } else {
            records.sortedByDescending { it.lastRead }
        }
    }

    val totalHours = remember(records) {
        val ms = records.sumOf { it.readTime }
        val h = ms / 3_600_000.0
        if (h >= 10) h.toInt().toString() else String.format(Locale.getDefault(), "%.1f", h)
    }

    val dateFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(title = "阅读记录", onBack = onBack)

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InkEmptyState(
                    title = "暂无阅读记录",
                    subtitle = "开始阅读任意书籍后，阅读时长与足迹会记录在这里",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                item {
                    InkCardGroup {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = totalHours,
                                    color = colors.ink,
                                    fontFamily = serifFont,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("总阅读 / 小时", color = colors.inkSecondary, fontSize = 11.5.sp)
                            }
                            VerticalDivider(
                                thickness = 0.5.dp,
                                color = colors.line,
                                modifier = Modifier.height(30.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = records.size.toString(),
                                    color = colors.ink,
                                    fontFamily = serifFont,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("阅读书籍 / 本", color = colors.inkSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    InkSegmentedControl(
                        options = listOf("最近阅读", "阅读时长"),
                        selectedIndex = sortTab,
                        onSelect = { sortTab = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(sortedRecords, key = { "${it.bookName}_${it.author}" }) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    val book = appDb.bookDao.getBook(record.bookName, record.displayAuthor)
                                        ?: appDb.bookDao.findByName(record.bookName).firstOrNull()
                                    withContext(Dispatchers.Main) {
                                        if (book != null) {
                                            context.startActivityForBook(book)
                                        } else {
                                            context.toastOnUi("该书已不在书架中")
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InkBookCover(
                            coverUrl = record.coverUrl,
                            title = record.bookName,
                            author = record.displayAuthor,
                            cornerRadius = 4.dp,
                            modifier = Modifier.size(width = 44.dp, height = 60.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.bookName,
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                record.displayAuthor.takeIf { it.isNotBlank() },
                                record.lastChapterTitle?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sub,
                                    color = colors.inkSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val timeText = formatDuration(record.readTime)
                            val dateText = if (record.lastRead > 0L) dateFormatter.format(Date(record.lastRead)) else ""
                            Text(
                                text = listOf(timeText, dateText).filter { it.isNotBlank() }.joinToString(" · "),
                                color = colors.inkTertiary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    appDb.readRecordDao.deleteByBook(record.bookName, record.author)
                                    withContext(Dispatchers.Main) { refreshKey += 1 }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "删除记录",
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    return when {
        ms <= 0L -> "不足 1 分钟"
        hours > 0 -> "${hours} 小时 ${mins} 分钟"
        else -> "${mins} 分钟"
    }
}
