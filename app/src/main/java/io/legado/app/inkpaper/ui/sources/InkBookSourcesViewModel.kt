package io.legado.app.inkpaper.ui.sources

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourceCheckState
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.RuleSub
import io.legado.app.help.DefaultData
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.source.SourceHelp
import io.legado.app.inkpaper.source.InkRuleSubSync
import io.legado.app.model.CheckSource
import io.legado.app.ui.association.BookSourceImportJson
import io.legado.app.ui.association.parseBookSourceJson
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InkBookSourcesFilter(
    val query: String = "",
    val tab: Int = 0, // 0: 全部, 1: 已启用, 2: 已禁用, 3: 可发现
    val group: String? = null,
)

class InkBookSourcesViewModel : ViewModel() {

    private val _filter = MutableStateFlow(InkBookSourcesFilter())
    val filter: StateFlow<InkBookSourcesFilter> = _filter.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isSyncingSub = MutableStateFlow(false)
    val isSyncingSub: StateFlow<Boolean> = _isSyncingSub.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val subscriptions: StateFlow<List<RuleSub>> = appDb.ruleSubDao.flowAll()
        .map { list -> list.filter { it.type == 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSources: StateFlow<List<BookSourcePart>> = appDb.bookSourceDao.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<String>> = allSources.map { list ->
        list.flatMap { part ->
            part.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toList().orEmpty()
        }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredSources: StateFlow<List<BookSourcePart>> =
        combine(allSources, _filter) { sources, f ->
            sources.filter { part ->
                val matchesTab = when (f.tab) {
                    1 -> part.enabled
                    2 -> !part.enabled
                    3 -> part.enabledExplore && part.hasExploreUrl
                    else -> true
                }
                val matchesGroup = f.group == null ||
                    part.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.contains(f.group) == true
                val matchesQuery = f.query.isBlank() ||
                    part.bookSourceName.contains(f.query, ignoreCase = true) ||
                    part.bookSourceUrl.contains(f.query, ignoreCase = true) ||
                    (part.bookSourceGroup?.contains(f.query, ignoreCase = true) == true)
                matchesTab && matchesGroup && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun selectTab(tab: Int) {
        _filter.update { it.copy(tab = tab) }
    }

    fun selectGroup(group: String?) {
        _filter.update { it.copy(group = group) }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun toggleEnabled(part: BookSourcePart) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.bookSourceDao.enable(part.bookSourceUrl, !part.enabled)
        }
    }

    fun toggleExplore(part: BookSourcePart) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.bookSourceDao.enableExplore(part.bookSourceUrl, !part.enabledExplore)
        }
    }

    fun pinToTop(part: BookSourcePart) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = appDb.bookSourceDao.getBookSource(part.bookSourceUrl) ?: return@launch
            val minOrder = appDb.bookSourceDao.minOrder
            appDb.bookSourceDao.update(source.copy(customOrder = minOrder - 1))
            _statusMessage.value = "已置顶「${part.bookSourceName}」"
        }
    }

    fun deleteSource(part: BookSourcePart) {
        viewModelScope.launch(Dispatchers.IO) {
            SourceHelp.deleteBookSource(part.bookSourceUrl)
            _statusMessage.value = "已删除「${part.bookSourceName}」"
        }
    }

    fun setAllFilteredEnabled(enabled: Boolean) {
        val current = filteredSources.value
        if (current.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            appDb.bookSourceDao.enable(enabled, current)
            _statusMessage.value = if (enabled) {
                "已启用当前 ${current.size} 个书源"
            } else {
                "已禁用当前 ${current.size} 个书源"
            }
        }
    }

