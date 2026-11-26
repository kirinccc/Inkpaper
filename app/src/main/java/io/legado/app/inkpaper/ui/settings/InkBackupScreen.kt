package io.legado.app.inkpaper.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.inkpaper.design.InkCardGroup
import io.legado.app.inkpaper.design.InkEmptyState
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkSubTopBar
import io.legado.app.inkpaper.design.InkSwitch
import io.legado.app.inkpaper.design.InkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkBackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InkBackupViewModel = viewModel(),
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRestoreName by remember { mutableStateOf<String?>(null) }

    val pickLocalDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.backupToLocalDir(context, uri)
        }
    }

    val pickRestoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromLocalUri(context, uri)
        }
    }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.ink,
        unfocusedBorderColor = colors.line,
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        cursorColor = colors.ink,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        InkSubTopBar(title = "备份与恢复", onBack = onBack)

        if (state.isBusy) {
            LinearProgressIndicator(
                color = colors.ink,
                trackColor = colors.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.statusMessage.isNullOrBlank()) {
                Text(
                    text = state.statusMessage.orEmpty(),
                    color = colors.ink,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSubtle)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { viewModel.backupToWebDavNow(context) },
                    enabled = !state.isBusy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.inkOnSolid,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text("立即备份", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = { viewModel.fetchRemoteBackups() },
                    enabled = !state.isBusy,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text("云端恢复", fontSize = 14.sp)
                }
            }

            InkCardGroup {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("WebDAV 服务器配置", color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.webDavUrl,
                        onValueChange = viewModel::updateUrl,
                        label = { Text("服务器地址", fontSize = 12.sp) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.account,
                        onValueChange = viewModel::updateAccount,
                        label = { Text("账号", fontSize = 12.sp) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text("应用密码", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.subDir,
                        onValueChange = viewModel::updateSubDir,
                        label = { Text("子目录", fontSize = 12.sp) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    OutlinedButton(
                        onClick = viewModel::saveAndTestWebDav,
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, colors.line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存并测试连接", fontSize = 13.5.sp)
                    }
                }
            }

            InkCardGroup {
                InkListRow(
                    title = "自动备份到 WebDAV",
                    trailing = {
                        InkSwitch(
                            checked = state.autoBackupWebDav,
                            onCheckedChange = viewModel::setAutoBackupWebDav,
                        )
                    },
                )
                InkListRow(
                    title = "同步阅读进度",
                    trailing = {
                        InkSwitch(
                            checked = state.syncBookProgress,
                            onCheckedChange = viewModel::setSyncBookProgress,
                        )
                    },
                )
                InkListRow(
                    title = "本地书籍自动上传同步",
                    showDivider = false,
                    trailing = {
                        InkSwitch(
                            checked = state.webDavBookAutoUpload,
                            onCheckedChange = viewModel::setWebDavBookAutoUpload,
                        )
                    },
                )
            }

            InkCardGroup {
                InkListRow(
                    title = "导出到本地文件夹",
                    leading = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = colors.inkSecondary) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    onClick = { pickLocalDirLauncher.launch(null) },
                )
                InkListRow(
                    title = "从本地备份文件恢复",
                    leading = { Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = colors.inkSecondary) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkSecondary) },
                    showDivider = false,
                    onClick = { pickRestoreFileLauncher.launch(arrayOf("application/zip", "*/*")) },
                )
            }
        }
    }

    if (state.showRestoreSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeRestoreSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                Text(
                    text = "选择云端备份",
                    color = colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                if (state.remoteBackups.isEmpty()) {
                    InkEmptyState(
                        title = "暂无云端备份",
                        subtitle = "请先点击「立即备份」上传一份配置",
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(state.remoteBackups) { name ->
                            InkListRow(
                                title = name,
                                onClick = { confirmRestoreName = name },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmRestoreName?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmRestoreName = null },
            containerColor = colors.surface,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSecondary,
            title = { Text("恢复备份", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要从「$name」恢复数据吗？当前本地书架与配置将被覆盖或合并。", fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestoreName = null
                        viewModel.restoreFromWebDav(name)
                    },
                ) {
                    Text("恢复", color = colors.ink, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreName = null }) {
                    Text("取消", color = colors.inkSecondary)
                }
            },
        )
    }
}
