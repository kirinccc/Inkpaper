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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookHighlight
import io.legado.app.data.entities.Bookmark
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkSegmentedControl
import io.legado.app.inkpaper.design.InkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkReaderTocSheet(
    book: Book,
    currentChapterIndex: Int,
    onDismiss: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSelectBookmark: (Bookmark) -> Unit,
    onSelectHighlight: (BookHighlight) -> Unit,
) {
    val colors = InkTheme.colors
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var reverseOrder by remember { mutableStateOf(false) }

    val chapters by produceState(initialValue = emptyList<BookChapter>(), book.bookUrl) {
        value = withContext(Dispatchers.IO) {
            appDb.bookChapterDao.getChapterList(book.bookUrl)
        }
    }
    val bookmarks by appDb.bookmarkDao.flowByBook(book.name, book.author)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val highlights by appDb.bookHighlightDao.flowByBook(book.bookUrl)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp),
        ) {
            InkSegmentedControl(
                options = listOf("目录", "书签", "笔记"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )

            when (selectedTab) {
                0 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.chip)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text("搜索章节", color = colors.inkSecondary, fontSize = 13.sp)
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = colors.ink, fontSize = 13.sp),
                                    cursorBrush = SolidColor(colors.ink),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "清空",
                                    tint = colors.inkSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { searchQuery = "" },
                                )
                            }
                        }
                        IconButton(onClick = { reverseOrder = !reverseOrder }) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = "倒序",
                                tint = if (reverseOrder) colors.ink else colors.inkSecondary,
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)

                    val filtered = remember(chapters, searchQuery, reverseOrder) {
                        val base = if (searchQuery.isBlank()) {
                            chapters
                        } else {
                            chapters.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        }
                        if (reverseOrder) base.reversed() else base
                    }

                    if (filtered.isEmpty()) {
                        InkEmptyState(
                            title = if (chapters.isEmpty()) "暂无目录" else "未找到匹配章节",
                            subtitle = if (chapters.isEmpty()) "请先刷新或加载章节目录" else "换个关键词试试",
                        )
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(chapters.size, reverseOrder, searchQuery) {
                            if (searchQuery.isBlank()) {
                                val targetIdx = filtered.indexOfFirst { it.index == currentChapterIndex }
                                if (targetIdx >= 0) {
                                    listState.scrollToItem((targetIdx - 3).coerceAtLeast(0))
                                }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(filtered, key = { "${it.index}_${it.url}" }) { chapter ->
                                val isCurrent = chapter.index == currentChapterIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onSelectChapter(chapter.index)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colors.ink),
                                        )
                                    }
                                    Text(
                                        text = chapter.title,
                                        color = if (isCurrent) colors.ink else colors.inkSecondary,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
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

                1 -> {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colors.line,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (bookmarks.isEmpty()) {
                        InkEmptyState(
                            title = "暂无书签",
                            subtitle = "在阅读顶栏点击书签图标即可添加",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(bookmarks, key = { it.time }) { bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onSelectBookmark(bookmark)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bookmark.chapterName,
                                            color = colors.ink,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (bookmark.bookText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = bookmark.bookText,
                                                color = colors.inkSecondary,
                                                fontSize = 12.5.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (bookmark.content.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = bookmark.content,
                                                color = colors.inkTertiary,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                appDb.bookmarkDao.delete(bookmark)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "删除书签",
                                            tint = colors.inkSecondary,
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

                else -> {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colors.line,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (highlights.isEmpty()) {
                        InkEmptyState(
                            title = "暂无划线笔记",
                            subtitle = "长按正文划选文字即可记录高亮与想法",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(highlights, key = { it.time }) { highlight ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onSelectHighlight(highlight)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = highlight.chapterName,
                                            color = colors.inkSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = highlight.bookText,
                                            color = colors.ink,
                                            fontSize = 13.5.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 20.sp,
                                        )
                                        if (highlight.note.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = highlight.note,
                                                color = colors.inkSecondary,
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.surfaceSubtle)
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                appDb.bookHighlightDao.delete(highlight)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "删除笔记",
                                            tint = colors.inkSecondary,
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
    }
}