    fun checkFilteredSources(context: Context) {
        val targets = filteredSources.value
        if (targets.isEmpty()) {
            _statusMessage.value = "当前列表没有可校验的书源"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CheckSource.start(context, targets, System.currentTimeMillis())
            }.onSuccess {
                _statusMessage.value = "已启动 ${targets.size} 个书源校验，可在通知栏查看进度"
            }.onFailure { err ->
                _statusMessage.value = "启动校验失败：${err.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun disableFailedSources() {
        viewModelScope.launch(Dispatchers.IO) {
            val failed = allSources.value.filter {
                it.enabled && it.checkStatus == BookSourceCheckState.FAILED
            }
            if (failed.isEmpty()) {
                _statusMessage.value = "暂无已启用的失效书源（请先执行书源校验）"
                return@launch
            }
            appDb.bookSourceDao.enable(false, failed)
            _statusMessage.value = "已禁用 ${failed.size} 个校验失效的书源"
        }
    }

    fun syncBuiltinSources() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = DefaultData.importDefaultBookSources(overwriteExisting = true)
            _statusMessage.value = "已同步内置精选书源（$count 个）"
        }
    }

    fun resetToBuiltinSources() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = DefaultData.resetToDefaultBookSources()
            _statusMessage.value = "已重置为内置精选书源（$count 个，已清理旧源）"
        }
    }

    fun addSubscription(name: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.isAbsUrl()) {
            _statusMessage.value = "订阅地址必须以 http:// 或 https:// 开头"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = appDb.ruleSubDao.findByUrl(cleanUrl)
            val sub = existing ?: RuleSub(
                name = name.trim().ifBlank { cleanUrl.substringAfter("://").substringBefore("/") },
                url = cleanUrl,
                type = 0,
                customOrder = appDb.ruleSubDao.maxOrder + 1,
                autoUpdate = true,
                silentUpdate = true,
                update = 0L,
            ).also { appDb.ruleSubDao.insert(it) }
            syncSubscriptionInternal(sub)
        }
    }

    fun syncSubscription(sub: RuleSub) {
        viewModelScope.launch(Dispatchers.IO) {
            syncSubscriptionInternal(sub)
        }
    }

    private suspend fun syncSubscriptionInternal(sub: RuleSub) {
        _isSyncingSub.value = true
        runCatching {
            InkRuleSubSync.syncBookSourceSub(sub, applyChanges = true)
        }.onSuccess { res ->
            val prunedSuffix = if (res.disabledMirrorCount > 0) {
                "，已禁用 ${res.disabledMirrorCount} 个失效旧源"
            } else {
                ""
            }
            _statusMessage.value =
                "订阅「${sub.name}」同步完成：解析 ${res.parsedCount} 个，更新/新增 ${res.updatedCount} 个$prunedSuffix"
        }.onFailure { err ->
            _statusMessage.value = "订阅同步失败：${err.localizedMessage ?: "网络或格式错误"}"
        }
        _isSyncingSub.value = false
    }

    fun deleteSubscription(sub: RuleSub) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.ruleSubDao.delete(sub)
            _statusMessage.value = "已删除订阅「${sub.name}」"
        }
    }

    fun importFromTextOrUrl(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _statusMessage.value = null
            runCatching {
                val content = if (input.isAbsUrl()) {
                    fetchUrlText(input)
                } else {
                    input
                }
                val parsed = parseBookSources(content)
                if (parsed.isEmpty()) error("未解析到有效书源")
                SourceHelp.insertBookSource(*parsed.toTypedArray())
                parsed.size
            }.onSuccess { count ->
                _statusMessage.value = "已导入 $count 个书源"
            }.onFailure { err ->
                _statusMessage.value = "导入失败：${err.localizedMessage ?: "格式无效"}"
            }
            _isImporting.value = false
        }
    }

    private suspend fun fetchUrlText(url: String): String {
        val cleanUrl = if (url.endsWith("#requestWithoutUA")) {
            url.substringBeforeLast("#requestWithoutUA")
        } else {
            url
        }
        return okHttpClient.newCallResponseBody {
            url(cleanUrl)
            if (url.endsWith("#requestWithoutUA")) {
                header(AppConst.UA_NAME, "null")
            }
        }.decompressed().text("utf-8")
    }

    private suspend fun parseBookSources(content: String): List<BookSource> {
        return when (val parsed = parseBookSourceJson(content.trim(), allowSourceUrls = true)) {
            is BookSourceImportJson.Sources -> parsed.items
            is BookSourceImportJson.SourceUrls -> {
                val all = mutableListOf<BookSource>()
                for (subUrl in parsed.items) {
                    val subContent = fetchUrlText(subUrl)
                    when (val subParsed = parseBookSourceJson(subContent.trim(), allowSourceUrls = false)) {
                        is BookSourceImportJson.Sources -> all.addAll(subParsed.items)
                        is BookSourceImportJson.SourceUrls -> Unit
                    }
                }
                all
            }
        }
    }
}
