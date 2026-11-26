package io.legado.app.inkpaper.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.inkpaper.design.InkListRow
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkLocalImportSheet(
    onDismiss: () -> Unit,
) {
    val colors = InkTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        onDismiss()
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                var imported = 0
                for (uri in uris) {
                    runCatching { LocalBook.importFile(uri) }.onSuccess { imported++ }
                }
                context.toastOnUi(
                    if (imported > 0) "已导入 $imported 本本地书籍" else "未导入有效书籍文件",
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "导入本地书籍",
                color = colors.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            InkListRow(
                title = "快速选择文件导入",
                subtitle = "支持 TXT / EPUB / MOBI / PDF",
                leading = { Icon(Icons.Outlined.FileOpen, contentDescription = null, tint = colors.inkSecondary) },
                onClick = {
                    pickFilesLauncher.launch(arrayOf("*/*"))
                },
            )
            InkListRow(
                title = "按文件夹扫描导入",
                subtitle = "浏览目录并批量选择本地书籍",
                leading = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = colors.inkSecondary) },
                showDivider = false,
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(context, ImportBookActivity::class.java))
                },
            )
        }
    }
}
