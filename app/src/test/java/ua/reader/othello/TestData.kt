package ua.reader.othello

import java.io.File

/** The real library data, loaded once for all tests (Gradle runs tests from the module directory). */
object TestData {
    private val books = File("src/main/assets/books")

    val shelf: List<LibraryEntry> by lazy { LibraryEntry.parseAll(File(books, "library.json").readText()) }

    fun book(entry: LibraryEntry): Play = Play.parse(File(books, entry.file).readText())

    val play: Play by lazy { book(shelf.first { it.id == "othello" }) }

    fun lens(id: String) = Lens(play, play.perspectives.getValue(id))
}
