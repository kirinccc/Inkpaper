package io.legado.app.inkpaper.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ShelfFilter {
    data object All : ShelfFilter
    data object Reading : ShelfFilter
    data object Finished : ShelfFilter
    data object Local : ShelfFilter
    data class Group(val group: BookGroup) : ShelfFilter
}

class InkBookshelfViewModel : ViewModel() {

    private val _selectedFilter = MutableStateFlow<ShelfFilter>(ShelfFilter.All)
    val selectedFilter: StateFlow<ShelfFilter> = _selectedFilter.asStateFlow()

    val allBooks: StateFlow<List<Book>> = appDb.bookDao.flowAll()
        .map { list ->
            list.filterNot { it.isNotShelf }
                .sortedWith(
                    compareBy<Book> { it.order >= 0 }
                        .thenByDescending { it.durChapterTime },
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userGroups: StateFlow<List<BookGroup>> = appDb.bookGroupDao.flowSelect()
        .map { list -> list.filter { it.groupId > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredBooks: StateFlow<List<Book>> = combine(allBooks, _selectedFilter) { books, filter ->
        when (filter) {
            ShelfFilter.All -> books
            ShelfFilter.Reading -> books.filter {
                it.durChapterIndex > 0 && (it.totalChapterNum <= 0 || it.durChapterIndex + 1 < it.totalChapterNum)
            }
            ShelfFilter.Finished -> books.filter {
                it.totalChapterNum > 0 && it.durChapterIndex + 1 >= it.totalChapterNum
            }
            ShelfFilter.Local -> books.filter { it.isLocal }
            is ShelfFilter.Group -> books.filter { (it.group and filter.group.groupId) > 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueBook: StateFlow<Book?> = allBooks
        .map { list -> list.maxByOrNull { it.durChapterTime }?.takeIf { it.durChapterTime > 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectFilter(filter: ShelfFilter) {
        _selectedFilter.value = filter
    }

    fun togglePin(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextOrder = if (book.order < 0) 0 else -1
            appDb.bookDao.update(book.copy(order = nextOrder))
        }
    }

    fun removeBook(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.bookDao.delete(book)
        }
    }
}
