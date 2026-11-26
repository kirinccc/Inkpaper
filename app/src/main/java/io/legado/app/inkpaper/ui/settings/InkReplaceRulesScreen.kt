package io.legado.app.inkpaper.ui.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkReplaceRulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InkReplaceRulesViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val rules by viewModel.filteredRules.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var editingRule by remember { mutableStateOf<ReplaceRule?>(null) }
    var showImportSheet by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            if (content.isNotBlank()) {
                showImportSheet = false
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
            title = "替换净化",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showImportSheet = true }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "导入规则", tint = colors.ink)
                }
                IconButton(onClick = { editingRule = ReplaceRule() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建规则", tint = colors.ink)
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
                if (query.isEmpty()) {
                    Text("搜索规则名称或匹配内容", color = colors.inkSecondary, fontSize = 13.5.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::updateQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 13.5.sp),
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
                        .clickable { viewModel.updateQuery("") },
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkFilterChip("全部", tab == 0, { viewModel.selectTab(0) })
            InkFilterChip("已启用", tab == 1, { viewModel.selectTab(1) })
            InkFilterChip("已禁用", tab == 2, { viewModel.selectTab(2) })
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

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.line)

        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InkEmptyState(
                    title = "暂无替换规则",
                    subtitle = "点击右上角 + 新建净化规则，或导入 JSON 规则集",
                    actionLabel = "新建规则",
                    onAction = { editingRule = ReplaceRule() },
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                items(rules, key = { it.id }) { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRule = rule }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name.ifBlank { rule.pattern },
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val preview = "${rule.pattern} → ${rule.replacement.ifEmpty { "(删除)" }}"
                            Text(
                                text = preview,
                                color = colors.inkSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        InkSwitch(
                            checked = rule.isEnabled,
                            onCheckedChange = { viewModel.toggleEnabled(rule) },
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

    editingRule?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        var group by remember(target.id) { mutableStateOf(target.group.orEmpty()) }
        var pattern by remember(target.id) { mutableStateOf(target.pattern) }
        var replacement by remember(target.id) { mutableStateOf(target.replacement) }
        var scopeText by remember(target.id) { mutableStateOf(target.scope.orEmpty()) }
        var isRegex by remember(target.id) { mutableStateOf(target.isRegex) }
        var scopeContent by remember(target.id) { mutableStateOf(target.scopeContent) }
        var scopeTitle by remember(target.id) { mutableStateOf(target.scopeTitle) }
        var errorText by remember(target.id) { mutableStateOf<String?>(null) }

        val tfColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.ink,
            unfocusedBorderColor = colors.line,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            cursorColor = colors.ink,
        )

        ModalBottomSheet(
            onDismissRequest = { editingRule = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (target.pattern.isEmpty() && target.name.isEmpty()) "新建替换规则" else "编辑替换规则",
                    color = colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称", fontSize = 12.sp) },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配规则", fontSize = 12.sp) },
                    minLines = 2,
                    maxLines = 4,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为（留空即删除）", fontSize = 12.sp) },
                    minLines = 1,
                    maxLines = 3,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text("分组", fontSize = 12.sp) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = scopeText,
                        onValueChange = { scopeText = it },
                        label = { Text("作用范围（留空通用）", fontSize = 12.sp) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                    )
                }

                InkCardGroup {
                    InkListRow(
                        title = "使用正则表达式",
                        trailing = { InkSwitch(checked = isRegex, onCheckedChange = { isRegex = it }) },
                    )
                    InkListRow(
                        title = "作用于正文",
                        trailing = { InkSwitch(checked = scopeContent, onCheckedChange = { scopeContent = it }) },
                    )
                    InkListRow(
                        title = "作用于标题",
                        showDivider = false,
                        trailing = { InkSwitch(checked = scopeTitle, onCheckedChange = { scopeTitle = it }) },
                    )
                }

                if (!errorText.isNullOrBlank()) {
                    Text(errorText.orEmpty(), color = colors.inkSecondary, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (target.pattern.isNotEmpty() || target.name.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteRule(target)
                                editingRule = null
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkSecondary),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("删除", fontSize = 13.5.sp)
                        }
                    }
                    Button(
                        onClick = {
                            val updated = target.copy(
                                name = name.trim(),
                                group = group.trim().takeIf { it.isNotEmpty() },
                                pattern = pattern,
                                replacement = replacement,
                                scope = scopeText.trim().takeIf { it.isNotEmpty() },
                                isRegex = isRegex,
                                scopeContent = scopeContent,
                                scopeTitle = scopeTitle,
                            )
                            viewModel.saveRule(updated) { ok, err ->
                                if (ok) editingRule = null else errorText = err
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.inkOnSolid,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("保存", fontSize = 13.5.sp)
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
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
                Text("导入替换规则", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("粘贴规则 URL 或 JSON 内容", color = colors.inkSecondary, fontSize = 13.5.sp) },
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
                        enabled = importText.isNotBlank(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.inkOnSolid,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("确认导入", fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}
