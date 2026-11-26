package io.legado.app.inkpaper.ui.bookdetail

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.inkpaper.design.InkBookCover
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.utils.startActivityForBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkBookDetailScreen(
    bookKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InkBookDetailViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var expandIntro by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var reverseToc by remember { mutableStateOf(false) }

    LaunchedEffect(bookKey) {
        viewModel.load(bookKey)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(
            title = "书籍详情",
            onBack = onBack,
            actions = {
                state.book?.let { book ->
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, BookInfoActivity::class.java).apply {
                                    putExtra("name", book.name)
                                    putExtra("author", book.author)
                                    putExtra("bookUrl", book.bookUrl)
                                },
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "更多",
                            tint = colors.ink,
                        )
                    }
                }
            },
        )

        val book = state.book
        if (book == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.errorMessage != null) {
                    InkEmptyState(
                        title = "无法加载书籍",
                        subtitle = state.errorMessage.orEmpty(),
                        actionLabel = "返回",
                        onAction = onBack,
                    )
                } else {
                    CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp)
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            InkBookCover(
                coverUrl = book.getDisplayCover(),
                title = book.name,
                author = book.author,
                cornerRadius = 8.dp,
                modifier = Modifier.size(width = 118.dp, height = 162.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = book.name,
                color = colors.ink,
                fontFamily = serifFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val meta = listOfNotNull(
                book.author.takeIf { it.isNotBlank() },
                book.kind?.takeIf { it.isNotBlank() },
                book.wordCount?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    color = colors.inkSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (book.originName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "来源：${book.originName}",
                    color = colors.inkTertiary,
                    fontSize = 11.5.sp,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = {
                    viewModel.prepareForReading { readyBook ->
                        context.startActivityForBook(readyBook)
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.inkOnSolid,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = if (book.durChapterIndex > 0) "继续阅读" else "开始阅读",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::toggleBookshelf,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    contentPadding = PaddingValues(vertical = 11.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (state.inBookshelf) Icons.Outlined.Check else Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                    Text(
                        text = if (state.inBookshelf) "已在书架" else "加入书架",
                        fontSize = 13.5.sp,
                    )
                }
                OutlinedButton(
                    onClick = { showTocSheet = true },
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    contentPadding = PaddingValues(vertical = 11.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                    val tocLabel = when {
                        state.isLoadingToc -> "加载目录..."
                        state.chapters.isNotEmpty() -> "目录 (${state.chapters.size})"
                        else -> "目录"
                    }
                    Text(text = tocLabel, fontSize = 13.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            val introText = book.getDisplayIntro()?.trim().orEmpty()
            if (introText.isNotEmpty()) {
                InkCardGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandIntro = !expandIntro }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "简介",
                                color = colors.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (expandIntro) "收起" else "展开",
                                color = colors.inkSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = introText,
                            color = colors.inkSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                            maxLines = if (expandIntro) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            InkCardGroup {
                InkListRow(
                    title = "最新章节",
                    subtitle = book.latestChapterTitle?.takeIf { it.isNotBlank() } ?: "暂无章节信息",
                    onClick = { showTocSheet = true },
                )
                InkListRow(
                    title = "当前进度",
                    subtitle = book.durChapterTitle?.takeIf { it.isNotBlank() } ?: "尚未开始阅读",
                    showDivider = false,
                    onClick = {
                        viewModel.prepareForReading { readyBook ->
                            context.startActivityForBook(readyBook)
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "目录 (${state.chapters.size})",
                        color = colors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { reverseToc = !reverseToc }) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = "倒序",
                            tint = colors.inkSecondary,
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                if (state.isLoadingToc) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp)
                    }
                } else if (state.chapters.isEmpty()) {
                    InkEmptyState(
                        title = "暂无目录",
                        subtitle = state.errorMessage ?: "请稍后再试或切换其他书源",
                    )
                } else {
                    val displayChapters = if (reverseToc) state.chapters.reversed() else state.chapters
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(displayChapters, key = { _, ch -> "${ch.index}_${ch.url}" }) { _, chapter ->
                            val isCurrent = chapter.index == state.book?.durChapterIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showTocSheet = false
                                        viewModel.prepareForReading(chapterIndex = chapter.index) { readyBook ->
                                            context.startActivityForBook(readyBook)
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
        }
    }
}
