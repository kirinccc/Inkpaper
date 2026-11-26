package io.legado.app.inkpaper.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.inkpaper.design.InkFilterChip
import io.legado.app.inkpaper.design.InkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkAddBookUrlSheet(
    initialInput: String = "",
    isLoading: Boolean,
    errorMessage: String?,
    showOpenSourcesHint: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onOpenBookSources: () -> Unit,
) {
    val colors = InkTheme.colors
    val clipboard = LocalClipboardManager.current
    var inputText by remember(initialInput) { mutableStateOf(initialInput) }
    var clipboardCandidate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val clip = runCatching { clipboard.getText()?.text }.getOrNull().orEmpty()
        if (clip.isNotBlank() && InkSearchQueryHelper.extractBookUrlOrId(clip) != null) {
            clipboardCandidate = clip.trim()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
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
            Text(
                text = "通过链接或 ID 添加书籍",
                color = colors.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "支持粘贴书籍网址、分享口令，或 19 位番茄书籍 ID",
                color = colors.inkSecondary,
                fontSize = 12.5.sp,
            )
            clipboardCandidate?.let { candidate ->
                if (inputText != candidate) {
                    InkFilterChip(
                        text = "填入剪贴板中的链接/ID",
                        selected = false,
                        onClick = { inputText = candidate },
                    )
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "例如：7285555567827880975 或 https://...",
                        color = colors.inkSecondary,
                        fontSize = 13.5.sp,
                    )
                },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.ink,
                    unfocusedBorderColor = colors.line,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    cursorColor = colors.ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!errorMessage.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = errorMessage,
                        color = colors.inkSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                    if (showOpenSourcesHint) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenBookSources()
                            },
                        ) {
                            Text(
                                text = "前往书源管理启用/登录",
                                color = colors.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消", fontSize = 13.5.sp)
                }
                Button(
                    onClick = { onSubmit(inputText) },
                    enabled = inputText.isNotBlank() && !isLoading,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.inkOnSolid,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = colors.inkOnSolid,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text("解析并查看", fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}
