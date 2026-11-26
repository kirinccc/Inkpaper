package io.legado.app.inkpaper.ui.bookshelf

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.Book
import io.legado.app.inkpaper.design.InkBookCover
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkPageHeader
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.design.InkThemeController
import io.legado.app.inkpaper.ui.search.InkAddBookUrlSheet
import io.legado.app.inkpaper.ui.search.InkBookUrlResolveResult
import io.legado.app.inkpaper.ui.search.InkBookUrlResolver
import io.legado.app.inkpaper.ui.settings.InkLocalImportSheet
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InkBookshelfScreen(
    onOpenSearch: () -> Unit,
    onOpenDetail: (Book) -> Unit,
    onOpenBookSources: () -> Unit,
    onOpenBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: InkBookshelfViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val uiPrefs by InkThemeController.prefs.collectAsStateWithLifecycle()
    val books by viewModel.filteredBooks.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val userGroups by viewModel.userGroups.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val continueBook by viewModel.continueBook.collectAsStateWithLifecycle()
    val enabledSourceCount by viewModel.enabledSourceCount.collectAsStateWithLifecycle()

    var showMoreSheet by remember { mutableStateOf(false) }
    var showLocalImportSheet by remember { mutableStateOf(false) }
    var showAddUrlSheet by remember { mutableStateOf(false) }
    var isAddingUrl by remember { mutableStateOf(false) }
    var urlAddError by remember { mutableStateOf<String?>(null) }
    var urlNeedSource by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var actionBook by remember { mutableStateOf<Book?>(null) }
    var confirmRemoveBook by remember { mutableStateOf<Book?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkPageHeader(
            title = "书架",
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索",
                        tint = colors.ink,
                    )
                }
                IconButton(onClick = { showMoreSheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "更多",
                        tint = colors.ink,
                    )
                }
            },
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        ) {
            item {
                InkFilterChip(
                    text = "全部",
                    selected = selectedFilter is ShelfFilter.All,
                    onClick = { viewModel.selectFilter(ShelfFilter.All) },
                )
            }
            item {
                InkFilterChip(
                    text = "在读",
                    selected = selectedFilter is ShelfFilter.Reading,
                    onClick = { viewModel.selectFilter(ShelfFilter.Reading) },
                )
            }
            item {
                InkFilterChip(
                    text = "已读完",
                    selected = selectedFilter is ShelfFilter.Finished,
                    onClick = { viewModel.selectFilter(ShelfFilter.Finished) },
                )
            }
            item {
                InkFilterChip(
                    text = "本地",
                    selected = selectedFilter is ShelfFilter.Local,
                    onClick = { viewModel.selectFilter(ShelfFilter.Local) },
                )
            }
            items(userGroups, key = { it.groupId }) { group ->
                InkFilterChip(
                    text = group.groupName,
                    selected = (selectedFilter as? ShelfFilter.Group)?.group?.groupId == group.groupId,
                    onClick = { viewModel.selectFilter(ShelfFilter.Group(group)) },
                )
            }
        }

        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (allBooks.isEmpty() && enabledSourceCount == 0) {
                    ColdStartOnboardingCard(
                        onImportLocal = { showLocalImportSheet = true },
                        onOpenBookSources = onOpenBookSources,
                        onOpenBackup = onOpenBackup,
                    )
                } else {
                    InkEmptyState(
                        title = if (allBooks.isEmpty()) "书架还是空的" else "该分组暂无书籍",
                        subtitle = if (allBooks.isEmpty()) {
                            "去搜索想读的小说，或导入本地 TXT / EPUB 书籍"
                        } else {
                            "切换其他分组看看，或把书籍加入当前分组"
                        },
                        actionLabel = if (allBooks.isEmpty()) "搜索找书" else "查看全部",
                        onAction = {
                            if (allBooks.isEmpty()) {
                                onOpenSearch()
                            } else {
                                viewModel.selectFilter(ShelfFilter.All)
                            }
                        },
                    )
                }
            }
        } else if (uiPrefs.bookshelfGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiPrefs.showContinueCard && continueBook != null && selectedFilter is ShelfFilter.All) {
                    val book = continueBook!!
                    item(span = { GridItemSpan(3) }) {
                        ContinueReadingCard(
                            book = book,
                            onClick = { context.startActivityForBook(book) },
                        )
                    }
                }
                items(books, key = { it.bookUrl }) { book ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { context.startActivityForBook(book) },
                                onLongClick = { actionBook = book },
                            ),
                    ) {
                        InkBookCover(
                            coverUrl = book.getDisplayCover(),
                            title = book.name,
                            author = book.author,
                            showDot = book.lastCheckCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.73f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = book.name,
                            color = colors.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatProgressText(book),
                            color = colors.inkSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiPrefs.showContinueCard && continueBook != null && selectedFilter is ShelfFilter.All) {
                    val book = continueBook!!
                    item {
                        ContinueReadingCard(
                            book = book,
                            onClick = { context.startActivityForBook(book) },
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                }
                items(books, key = { it.bookUrl }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { context.startActivityForBook(book) },
                                onLongClick = { actionBook = book },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        InkBookCover(
                            coverUrl = book.getDisplayCover(),
                            title = book.name,
                            author = book.author,
                            showDot = book.lastCheckCount > 0,
                            cornerRadius = 5.dp,
                            modifier = Modifier.size(width = 48.dp, height = 66.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.name,
                                color = colors.ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = book.durChapterTitle?.takeIf { it.isNotBlank() } ?: book.author,
                                color = colors.inkSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatProgressText(book),
                                color = colors.inkTertiary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                }
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                InkListRow(
                    title = if (uiPrefs.bookshelfGrid) "切换为列表视图" else "切换为网格视图",
                    leading = {
                        Icon(
                            imageVector = if (uiPrefs.bookshelfGrid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = {
                        InkThemeController.setBookshelfGrid(!uiPrefs.bookshelfGrid)
                        showMoreSheet = false
                    },
                )
                InkListRow(
                    title = "显示「继续阅读」卡片",
                    trailing = {
                        InkSwitch(
                            checked = uiPrefs.showContinueCard,
                            onCheckedChange = { InkThemeController.setShowContinueCard(it) },
                        )
                    },
                )
                InkListRow(
                    title = "导入本地书籍",
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.FileOpen,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = {
                        showMoreSheet = false
                        showLocalImportSheet = true
                    },
                )
                InkListRow(
                    title = "链接 / 番茄 ID 加书",
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = {
                        showMoreSheet = false
                        urlAddError = null
                        urlNeedSource = false
                        showAddUrlSheet = true
                    },
                )
                InkListRow(
                    title = "书源管理",
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    showDivider = false,
                    onClick = {
                        showMoreSheet = false
                        onOpenBookSources()
                    },
                )
            }
        }
    }

    if (showLocalImportSheet) {
        InkLocalImportSheet(onDismiss = { showLocalImportSheet = false })
    }

    if (showAddUrlSheet) {
        InkAddBookUrlSheet(
            isLoading = isAddingUrl,
            errorMessage = urlAddError,
            showOpenSourcesHint = urlNeedSource,
            onDismiss = { showAddUrlSheet = false },
            onSubmit = { input ->
                scope.launch {
                    isAddingUrl = true
                    urlAddError = null
                    urlNeedSource = false
                    val result = withContext(Dispatchers.IO) {
                        InkBookUrlResolver.resolve(input)
                    }
                    isAddingUrl = false
                    when (result) {
                        is InkBookUrlResolveResult.Success -> {
                            showAddUrlSheet = false
                            onOpenDetail(result.book)
                        }
                        is InkBookUrlResolveResult.Failure -> {
                            urlAddError = result.message
                            urlNeedSource = result.needEnableSource
                        }
                    }
                }
            },
            onOpenBookSources = onOpenBookSources,
        )
    }

    actionBook?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { actionBook = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InkBookCover(
                        coverUrl = book.getDisplayCover(),
                        title = book.name,
                        author = book.author,
                        cornerRadius = 4.dp,
                        modifier = Modifier.size(width = 40.dp, height = 54.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.name,
                            color = colors.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${book.author} · ${formatProgressText(book)}",
                            color = colors.inkSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                InkListRow(
                    title = "继续阅读",
                    leading = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = {
                        actionBook = null
                        context.startActivityForBook(book)
                    },
                )
                InkListRow(
                    title = "书籍详情",
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = {
                        actionBook = null
                        onOpenDetail(book)
                    },
                )
                InkListRow(
                    title = if (book.order < 0) "取消置顶" else "置顶书籍",
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    onClick = {
                        actionBook = null
                        viewModel.togglePin(book)
                    },
                )
                InkListRow(
                    title = "移出书架",
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                        )
                    },
                    showDivider = false,
                    onClick = {
                        actionBook = null
                        confirmRemoveBook = book
                    },
                )
            }
        }
    }

    confirmRemoveBook?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmRemoveBook = null },
            containerColor = colors.surface,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSecondary,
            title = { Text("移出书架", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要将《${book.name}》从书架移出吗？", fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveBook = null
                        viewModel.removeBook(book)
                    },
                ) {
                    Text("移出", color = colors.ink, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveBook = null }) {
                    Text("取消", color = colors.inkSecondary)
                }
            },
        )
    }
}

