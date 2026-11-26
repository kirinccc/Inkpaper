package io.legado.app.inkpaper.ui.reader

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.ui.search.InkSearchQueryHelper
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkChangeSourceSheet(
    book: Book,
    onDismiss: () -> Unit,
    onSwitchSource: (BookSource, Book, List<BookChapter>) -> Unit,
) {
    val colors = InkTheme.colors
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateListOf<SearchBook>() }
    var refreshKey by remember { mutableIntStateOf(0) }
    var searchByAuthor by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(true) }
    var searchedCount by remember { mutableIntStateOf(0) }
    var totalSources by remember { mutableIntStateOf(0) }
    var switchingUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(book.bookUrl, refreshKey, searchByAuthor) {
        isSearching = true
        errorMessage = null
        searchedCount = 0
        results.clear()

        if (!searchByAuthor) {
            val cached = withContext(Dispatchers.IO) {
                runCatching {
                    appDb.searchBookDao.changeSourceSearch(book.name, book.author, "", "")
                }.getOrDefault(emptyList())
            }
            if (cached.isNotEmpty()) {
                results.addAll(cached.distinctBy { it.origin })
            }
        }

        val queryWord = if (searchByAuthor && book.author.isNotBlank()) {
            book.author.trim()
        } else {
            InkSearchQueryHelper.normalizeQuery(book.name)
        }

        val parts = withContext(Dispatchers.IO) {
            appDb.bookSourceDao.allEnabledPart
        }
        totalSources = parts.size
        val semaphore = Semaphore(6)
        withContext(Dispatchers.IO) {
            coroutineScope {
                parts.map { part ->
                    async {
                        semaphore.withPermit {
                            val source = part.getBookSource()
                            if (source != null) {
                                val items = withTimeoutOrNull(15_000L) {
                                    runCatching {
                                        WebBook.searchBookAwait(source, queryWord, 1)
                                    }.getOrNull()
                                }.orEmpty()
                                val matched = if (searchByAuthor) {
                                    items.filter {
                                        it.author.isNotBlank() &&
                                            InkSearchQueryHelper.isSameAuthor(it.author, book.author)
                                    }
                                } else {
                                    items.filter {
                                        InkSearchQueryHelper.isSameBookTitle(it.name, book.name) &&
                                            InkSearchQueryHelper.isSameAuthor(it.author, book.author)
                                    }
                                }
                                if (matched.isNotEmpty()) {
                                    matched.forEach { it.releaseHtmlData() }
                                    runCatching {
                                        appDb.searchBookDao.insert(*matched.toTypedArray())
                                    }
                                    withContext(Dispatchers.Main) {
                                        matched.forEach { candidate ->
                                            if (results.none { it.origin == candidate.origin }) {
                                                results.add(candidate)
                                            }
                                        }
                                        results.sortBy { it.originOrder }
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                searchedCount += 1
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        isSearching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "切换书源",
                        color = colors.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = if (isSearching && totalSources > 0) {
                        "正在搜索《${book.name}》($searchedCount/$totalSources) · 已找到 ${results.size} 源"
                    } else {
                        "《${book.name}》共找到 ${results.size} 个可用源"
                    }
                    Text(
                        text = subtitle,
                        color = colors.inkSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { refreshKey += 1 },
                    enabled = !isSearching,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "重新搜源",
                        tint = if (isSearching) colors.inkTertiary else colors.ink,
                    )
                }
            }

            if (book.author.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InkFilterChip(
                        text = "按书名匹配",
                        selected = !searchByAuthor,
                        onClick = { searchByAuthor = false },
                    )
                    InkFilterChip(
                        text = "按作者找改名源（${book.author}）",
                        selected = searchByAuthor,
                        onClick = { searchByAuthor = true },
                    )
                }
            }

            if (isSearching && totalSources > 0) {
                LinearProgressIndicator(
                    progress = { (searchedCount.toFloat() / totalSources.toFloat()).coerceIn(0f, 1f) },
                    color = colors.ink,
                    trackColor = colors.line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp),
                )
            } else {
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = colors.inkSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (results.isEmpty() && !isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val canTryAuthor = !searchByAuthor && book.author.isNotBlank()
                    InkEmptyState(
                        title = "未搜到其他书源",
                        subtitle = if (canTryAuthor) "这本书可能在站外使用了旧书名，可尝试按作者检索" else "请检查启用的书源，或稍后重试",
                        actionLabel = if (canTryAuthor) "按作者「${book.author}」搜改名源" else "重新搜源",
                        onAction = {
                            if (canTryAuthor) {
                                searchByAuthor = true
                            } else {
                                refreshKey += 1
                            }
                        },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { "${it.origin}_${it.bookUrl}" }) { item ->
                        val isCurrent = item.origin == book.origin
                        val isSwitching = switchingUrl == item.bookUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = switchingUrl == null) {
                                    errorMessage = null
                                    switchingUrl = item.bookUrl
                                    scope.launch {
                                        val switchResult = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val source = appDb.bookSourceDao.getBookSource(item.origin)
                                                    ?: error("书源不存在")
                                                var targetBook = item.toBook()
                                                if (targetBook.tocUrl.isBlank()) {
                                                    targetBook = WebBook.getBookInfoAwait(source, targetBook)
                                                }
                                                val toc = WebBook.getChapterListAwait(source, targetBook).getOrThrow()
                                                Triple(source, targetBook, toc)
                                            }
                                        }
                                        switchingUrl = null
                                        switchResult.onSuccess { (source, newBook, toc) ->
                                            onDismiss()
                                            onSwitchSource(source, newBook, toc)
                                        }.onFailure { err ->
                                            errorMessage = "换源失败：${err.localizedMessage ?: "无法获取目录"}"
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = item.originName.ifBlank { item.origin },
                                        color = colors.ink,
                                        fontSize = 14.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isCurrent) {
                                        Text(
                                            text = "当前",
                                            color = colors.inkOnSolid,
                                            fontSize = 10.5.sp,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(colors.ink)
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                val subParts = listOfNotNull(
                                    if (!InkSearchQueryHelper.isSameBookTitle(item.name, book.name)) "站内名：《${item.name}》" else null,
                                    item.latestChapterTitle?.takeIf { it.isNotBlank() }?.let { "最新：$it" },
                                ).joinToString(" · ")
                                if (subParts.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = subParts,
                                        color = colors.inkSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (isSwitching) {
                                CircularProgressIndicator(
                                    color = colors.ink,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = colors.line,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}
