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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookHighlight
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkSegmentedControl
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InkAllBookmarksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 书签, 1: 划线笔记
    var query by remember { mutableStateOf("") }
    var highlightRefreshKey by remember { mutableIntStateOf(0) }

    val bookmarks by appDb.bookmarkDao.flowAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val highlights by produceState(initialValue = emptyList<BookHighlight>(), highlightRefreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { appDb.bookHighlightDao.all }.getOrDefault(emptyList())
        }
    }

    val filteredBookmarks = remember(bookmarks, query) {
        if (query.isBlank()) {
            bookmarks
        } else {
            bookmarks.filter {
                it.bookName.contains(query, true) ||
                    it.chapterName.contains(query, true) ||
                    it.bookText.contains(query, true) ||
                    it.content.contains(query, true)
            }
        }
    }

    val filteredHighlights = remember(highlights, query) {
        if (query.isBlank()) {
            highlights
        } else {
            highlights.filter {
                it.bookName.contains(query, true) ||
                    it.chapterName.contains(query, true) ||
                    it.bookText.contains(query, true) ||
                    it.note.contains(query, true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(title = "书签与笔记", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InkSegmentedControl(
                options = listOf("书签 (${bookmarks.size})", "划线笔记 (${highlights.size})"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.chip)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("搜索书名、章节或摘录内容", color = colors.inkSecondary, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.ink, fontSize = 13.sp),
                        cursorBrush = SolidColor(colors.ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清空",
                        tint = colors.inkSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { query = "" },
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.line)

        if (selectedTab == 0) {
            if (filteredBookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InkEmptyState(
                        title = "暂无书签",
                        subtitle = "阅读时在顶部菜单点击书签图标即可添加",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    items(filteredBookmarks, key = { it.time }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch(Dispatchers.IO) {
                                        val book = appDb.bookDao.getBook(bookmark.bookName, bookmark.bookAuthor)
                                            ?: appDb.bookDao.findByName(bookmark.bookName).firstOrNull()
                                        if (book != null) {
                                            val target = book.copy(
                                                durChapterIndex = bookmark.chapterIndex,
                                                durChapterTitle = bookmark.chapterName,
                                                durChapterPos = bookmark.chapterPos,
                                            )
                                            appDb.bookDao.update(target)
                                            withContext(Dispatchers.Main) {
                                                context.startActivityForBook(target)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                context.toastOnUi("该书已不在书架中")
                                            }
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
                                        text = bookmark.bookName,
                                        color = colors.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.chip)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                    Text(
                                        text = bookmark.chapterName,
                                        color = colors.inkSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (bookmark.bookText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = bookmark.bookText,
                                        color = colors.ink,
                                        fontSize = 13.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (bookmark.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bookmark.content,
                                        color = colors.inkSecondary,
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
        } else {
            if (filteredHighlights.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InkEmptyState(
                        title = "暂无划线笔记",
                        subtitle = "长按正文划选文字即可记录高亮与想法",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    items(filteredHighlights, key = { it.time }) { highlight ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch(Dispatchers.IO) {
                                        val book = appDb.bookDao.getBook(highlight.bookUrl)
                                            ?: appDb.bookDao.getBook(highlight.bookName, highlight.bookAuthor)
                                        if (book != null) {
                                            val target = book.copy(
                                                durChapterIndex = highlight.chapterIndex,
                                                durChapterTitle = highlight.chapterName,
                                                durChapterPos = highlight.chapterPos,
                                            )
                                            appDb.bookDao.update(target)
                                            withContext(Dispatchers.Main) {
                                                context.startActivityForBook(target)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                context.toastOnUi("该书已不在书架中")
                                            }
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
                                        text = highlight.bookName,
                                        color = colors.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.chip)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                    Text(
                                        text = highlight.chapterName,
                                        color = colors.inkSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
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
                                        withContext(Dispatchers.Main) { highlightRefreshKey += 1 }
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
