package io.legado.app.inkpaper.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

data class InkSearchUiState(
    val query: String = "",
    val hasSubmitted: Boolean = false,
    val isSearching: Boolean = false,
    val searchedCount: Int = 0,
    val totalSources: Int = 0,
    val results: List<SearchBook> = emptyList(),
    val errorMessage: String? = null,
    val selectedGroup: String? = LocalConfig.lastSearchGroup,
    val precisionSearch: Boolean = appCtx.getPrefBoolean(PreferKey.precisionSearch),
    val fallbackHints: List<String> = emptyList(),
    val isAddingUrl: Boolean = false,
    val urlAddError: String? = null,
    val urlAddNeedEnableSource: Boolean = false,
)

class InkSearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InkSearchUiState())
    val uiState: StateFlow<InkSearchUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SearchKeyword>> = appDb.searchKeywordDao.flowByUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableGroups: StateFlow<List<String>> = appDb.bookSourceDao.flowEnabled()
        .map { list ->
            list.flatMap { part ->
                part.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toList().orEmpty()
            }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val disabledBackupCount: StateFlow<Int> = appDb.bookSourceDao.flowDisabled()
        .map { list ->
            val backup = list.count { it.bookSourceGroup?.contains("长尾备用") == true }
            if (backup > 0) backup else list.count { !it.hasLoginUrl }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val searchModel = SearchModel(
        scope = viewModelScope,
        callBack = object : SearchModel.CallBack {
            override fun getSearchScope(): SearchScope =
                SearchScope(_uiState.value.selectedGroup.orEmpty())

            override fun onSearchStart() {
                _uiState.update {
                    it.copy(
                        isSearching = true,
                        searchedCount = 0,
                        results = emptyList(),
                        errorMessage = null,
                        fallbackHints = emptyList(),
                    )
                }
            }

            override fun onSearchProgress(searched: Int, total: Int) {
                _uiState.update {
                    it.copy(searchedCount = searched, totalSources = total)
                }
            }

            override fun onSearchSuccess(searchBooks: List<SearchBook>) {
                val snapshot = ArrayList(searchBooks)
                _uiState.update { it.copy(results = snapshot) }
            }

            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                _uiState.update { state ->
                    val hints = if (state.results.size < 3) {
                        InkSearchQueryHelper.generateFallbackHints(state.query)
                    } else {
                        emptyList()
                    }
                    state.copy(
                        isSearching = false,
                        fallbackHints = hints,
                    )
                }
            }

            override fun onSearchCancel(exception: Throwable?) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = exception?.localizedMessage,
                    )
                }
            }
        },
    )

    fun updateQuery(newQuery: String) {
        _uiState.update {
            if (newQuery.isBlank()) {
                it.copy(query = newQuery, errorMessage = null, fallbackHints = emptyList())
            } else {
                it.copy(query = newQuery)
            }
        }
    }

    fun submitSearch(rawKeyword: String = _uiState.value.query) {
        val displayKeyword = rawKeyword.trim()
        val normalized = InkSearchQueryHelper.normalizeQuery(displayKeyword)
        if (normalized.isEmpty()) return
        _uiState.update {
            it.copy(
                query = displayKeyword,
                hasSubmitted = true,
                errorMessage = null,
                fallbackHints = emptyList(),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = appDb.searchKeywordDao.get(displayKeyword)
            val entry = if (existing != null) {
                existing.copy(usage = existing.usage + 1, lastUseTime = System.currentTimeMillis())
            } else {
                SearchKeyword(word = displayKeyword, usage = 1, lastUseTime = System.currentTimeMillis())
            }
            appDb.searchKeywordDao.insert(entry)
        }
        searchModel.search(System.currentTimeMillis(), normalized)
    }

    fun selectGroup(group: String?) {
        LocalConfig.lastSearchGroup = group
        _uiState.update { it.copy(selectedGroup = group) }
        val current = _uiState.value
        if (current.hasSubmitted && current.query.isNotBlank()) {
            submitSearch(current.query)
        }
    }

    fun togglePrecisionSearch() {
        val next = !_uiState.value.precisionSearch
        appCtx.putPrefBoolean(PreferKey.precisionSearch, next)
        _uiState.update { it.copy(precisionSearch = next) }
        val current = _uiState.value
        if (current.hasSubmitted && current.query.isNotBlank()) {
            submitSearch(current.query)
        }
    }

    fun enableBackupSourcesAndRetry() {
        val currentQuery = _uiState.value.query
        if (currentQuery.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val disabled = appDb.bookSourceDao.allDisabled
            val backupTargets = disabled.filter { it.bookSourceGroup?.contains("长尾备用") == true }
                .ifEmpty {
                    disabled.filter {
                        it.loginUrl.isNullOrBlank() && it.loginUi.isNullOrBlank()
                    }
                }
            if (backupTargets.isNotEmpty()) {
                val updated = backupTargets.map { it.copy(enabled = true) }
                appDb.bookSourceDao.update(*updated.toTypedArray())
            }
            withContext(Dispatchers.Main) {
                LocalConfig.lastSearchGroup = null
                _uiState.update { it.copy(selectedGroup = null) }
                submitSearch(currentQuery)
            }
        }
    }

    fun clearUrlAddError() {
        _uiState.update {
            it.copy(urlAddError = null, urlAddNeedEnableSource = false)
        }
    }

    fun addBookByUrlOrId(rawInput: String, onResolved: (Book) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(isAddingUrl = true, urlAddError = null, urlAddNeedEnableSource = false)
            }
            when (val result = InkBookUrlResolver.resolve(rawInput)) {
                is InkBookUrlResolveResult.Success -> {
                    _uiState.update { it.copy(isAddingUrl = false) }
                    withContext(Dispatchers.Main) { onResolved(result.book) }
                }
                is InkBookUrlResolveResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isAddingUrl = false,
                            urlAddError = result.message,
                            urlAddNeedEnableSource = result.needEnableSource,
                        )
                    }
                }
            }
        }
    }

    fun stopSearch() {
        searchModel.cancelSearch()
    }

    fun deleteHistoryItem(keyword: SearchKeyword) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.searchKeywordDao.delete(keyword)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.searchKeywordDao.deleteAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchModel.close()
    }
}
