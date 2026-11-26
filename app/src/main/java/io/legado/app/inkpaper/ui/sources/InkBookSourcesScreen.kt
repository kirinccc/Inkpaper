package io.legado.app.inkpaper.ui.sources

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.BookSourceCheckState
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.RuleSub
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkBookSourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InkBookSourcesViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sources by viewModel.filteredSources.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isSyncingSub by viewModel.isSyncingSub.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showImportSheet by remember { mutableStateOf(false) }
    var showBatchSheet by remember { mutableStateOf(false) }
    var showSubSheet by remember { mutableStateOf(false) }
    var actionSource by remember { mutableStateOf<BookSourcePart?>(null) }
    var confirmDeleteSource by remember { mutableStateOf<BookSourcePart?>(null) }
    var confirmResetBuiltin by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            if (content.isNotBlank()) {
                showImportSheet = false
                showBatchSheet = false
                viewModel.importFromTextOrUrl(content)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(
            title = "书源管理",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showImportSheet = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "导入书源", tint = colors.ink)
                }
                IconButton(onClick = { showBatchSheet = true }) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "批量操作", tint = colors.ink)
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.chip)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(17.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (filter.query.isEmpty()) {
                    Text("搜索书源名称、分组或网址", color = colors.inkSecondary, fontSize = 13.5.sp)
                }
                BasicTextField(
                    value = filter.query,
                    onValueChange = viewModel::updateQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filter.query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "清空",
                    tint = colors.inkSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { viewModel.updateQuery("") },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            item {
                InkFilterChip("全部", filter.tab == 0 && filter.group == null, {
                    viewModel.selectTab(0)
                    viewModel.selectGroup(null)
                })
            }
            item { InkFilterChip("已启用", filter.tab == 1, { viewModel.selectTab(if (filter.tab == 1) 0 else 1) }) }
            item { InkFilterChip("已禁用", filter.tab == 2, { viewModel.selectTab(if (filter.tab == 2) 0 else 2) }) }
            item { InkFilterChip("可发现", filter.tab == 3, { viewModel.selectTab(if (filter.tab == 3) 0 else 3) }) }
            items(groups) { group ->
                InkFilterChip(
                    text = group,
                    selected = filter.group == group,
                    onClick = {
                        viewModel.selectGroup(if (filter.group == group) null else group)
                    },
                )
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceSubtle)
                    .clickable { viewModel.clearStatus() }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(statusMessage.orEmpty(), color = colors.ink, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Text("关闭", color = colors.inkSecondary, fontSize = 12.sp)
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.line)

        if (sources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InkEmptyState(
                    title = "暂无匹配书源",
                    subtitle = "点击右上角 + 可通过链接、JSON 或本地文件导入书源",
                    actionLabel = "导入书源",
                    onAction = { showImportSheet = true },
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                items(sources, key = { it.bookSourceUrl }) { part ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actionSource = part }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = part.bookSourceName,
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                part.bookSourceGroup?.takeIf { it.isNotBlank() },
                                if (part.hasLoginUrl) "需登录" else null,
                                when (part.checkStatus) {
                                    BookSourceCheckState.PASSED -> "可用"
                                    BookSourceCheckState.FAILED -> "校验失效"
                                    else -> null
                                },
                                if (part.hasExploreUrl && part.enabledExplore) "支持发现" else null,
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
                        }
                        InkSwitch(
                            checked = part.enabled,
                            onCheckedChange = { viewModel.toggleEnabled(part) },
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

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!isImporting) showImportSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("导入书源", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("粘贴书源 URL（http/https）或 JSON 内容", color = colors.inkSecondary, fontSize = 13.5.sp) },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.line,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        cursorColor = colors.ink,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text("本地文件", fontSize = 13.5.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.importFromTextOrUrl(importText)
                            importText = ""
                            showImportSheet = false
                        },
                        enabled = importText.isNotBlank() && !isImporting,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.inkOnSolid,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(color = colors.inkOnSolid, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("确认导入", fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }

    if (showBatchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBatchSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                InkListRow(
                    title = "校验当前列表书源 (${sources.size})",
                    onClick = {
                        showBatchSheet = false
                        viewModel.checkFilteredSources(context)
                    },
                )
                InkListRow(
                    title = "禁用校验失效书源",
                    onClick = {
                        showBatchSheet = false
                        viewModel.disableFailedSources()
                    },
                )
                InkListRow(
                    title = "书源订阅管理 (${subscriptions.size})",
                    onClick = {
                        showBatchSheet = false
                        showSubSheet = true
                    },
                )
                InkListRow(
                    title = "同步内置精选书源",
                    onClick = {
                        showBatchSheet = false
                        viewModel.syncBuiltinSources()
                    },
                )
                InkListRow(
                    title = "重置为内置精选书源（清空旧源）",
                    onClick = {
                        showBatchSheet = false
                        confirmResetBuiltin = true
                    },
                )
                InkListRow(
                    title = "启用当前列表 (${sources.size})",
                    onClick = {
                        showBatchSheet = false
                        viewModel.setAllFilteredEnabled(true)
                    },
                )
                InkListRow(
                    title = "禁用当前列表 (${sources.size})",
                    onClick = {
                        showBatchSheet = false
                        viewModel.setAllFilteredEnabled(false)
                    },
                )
                InkListRow(
                    title = "从本地 JSON 文件导入",
                    showDivider = false,
                    onClick = {
                        openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                )
            }
        }
    }

    actionSource?.let { part ->
        ModalBottomSheet(
            onDismissRequest = { actionSource = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = part.bookSourceName,
                    color = colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                InkListRow(
                    title = if (part.enabled) "禁用书源" else "启用书源",
                    onClick = {
                        actionSource = null
                        viewModel.toggleEnabled(part)
                    },
                )
                if (part.hasLoginUrl) {
                    InkListRow(
                        title = "登录书源账号",
                        leading = { Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = colors.inkSecondary) },
                        onClick = {
                            actionSource = null
                            context.startActivity(
                                Intent(context, SourceLoginActivity::class.java).apply {
                                    putExtra("type", "bookSource")
                                    putExtra("key", part.bookSourceUrl)
                                },
                            )
                        },
                    )
                }
                if (part.hasExploreUrl) {
                    InkListRow(
                        title = if (part.enabledExplore) "关闭发现" else "开启发现",
                        leading = { Icon(Icons.Outlined.Explore, contentDescription = null, tint = colors.inkSecondary) },
                        onClick = {
                            actionSource = null
                            viewModel.toggleExplore(part)
                        },
                    )
                }
                InkListRow(
                    title = "置顶书源",
                    leading = { Icon(Icons.Outlined.PushPin, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = {
                        actionSource = null
                        viewModel.pinToTop(part)
                    },
                )
                InkListRow(
                    title = "编辑书源规则（高级）",
                    leading = { Icon(Icons.Outlined.Code, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = {
                        actionSource = null
                        context.startActivity(
                            Intent(context, BookSourceEditActivity::class.java).apply {
                                putExtra("sourceUrl", part.bookSourceUrl)
                            },
                        )
                    },
                )
                InkListRow(
                    title = "删除书源",
                    leading = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.inkSecondary) },
                    showDivider = false,
                    onClick = {
                        actionSource = null
                        confirmDeleteSource = part
                    },
                )
            }
        }
    }

    confirmDeleteSource?.let { part ->
        AlertDialog(
            onDismissRequest = { confirmDeleteSource = null },
            containerColor = colors.surface,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSecondary,
            title = { Text("删除书源", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要删除「${part.bookSourceName}」吗？", fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSource = null
                        viewModel.deleteSource(part)
                    },
                ) {
                    Text("删除", color = colors.ink, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSource = null }) {
                    Text("取消", color = colors.inkSecondary)
                }
            },
        )
    }

    if (confirmResetBuiltin) {
        AlertDialog(
            onDismissRequest = { confirmResetBuiltin = false },
            containerColor = colors.surface,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSecondary,
            title = { Text("重置为内置精选书源", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("将清空当前全部书源并恢复为内置精选书源，确定继续吗？", fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetBuiltin = false
                        viewModel.resetToBuiltinSources()
                    },
                ) {
                    Text("重置", color = colors.ink, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetBuiltin = false }) {
                    Text("取消", color = colors.inkSecondary)
                }
            },
        )
    }

    if (showSubSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!isSyncingSub) showSubSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("书源订阅管理", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = subName,
                    onValueChange = { subName = it },
                    placeholder = { Text("订阅名称（可选）", color = colors.inkSecondary, fontSize = 13.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.line,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        cursorColor = colors.ink,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    placeholder = { Text("订阅地址（https://...json）", color = colors.inkSecondary, fontSize = 13.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.line,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        cursorColor = colors.ink,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.addSubscription(subName, subUrl)
                        subName = ""
                        subUrl = ""
                    },
                    enabled = subUrl.isNotBlank() && !isSyncingSub,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.inkOnSolid,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSyncingSub) {
                        CircularProgressIndicator(color = colors.inkOnSolid, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("添加并同步", fontSize = 13.5.sp)
                    }
                }
                if (subscriptions.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                    subscriptions.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.name, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val timeText = if (sub.update > 0) "上次同步 ${dateFormat.format(Date(sub.update))}" else "尚未同步"
                                Text("$timeText · ${sub.url}", color = colors.inkSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                onClick = { viewModel.syncSubscription(sub) },
                                enabled = !isSyncingSub,
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = "同步", tint = colors.ink)
                            }
                            IconButton(
                                onClick = { viewModel.deleteSubscription(sub) },
                                enabled = !isSyncingSub,
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = colors.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