@Composable
private fun ContinueReadingCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    InkCardGroup(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InkBookCover(
                coverUrl = book.getDisplayCover(),
                title = book.name,
                author = book.author,
                cornerRadius = 4.dp,
                modifier = Modifier.size(width = 36.dp, height = 48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "上次读到",
                        color = colors.inkSecondary,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.chip)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Text(
                        text = formatProgressText(book),
                        color = colors.inkSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.name,
                    color = colors.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!book.durChapterTitle.isNullOrBlank()) {
                    Text(
                        text = book.durChapterTitle.orEmpty(),
                        color = colors.inkSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "继续",
                color = colors.inkOnSolid,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.ink)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun formatProgressText(book: Book): String {
    if (book.durChapterIndex <= 0 && book.durChapterTime <= 0L) {
        return "未开始"
    }
    val total = book.totalChapterNum
    if (total <= 0) {
        return if (book.durChapterIndex == 0) "第 1 章" else "第 ${book.durChapterIndex + 1} 章"
    }
    if (book.durChapterIndex + 1 >= total) {
        return "已读完"
    }
    val percent = (((book.durChapterIndex + 1).toFloat() / total.toFloat()) * 100f)
        .roundToInt()
        .coerceIn(1, 99)
    return "已读 $percent%"
}

@Composable
private fun ColdStartOnboardingCard(
    onImportLocal: () -> Unit,
    onOpenBookSources: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val colors = InkTheme.colors
    InkCardGroup(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Text(
                text = "欢迎使用 InkPaper",
                color = colors.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "当前尚未启用任何在线书源。你可以直接导入本地书籍，或添加外部书源订阅 / 连接 WebDAV 恢复个人配置。",
                color = colors.inkSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            InkListRow(
                title = "导入本地 TXT / EPUB",
                onClick = onImportLocal,
            )
            InkListRow(
                title = "添加或管理书源订阅",
                onClick = onOpenBookSources,
            )
            InkListRow(
                title = "从 WebDAV 恢复配置与书源",
                showDivider = false,
                onClick = onOpenBackup,
            )
        }
    }
}
