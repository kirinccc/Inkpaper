package io.legado.app.inkpaper.ui.discover

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.appDb
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkPageHeader
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity

@Composable
fun InkDiscoverScreen(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val exploreSources by appDb.bookSourceDao.flowExplore()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkPageHeader(title = "发现")

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.chip)
                        .clickable(onClick = onOpenSearch)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "搜索书籍、作者、书源",
                        color = colors.inkSecondary,
                        fontSize = 14.sp,
                    )
                }
            }

            item {
                InkCardGroup {
                    InkListRow(
                        title = "RSS 订阅",
                        subtitle = "管理与浏览订阅源",
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.chip),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RssFeed,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
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
                            context.startActivity(Intent(context, RssSourceActivity::class.java))
                        },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "书源发现",
                        color = colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "管理 ›",
                        color = colors.inkSecondary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, BookSourceActivity::class.java))
                        },
                    )
                }
            }

            if (exploreSources.isEmpty()) {
                item {
                    InkEmptyState(
                        title = "暂无可发现书源",
                        subtitle = "请在书源管理中导入或启用支持发现的书源",
                        actionLabel = "前往书源管理",
                        onAction = {
                            context.startActivity(Intent(context, BookSourceActivity::class.java))
                        },
                    )
                }
            } else {
                item {
                    InkCardGroup {
                        exploreSources.forEachIndexed { index, source ->
                            InkListRow(
                                title = source.bookSourceName,
                                subtitle = source.bookSourceGroup?.takeIf { it.isNotBlank() },
                                leading = {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.chip),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Explore,
                                            contentDescription = null,
                                            tint = colors.ink,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.inkSecondary,
                                    )
                                },
                                showDivider = index < exploreSources.lastIndex,
                                onClick = {
                                    context.startActivity(
                                        Intent(context, ExploreShowActivity::class.java).apply {
                                            putExtra("sourceUrl", source.bookSourceUrl)
                                            putExtra("exploreName", source.bookSourceName)
                                            putExtra("showExploreCategories", true)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
