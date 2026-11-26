package io.legado.app.inkpaper.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InkExploreUiState(
    val sourceName: String = "",
    val categories: List<ExploreKind> = emptyList(),
    val selectedIndex: Int = 0,
    val books: List<SearchBook> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

class InkExploreViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InkExploreUiState())
    val uiState: StateFlow<InkExploreUiState> = _uiState.asStateFlow()

    private var currentSourceUrl: String = ""
    private var bookSource: BookSource? = null
    private var page = 1
    private var loadJob: Job? = null

    fun init(sourceUrl: String) {
        if (currentSourceUrl == sourceUrl && bookSource != null) return
        currentSourceUrl = sourceUrl
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            if (source == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "未找到该书源")
                }
                return@launch
            }
            bookSource = source
            val kinds = runCatching { source.exploreKinds() }
                .getOrDefault(emptyList())
                .filter { !it.url.isNullOrBlank() && it.title.isNotBlank() }

            _uiState.update {
                it.copy(
                    sourceName = source.bookSourceName,
                    categories = kinds,
                    selectedIndex = 0,
                )
            }
            if (kinds.isEmpty()) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "该书源暂无发现分类")
                }
            } else {
                loadCategoryPage(0, reset = true)
            }
        }
    }

    fun selectCategory(index: Int) {
        if (index == _uiState.value.selectedIndex && _uiState.value.books.isNotEmpty()) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            loadCategoryPage(index, reset = true)
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            loadCategoryPage(state.selectedIndex, reset = false)
        }
    }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            loadCategoryPage(_uiState.value.selectedIndex, reset = true)
        }
    }

    private suspend fun loadCategoryPage(index: Int, reset: Boolean) {
        val source = bookSource ?: return
        val kind = _uiState.value.categories.getOrNull(index) ?: return
        val url = kind.url ?: return
        if (reset) {
            page = 1
            _uiState.update {
                it.copy(
                    selectedIndex = index,
                    isLoading = true,
                    isLoadingMore = false,
                    books = emptyList(),
                    errorMessage = null,
                )
            }
        } else {
            page += 1
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
        }

        runCatching {
            WebBook.exploreBookAwait(source, url, page)
        }.onSuccess { list ->
            list.forEach { it.releaseHtmlData() }
            if (list.isNotEmpty()) {
                appDb.searchBookDao.insert(*list.toTypedArray())
            }
            _uiState.update { state ->
                val merged = if (reset) list else (state.books + list).distinctBy { it.bookUrl }
                state.copy(
                    books = merged,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = list.isNotEmpty(),
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            if (!reset) page = (page - 1).coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = error.localizedMessage ?: "加载失败",
                )
            }
        }
    }
}
