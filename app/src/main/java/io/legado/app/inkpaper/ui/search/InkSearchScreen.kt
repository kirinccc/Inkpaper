package io.legado.app.inkpaper.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.SearchBook
import io.legado.app.inkpaper.design.InkBookCover
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkSearchScreen(
    onBack: () -> Unit,
    onSelectResult: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InkSearchViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.ink,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.chip)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.query.isEmpty()) {
                        Text(
                            text = "搜索书名、作者、书源",
                            color = colors.inkSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::updateQuery,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.ink,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(colors.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                viewModel.submitSearch()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清空",
                        tint = colors.inkSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.updateQuery("") },
                    )
                }
            }
            Text(
                text = if (state.isSearching) "停止" else "搜索",
                color = colors.inkOnSolid,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.ink)
                    .clickable {
                        keyboard?.hide()
                        if (state.isSearching) viewModel.stopSearch() else viewModel.submitSearch()
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }

        if (state.isSearching && state.totalSources > 0) {
            LinearProgressIndicator(
                progress = { (state.searchedCount.toFloat() / state.totalSources.toFloat()).coerceIn(0f, 1f) },
                color = colors.ink,
                trackColor = colors.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp),
            )
        } else {
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
        }

        if (!state.hasSubmitted || state.query.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "搜索历史",
                            color = colors.ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "清空",
                            color = colors.inkSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.clickable { viewModel.clearHistory() },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.take(18).forEach { item ->
                            InkFilterChip(
                                text = item.word,
                                selected = false,
                                onClick = {
                                    keyboard?.hide()
                                    viewModel.submitSearch(item.word)
                                },
                            )
                        }
                    }
                } else {
                    InkEmptyState(
                        title = "全网书源聚合搜索",
                        subtitle = "输入书名或作者，将自动归并同一本书的多个书源",
                    )
                }
            }
        } else {
            if (state.isSearching || state.results.isNotEmpty()) {
                val statusLabel = if (state.isSearching) {
                    "正在检索 ${state.searchedCount} / ${state.totalSources} 个书源 · 已找到 ${state.results.size} 本"
                } else {
                    "共找到 ${state.results.size} 个结果"
                }
                Text(
                    text = statusLabel,
                    color = colors.inkSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            if (!state.isSearching && state.results.isEmpty()) {
                InkEmptyState(
                    title = "未找到相关书籍",
                    subtitle = state.errorMessage ?: "换个关键词试试，或检查启用的书源设置",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results, key = { it.bookUrl }) { item ->
                        SearchResultRow(
                            item = item,
                            onClick = { onSelectResult(item) },
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchBook,
    onClick: () -> Unit,
) {
    val colors = InkTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.name,
                    color = colors.ink,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val sourceCount = item.origins.size
                val sourceTag = if (sourceCount > 1) {
                    "${item.originName} 等 $sourceCount 源"
                } else {
                    item.originName
                }
                if (sourceTag.isNotBlank()) {
                    Text(
                        text = sourceTag,
                        color = colors.inkSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.chip)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val meta = listOfNotNull(
                item.author.takeIf { it.isNotBlank() },
                item.kind?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
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
}
