package io.legado.app.inkpaper.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.source.SourceHelp
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

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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
