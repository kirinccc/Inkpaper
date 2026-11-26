package io.legado.app.inkpaper.ui.search

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.inkpaper.design.InkBookCover
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.ui.browser.WebViewActivity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkSearchScreen(
    onBack: () -> Unit,
    onSelectResult: (SearchBook) -> Unit,
    initialQuery: String = "",
    onOpenBookSources: () -> Unit = {},
    onBookAddedByUrl: (Book) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: InkSearchViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val availableGroups by viewModel.availableGroups.collectAsStateWithLifecycle()
    val disabledBackupCount by viewModel.disabledBackupCount.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var showAddUrlSheet by remember { mutableStateOf(false) }
    var urlSheetInitialInput by remember { mutableStateOf("") }

    val webSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedQuery = result.data?.getStringExtra("searchQuery")?.trim().orEmpty()
            val pickedUrl = result.data?.getStringExtra("bookUrl")?.trim().orEmpty()
            if (pickedQuery.isNotEmpty()) {
                viewModel.submitSearch(pickedQuery)
            } else if (pickedUrl.isNotEmpty()) {
                urlSheetInitialInput = pickedUrl
                viewModel.clearUrlAddError()
                showAddUrlSheet = true
                viewModel.addBookByUrlOrId(pickedUrl) { book ->
                    showAddUrlSheet = false
                    onBookAddedByUrl(book)
                }
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && !state.hasSubmitted) {
            viewModel.submitSearch(initialQuery)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
            IconButton(
                onClick = {
                    urlSheetInitialInput = ""
                    viewModel.clearUrlAddError()
                    showAddUrlSheet = true
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = "链接或 ID 添加",
                    tint = colors.ink,
                )
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

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            item {
                InkFilterChip(
                    text = "精准",
                    selected = state.precisionSearch,
                    onClick = { viewModel.togglePrecisionSearch() },
                )
            }
            item {
                InkFilterChip(
                    text = "全部启用",
                    selected = state.selectedGroup == null,
                    onClick = { viewModel.selectGroup(null) },
                )
            }
            items(availableGroups) { group ->
                InkFilterChip(
                    text = group,
                    selected = state.selectedGroup == group,
                    onClick = {
                        viewModel.selectGroup(if (state.selectedGroup == group) null else group)
                    },
                )
            }
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
                        subtitle = "输入书名或作者，将自动归并同一本书的多个书源；右上角链接图标支持网址或番茄 ID 直达",
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    InkEmptyState(
                        title = "未找到相关书籍",
                        subtitle = state.errorMessage ?: "可能是平台改名书、小众原创或当前分组未收录",
                    )
                    InkSearchFallbackPanel(
                        query = state.query,
                        precisionSearch = state.precisionSearch,
                        selectedGroup = state.selectedGroup,
                        disabledBackupCount = disabledBackupCount,
                        fallbackHints = state.fallbackHints,
                        onTogglePrecision = { viewModel.togglePrecisionSearch() },
                        onResetGroup = { viewModel.selectGroup(null) },
                        onEnableBackupRetry = { viewModel.enableBackupSourcesAndRetry() },
                        onRetryHint = { hint -> viewModel.submitSearch(hint) },
                        onOpenWebSearch = {
                            webSearchLauncher.launch(
                                Intent(context, WebViewActivity::class.java).apply {
                                    putExtra("url", InkSearchQueryHelper.buildWebSearchUrl(state.query))
                                    putExtra("title", "全网查书：${state.query}")
                                    putExtra("enableSearchReturn", true)
                                },
                            )
                        },
                        onOpenAddUrl = {
                            urlSheetInitialInput = ""
                            viewModel.clearUrlAddError()
                            showAddUrlSheet = true
                        },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    items(state.results, key = { it.bookUrl }) { item ->
                        SearchResultRow(
                            item = item,
                            onClick = { onSelectResult(item) },
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                    }
                    if (!state.isSearching && state.results.size in 1..2) {
                        item(key = "fallback_panel") {
                            Spacer(modifier = Modifier.height(16.dp))
                            InkSearchFallbackPanel(
                                query = state.query,
                                precisionSearch = state.precisionSearch,
                                selectedGroup = state.selectedGroup,
                                disabledBackupCount = disabledBackupCount,
                                fallbackHints = state.fallbackHints,
                                onTogglePrecision = { viewModel.togglePrecisionSearch() },
                                onResetGroup = { viewModel.selectGroup(null) },
                                onEnableBackupRetry = { viewModel.enableBackupSourcesAndRetry() },
                                onRetryHint = { hint -> viewModel.submitSearch(hint) },
                                onOpenWebSearch = {
                                    webSearchLauncher.launch(
                                        Intent(context, WebViewActivity::class.java).apply {
                                            putExtra("url", InkSearchQueryHelper.buildWebSearchUrl(state.query))
                                            putExtra("title", "全网查书：${state.query}")
                                            putExtra("enableSearchReturn", true)
                                        },
                                    )
                                },
                                onOpenAddUrl = {
                                    urlSheetInitialInput = ""
                                    viewModel.clearUrlAddError()
                                    showAddUrlSheet = true
                                },
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddUrlSheet) {
        InkAddBookUrlSheet(
            initialInput = urlSheetInitialInput,
            isLoading = state.isAddingUrl,
            errorMessage = state.urlAddError,
            showOpenSourcesHint = state.urlAddNeedEnableSource,
            onDismiss = { showAddUrlSheet = false },
            onSubmit = { input ->
                viewModel.addBookByUrlOrId(input) { book ->
                    showAddUrlSheet = false
                    onBookAddedByUrl(book)
                }
            },
            onOpenBookSources = onOpenBookSources,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InkSearchFallbackPanel(
    query: String,
    precisionSearch: Boolean,
    selectedGroup: String?,
    disabledBackupCount: Int,
    fallbackHints: List<String>,
    onTogglePrecision: () -> Unit,
    onResetGroup: () -> Unit,
    onEnableBackupRetry: () -> Unit,
    onRetryHint: (String) -> Unit,
    onOpenWebSearch: () -> Unit,
    onOpenAddUrl: () -> Unit,
) {
    val colors = InkTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSubtle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "没找到目标书？可能是改名书或小众书",
            color = colors.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (precisionSearch) {
                InkFilterChip(
                    text = "关闭精准匹配重试",
                    selected = false,
                    onClick = onTogglePrecision,
                )
            }
            if (selectedGroup != null) {
                InkFilterChip(
                    text = "搜全部启用书源",
                    selected = false,
                    onClick = onResetGroup,
                )
            }
            if (disabledBackupCount > 0) {
                InkFilterChip(
                    text = "启用备用源重搜 (+$disabledBackupCount)",
                    selected = false,
                    onClick = onEnableBackupRetry,
                )
            }
            fallbackHints.forEach { hint ->
                InkFilterChip(
                    text = "改搜「$hint」",
                    selected = false,
                    onClick = { onRetryHint(hint) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onOpenWebSearch,
                enabled = query.isNotBlank(),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, colors.line),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                modifier = Modifier.weight(1f),
            ) {
                Text("浏览器查作者/旧名", fontSize = 12.5.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenAddUrl,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, colors.line),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                modifier = Modifier.weight(1f),
            ) {
                Text("粘贴链接 / 番茄 ID", fontSize = 12.5.sp, maxLines = 1)
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
