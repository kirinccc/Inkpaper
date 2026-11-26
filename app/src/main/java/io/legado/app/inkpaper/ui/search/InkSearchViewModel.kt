package io.legado.app.inkpaper.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InkSearchUiState(
    val query: String = "",
    val hasSubmitted: Boolean = false,
    val isSearching: Boolean = false,
    val searchedCount: Int = 0,
    val totalSources: Int = 0,
    val results: List<SearchBook> = emptyList(),
    val errorMessage: String? = null,
)

class InkSearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InkSearchUiState())
    val uiState: StateFlow<InkSearchUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SearchKeyword>> = appDb.searchKeywordDao.flowByUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val searchModel = SearchModel(
        scope = viewModelScope,
        callBack = object : SearchModel.CallBack {
            override fun getSearchScope(): SearchScope = SearchScope("")

            override fun onSearchStart() {
                _uiState.update {
                    it.copy(
                        isSearching = true,
                        searchedCount = 0,
                        results = emptyList(),
                        errorMessage = null,
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
                _uiState.update { it.copy(isSearching = false) }
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
                it.copy(query = newQuery, errorMessage = null)
            } else {
                it.copy(query = newQuery)
            }
        }
    }

    fun submitSearch(rawKeyword: String = _uiState.value.query) {
        val keyword = rawKeyword.trim()
        if (keyword.isEmpty()) return
        _uiState.update {
            it.copy(
                query = keyword,
                hasSubmitted = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = appDb.searchKeywordDao.get(keyword)
            val entry = if (existing != null) {
                existing.copy(usage = existing.usage + 1, lastUseTime = System.currentTimeMillis())
            } else {
                SearchKeyword(word = keyword, usage = 1, lastUseTime = System.currentTimeMillis())
            }
            appDb.searchKeywordDao.insert(entry)
        }
        searchModel.search(System.currentTimeMillis(), keyword)
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
        searchModel.close()
        super.onCleared()
    }
}
