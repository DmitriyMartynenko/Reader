package ua.reader.othello

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The reader's own books: added from .book.json files on the phone, replaced by newer versions, removed. */
class MyBooksTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val builtIn = TestData.shelf.map { it.id }.toSet()
    private val source = File("src/main/assets/books/deadwood.book.json").readText()

    private fun library() = MyBooks(File(folder.root, "library"), builtIn)

    /** A real built book under another id and title, as if the reader had built it themselves. */
    private fun book(id: String, title: String = "Моя книга", format: Int = BOOK_FORMAT): ByteArray =
        JSONObject(source).apply {
            put("format", format)
            getJSONObject("meta").put("id", id).put("title", title)
        }.toString().toByteArray()

    @Test
    fun addsABookThatOpens() {
        val books = library()
        val added = books.add(book("my-story"))
        assertFalse(added.replaced)
        assertFalse(added.entry.builtIn)
        assertEquals(listOf("my-story"), books.entries().map { it.id })

        val entry = books.entries().single()
        assertEquals("Моя книга", entry.title)
        val play = Play.parse(books.read(entry))
        assertEquals("my-story", play.meta.id)
        assertEquals(play.pyramid.idea, entry.idea)
    }

    @Test
    fun aNewVersionReplacesTheOldOne() {
        val books = library()
        books.add(book("my-story", "Чернетка"))
        val again = books.add(book("my-story", "Остаточна версія"))
        assertTrue(again.replaced)
        assertEquals(listOf("Остаточна версія"), books.entries().map { it.title })
    }

    @Test
    fun keepsSeveralBooksAndSurvivesARestart() {
        library().add(book("first"))
        library().add(book("second"))
        assertEquals(setOf("first", "second"), library().entries().map { it.id }.toSet())
    }

    @Test
    fun removesABookAndItsFile() {
        val books = library()
        books.add(book("first"))
        books.add(book("second"))
        books.remove("first")
        assertEquals(listOf("second"), books.entries().map { it.id })
        assertFalse(File(folder.root, "library/first.book.json").exists())
    }

    @Test
    fun builtInBooksCannotBeShadowed() {
        val e = assertThrows(ImportException::class.java) { library().add(book("deadwood")) }
        assertTrue(e.message!!.contains("стандартних"))
        assertTrue(library().entries().isEmpty())
    }

    @Test
    fun rejectsFilesThatAreNotBooks() {
        val books = library()
        assertThrows(ImportException::class.java) { books.add("<FictionBook/>".toByteArray()) }
        assertThrows(ImportException::class.java) { books.add("""{"books": []}""".toByteArray()) }
        val broken = JSONObject(source).apply { getJSONObject("play").remove("scenes") }.toString().toByteArray()
        assertThrows(ImportException::class.java) { books.add(broken) }
        assertTrue(books.entries().isEmpty())
    }

    @Test
    fun rejectsBooksFromANewerApp() {
        val e = assertThrows(ImportException::class.java) { library().add(book("my-story", format = BOOK_FORMAT + 1)) }
        assertTrue(e.message!!.contains("новішої версії"))
    }

    @Test
    fun anIdCannotLeaveTheFolder() {
        assertThrows(ImportException::class.java) { library().add(book("../escape")) }
        assertThrows(ImportException::class.java) { library().add(book("My Story")) }
        assertFalse(File(folder.root, "escape.book.json").exists())
    }
}
