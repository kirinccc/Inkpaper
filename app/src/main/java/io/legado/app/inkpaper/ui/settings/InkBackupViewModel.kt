package io.legado.app.inkpaper.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.Restore
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

data class InkBackupUiState(
    val webDavUrl: String = appCtx.getPrefString(PreferKey.webDavUrl).orEmpty().ifBlank { "https://dav.jianguoyun.com/dav/" },
    val account: String = appCtx.getPrefString(PreferKey.webDavAccount).orEmpty(),
    val password: String = appCtx.getPrefString(PreferKey.webDavPassword).orEmpty(),
    val subDir: String = appCtx.getPrefString(PreferKey.webDavDir).orEmpty().ifBlank { "legado" },
    val autoBackupWebDav: Boolean = appCtx.getPrefBoolean(PreferKey.autoBackupWebDav, true),
    val syncBookProgress: Boolean = appCtx.getPrefBoolean(PreferKey.syncBookProgress, true),
    val webDavBookAutoUpload: Boolean = appCtx.getPrefBoolean(PreferKey.webDavBookAutoUpload, false),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val remoteBackups: List<String> = emptyList(),
    val showRestoreSheet: Boolean = false,
)

class InkBackupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InkBackupUiState())
    val uiState: StateFlow<InkBackupUiState> = _uiState.asStateFlow()

    fun updateUrl(value: String) = _uiState.update { it.copy(webDavUrl = value) }
    fun updateAccount(value: String) = _uiState.update { it.copy(account = value) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }
    fun updateSubDir(value: String) = _uiState.update { it.copy(subDir = value) }

    fun setAutoBackupWebDav(enabled: Boolean) {
        appCtx.putPrefBoolean(PreferKey.autoBackupWebDav, enabled)
        _uiState.update { it.copy(autoBackupWebDav = enabled) }
    }

    fun setSyncBookProgress(enabled: Boolean) {
        appCtx.putPrefBoolean(PreferKey.syncBookProgress, enabled)
        _uiState.update { it.copy(syncBookProgress = enabled) }
    }

    fun setWebDavBookAutoUpload(enabled: Boolean) {
        appCtx.putPrefBoolean(PreferKey.webDavBookAutoUpload, enabled)
        _uiState.update { it.copy(webDavBookAutoUpload = enabled) }
    }

    fun closeRestoreSheet() {
        _uiState.update { it.copy(showRestoreSheet = false) }
    }

    private fun persistWebDavConfig() {
        val state = _uiState.value
        appCtx.putPrefString(PreferKey.webDavUrl, state.webDavUrl.trim())
        appCtx.putPrefString(PreferKey.webDavAccount, state.account.trim())
        appCtx.putPrefString(PreferKey.webDavPassword, state.password.trim())
        appCtx.putPrefString(PreferKey.webDavDir, state.subDir.trim())
    }

    fun saveAndTestWebDav() {
        persistWebDavConfig()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            AppWebDav.upConfig()
            val msg = if (AppWebDav.isOk) {
                "WebDAV 连接成功"
            } else {
                "WebDAV 未连接，请核对服务器地址、账号与应用密码"
            }
            _uiState.update { it.copy(isBusy = false, statusMessage = msg) }
        }
    }

    fun backupToWebDavNow(context: Context) {
        persistWebDavConfig()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = "正在备份并上传...") }
            runCatching {
                AppWebDav.upConfig()
                if (!AppWebDav.isOk) error("请先配置并连接 WebDAV")
                Backup.backupLocked(context, AppConfig.backupPath, uploadWebDav = true)
            }.onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = "已成功备份并上传到 WebDAV") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "备份失败：${err.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }

    fun fetchRemoteBackups() {
        persistWebDavConfig()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = "正在拉取云端备份列表...") }
            runCatching {
                AppWebDav.upConfig()
                if (!AppWebDav.isOk) error("请先配置并连接 WebDAV")
                AppWebDav.getBackupNames()
            }.onSuccess { names ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        remoteBackups = names,
                        showRestoreSheet = true,
                        statusMessage = if (names.isEmpty()) "云端暂无备份文件" else null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "获取列表失败：${err.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }

    fun restoreFromWebDav(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, showRestoreSheet = false, statusMessage = "正在恢复 $name...") }
            runCatching {
                AppWebDav.restoreWebDav(name)
            }.onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = "已成功恢复：$name") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "恢复失败：${err.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }

    fun backupToLocalDir(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = "正在导出本地备份...") }
            runCatching {
                Backup.backupLocked(context, treeUri.toString(), uploadWebDav = false)
            }.onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = "本地备份已导出") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "导出失败：${err.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }

    fun restoreFromLocalUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, statusMessage = "正在从本地文件恢复...") }
            runCatching {
                Restore.restore(context, uri)
            }.onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = "本地备份恢复完成") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "恢复失败：${err.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }
}
