package io.legado.app.inkpaper.ui.discover

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.SearchBook
import io.legado.app.inkpaper.design.InkBookCover
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkTheme

@Composable
fun InkExploreBooksScreen(
    sourceUrl: String,
    onBack: () -> Unit,
    onSelectBook: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InkExploreViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sourceUrl) {
        viewModel.init(sourceUrl)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(
            title = state.sourceName.ifBlank { "发现" },
            onBack = onBack,
        )

        if (state.categories.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.categories) { index, kind ->
                    InkFilterChip(
                        text = kind.title,
                        selected = index == state.selectedIndex,
                        onClick = { viewModel.selectCategory(index) },
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp)
                }
            }

            state.books.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    InkEmptyState(
                        title = "暂无内容",
                        subtitle = state.errorMessage ?: "当前分类下没有获取到书籍",
                        actionLabel = "重试",
                        onAction = viewModel::retry,
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    items(state.books, key = { it.bookUrl }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBook(item) }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            InkBookCover(
                                coverUrl = item.coverUrl,
                                title = item.name,
                                author = item.author,
                                cornerRadius = 5.dp,
                                modifier = Modifier.size(width = 56.dp, height = 76.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = colors.ink,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = listOfNotNull(
                                    item.author.takeIf { it.isNotBlank() },
                                    item.kind?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = meta,
                                        color = colors.inkSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!item.latestChapterTitle.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "最新：${item.latestChapterTitle}",
                                        color = colors.inkSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                val intro = item.intro?.trim()?.replace(Regex("\\s+"), " ")
                                if (!intro.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = intro,
                                        color = colors.inkTertiary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                    }

                    if (state.hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(
                                        color = colors.ink,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::loadNextPage,
                                        shape = CircleShape,
                                        border = BorderStroke(0.5.dp, colors.line),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 9.dp),
                                    ) {
                                        Text("加载更多", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
