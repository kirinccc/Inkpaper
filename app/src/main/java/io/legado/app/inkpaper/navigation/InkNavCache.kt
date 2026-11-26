package io.legado.app.inkpaper.navigation

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object InkNavCache {

    private val searchBooks = ConcurrentHashMap<String, SearchBook>()
    private val books = ConcurrentHashMap<String, Book>()

    fun putSearchBook(book: SearchBook) {
        searchBooks[book.bookUrl] = book
    }

    fun getSearchBook(bookUrl: String): SearchBook? = searchBooks[bookUrl]

    fun putBook(book: Book) {
        books[book.bookUrl] = book
    }

    fun getBook(bookUrl: String): Book? = books[bookUrl]

    fun encodeKey(raw: String): String =
        URLEncoder.encode(raw, StandardCharsets.UTF_8.name())

    fun decodeKey(encoded: String): String =
        runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }
            .getOrDefault(encoded)
}
