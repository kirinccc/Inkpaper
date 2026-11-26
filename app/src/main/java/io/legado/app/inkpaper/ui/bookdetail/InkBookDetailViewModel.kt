package io.legado.app.inkpaper.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.addType
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.removeType
import io.legado.app.inkpaper.navigation.InkNavCache
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InkBookDetailUiState(
    val book: Book? = null,
    val inBookshelf: Boolean = false,
    val chapters: List<BookChapter> = emptyList(),
    val isLoadingInfo: Boolean = false,
    val isLoadingToc: Boolean = false,
    val errorMessage: String? = null,
)

class InkBookDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InkBookDetailUiState())
    val uiState: StateFlow<InkBookDetailUiState> = _uiState.asStateFlow()

    private var loadedKey: String? = null

    fun load(bookKey: String) {
        if (loadedKey == bookKey && _uiState.value.book != null) return
        loadedKey = bookKey
        viewModelScope.launch(Dispatchers.IO) {
            val cachedBook = InkNavCache.getBook(bookKey)
            val cachedSearch = InkNavCache.getSearchBook(bookKey)
            val dbBook = appDb.bookDao.getBook(bookKey)
                ?: cachedBook?.let { appDb.bookDao.getBook(it.name, it.author) }
                ?: cachedSearch?.let { appDb.bookDao.getBook(it.name, it.author) }

            val initialBook = dbBook
                ?: cachedBook
                ?: cachedSearch?.toBook()
                ?: appDb.searchBookDao.getSearchBook(bookKey)?.toBook()

            if (initialBook == null) {
                _uiState.update { it.copy(errorMessage = "未找到书籍信息") }
                return@launch
            }

            val inShelf = dbBook != null && !dbBook.isNotShelf
            val cachedChapters = appDb.bookChapterDao.getChapterList(initialBook.bookUrl)
            _uiState.update {
                it.copy(
                    book = initialBook,
                    inBookshelf = inShelf,
                    chapters = cachedChapters,
                    errorMessage = null,
                )
            }

            if (!initialBook.isLocal) {
                refreshOnlineInfoAndToc(initialBook, inShelf, cachedChapters.isEmpty())
            }
        }
    }

    private suspend fun refreshOnlineInfoAndToc(
        book: Book,
        inShelf: Boolean,
        shouldLoadToc: Boolean,
    ) {
        val source = appDb.bookSourceDao.getBookSource(book.origin) ?: return
        var workingBook = book.copy()

        if (workingBook.tocUrl.isBlank() || workingBook.intro.isNullOrBlank()) {
            _uiState.update { it.copy(isLoadingInfo = true) }
            runCatching {
                WebBook.getBookInfoAwait(source, workingBook)
            }.onSuccess { updated ->
                workingBook = updated
                if (inShelf) {
                    appDb.bookDao.update(workingBook)
                }
                _uiState.update { it.copy(book = workingBook, isLoadingInfo = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingInfo = false) }
            }
        }

        if (shouldLoadToc && workingBook.tocUrl.isNotBlank()) {
            _uiState.update { it.copy(isLoadingToc = true) }
            runCatching {
                WebBook.getChapterListAwait(source, workingBook).getOrThrow()
            }.onSuccess { toc ->
                workingBook.totalChapterNum = toc.size
                workingBook.latestChapterTitle = toc.lastOrNull()?.title ?: workingBook.latestChapterTitle
                if (inShelf) {
                    appDb.bookDao.update(workingBook)
                    appDb.bookChapterDao.delByBook(workingBook.bookUrl)
                    appDb.bookChapterDao.insert(*toc.toTypedArray())
                }
                _uiState.update {
                    it.copy(
                        book = workingBook,
                        chapters = toc,
                        isLoadingToc = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingToc = false,
                        errorMessage = error.localizedMessage,
                    )
                }
            }
        }
    }

    fun toggleBookshelf() {
        val current = _uiState.value.book ?: return
        val currentlyInShelf = _uiState.value.inBookshelf
        val chapters = _uiState.value.chapters
        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyInShelf) {
                appDb.bookDao.delete(current)
                _uiState.update { it.copy(inBookshelf = false) }
            } else {
                val toSave = current.copy().apply {
                    removeType(BookType.notShelf)
                }
                appDb.bookDao.insert(toSave)
                if (chapters.isNotEmpty()) {
                    appDb.bookChapterDao.delByBook(toSave.bookUrl)
                    appDb.bookChapterDao.insert(*chapters.toTypedArray())
                }
                _uiState.update { it.copy(book = toSave, inBookshelf = true) }
            }
        }
    }

    fun prepareForReading(
        chapterIndex: Int? = null,
        onReady: (Book) -> Unit,
    ) {
        val current = _uiState.value.book ?: return
        val inShelf = _uiState.value.inBookshelf
        val chapters = _uiState.value.chapters
        viewModelScope.launch(Dispatchers.IO) {
            val targetBook = current.copy().apply {
                if (!inShelf) {
                    addType(BookType.notShelf)
                } else {
                    removeType(BookType.notShelf)
                }
                if (chapterIndex != null && chapterIndex in chapters.indices) {
                    durChapterIndex = chapterIndex
                    durChapterTitle = chapters[chapterIndex].title
                    durChapterPos = 0
                }
            }
            appDb.bookDao.insert(targetBook)
            if (chapters.isNotEmpty()) {
                val existing = appDb.bookChapterDao.getChapterList(targetBook.bookUrl)
                if (existing.isEmpty()) {
                    appDb.bookChapterDao.insert(*chapters.toTypedArray())
                }
            }
            _uiState.update { it.copy(book = targetBook) }
            withContext(Dispatchers.Main) {
                onReady(targetBook)
            }
        }
    }
}
