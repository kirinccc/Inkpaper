package io.legado.app.inkpaper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.ReplaceAnalyzer
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InkReplaceRulesViewModel : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(0) // 0: 全部, 1: 已启用, 2: 已禁用
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val allRules: StateFlow<List<ReplaceRule>> = appDb.replaceRuleDao.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredRules: StateFlow<List<ReplaceRule>> =
        combine(allRules, _query, _tab) { list, q, t ->
            list.filter { rule ->
                val matchesTab = when (t) {
                    1 -> rule.isEnabled
                    2 -> !rule.isEnabled
                    else -> true
                }
                val matchesQuery = q.isBlank() ||
                    rule.name.contains(q, ignoreCase = true) ||
                    rule.pattern.contains(q, ignoreCase = true) ||
                    (rule.group?.contains(q, ignoreCase = true) == true)
                matchesTab && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun selectTab(newTab: Int) {
        _tab.value = newTab
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun toggleEnabled(rule: ReplaceRule) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.replaceRuleDao.update(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun saveRule(rule: ReplaceRule, onResult: (Boolean, String?) -> Unit) {
        if (!rule.isValid()) {
            onResult(false, "匹配规则无效或正则语法有误")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (rule.order == Int.MIN_VALUE) {
                rule.copy(order = appDb.replaceRuleDao.maxOrder + 1)
            } else {
                rule
            }
            appDb.replaceRuleDao.insert(target)
            _statusMessage.value = "已保存「${target.name.ifBlank { target.pattern }}」"
        }
        onResult(true, null)
    }

    fun deleteRule(rule: ReplaceRule) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.replaceRuleDao.delete(rule)
            _statusMessage.value = "已删除规则"
        }
    }

    fun importFromTextOrUrl(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = if (input.isAbsUrl()) {
                    okHttpClient.newCallResponseBody { url(input) }.decompressed().text("utf-8").trim()
                } else {
                    input
                }
                val rules = when {
                    text.isJsonArray() -> ReplaceAnalyzer.jsonToReplaceRules(text).getOrThrow()
                    text.isJsonObject() -> listOf(ReplaceAnalyzer.jsonToReplaceRule(text).getOrThrow())
                    else -> error("仅支持 JSON 格式或规则链接")
                }
                if (rules.isEmpty()) error("未解析到替换规则")
                appDb.replaceRuleDao.insert(*rules.toTypedArray())
                rules.size
            }.onSuccess { count ->
                _statusMessage.value = "已导入 $count 条替换规则"
            }.onFailure { err ->
                _statusMessage.value = "导入失败：${err.localizedMessage ?: "格式错误"}"
            }
        }
    }
}
