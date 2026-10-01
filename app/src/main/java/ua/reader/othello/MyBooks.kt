package ua.reader.othello

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** The version of the book file tools/build-book.js writes; a newer file needs a newer app. */
const val BOOK_FORMAT = 1

/**
 * Books the reader added to this phone, beside the built-in shelf: each one a .book.json written by
 * tools/build-book.js, copied into the app's private storage with an index of its own. Every phone
 * keeps its own set; app updates leave it alone.
 */
class MyBooks(private val dir: File, private val builtInIds: Set<String>) {
    private val index = File(dir, "library.json")

    /** What [add] did: the shelf entry, and whether it replaced an earlier copy of the same book. */
    data class Added(val entry: LibraryEntry, val replaced: Boolean)

    fun entries(): List<LibraryEntry> =
        runCatching { LibraryEntry.parseAll(index.readText(), builtIn = false) }.getOrDefault(emptyList())

    fun read(entry: LibraryEntry): String = File(dir, entry.file).readText()

    /**
     * Keeps [bytes] as a book if the app can open it. A book with the id of one already added
     * replaces it, so a new version of the same book keeps its reading position.
     */
    fun add(bytes: ByteArray): Added {
        if (bytes.size > MAX_BYTES) throw ImportException("Файл завеликий для книги.")
        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw ImportException("Це не файл книги. Потрібен файл .book.json, зібраний tools/build-book.js.")
        }
        val format = json.optInt("format", 0)
        if (format == 0 || !json.has("meta") || !json.has("play")) {
            throw ImportException("Це не файл книги. Потрібен файл .book.json, зібраний tools/build-book.js.")
        }
        if (format > BOOK_FORMAT) throw ImportException("Цю книгу зібрано для новішої версії застосунку. Спершу оновіть застосунок.")
        val play = try {
            Play.parse(text)
        } catch (e: Exception) {
            throw ImportException("Файл книги пошкоджений, застосунок не може його відкрити.")
        }
        val meta = play.meta
        if (!ID.matches(meta.id)) throw ImportException("У книги неприпустимий id «${meta.id}»: лише малі латинські літери, цифри й дефіси.")
        if (meta.id in builtInIds) throw ImportException("Книга з id «${meta.id}» уже є серед стандартних.")

        val file = "${meta.id}.book.json"
        val record = JSONObject()
            .put("id", meta.id)
            .put("title", meta.title)
            .put("author", meta.author)
            .put("genre", meta.genre)
            .put("year", meta.year)
            .put("cover", json.getJSONObject("meta").getString("cover"))
            .put("idea", play.pyramid.idea)
            .put("file", file)
        val all = records()
        val others = all.filter { it.getString("id") != meta.id }
        val replaced = others.size < all.size

        dir.mkdirs()
        writeAtomically(File(dir, file), text)
        writeIndex(others + record)
        return Added(LibraryEntry.parseAll(JSONObject().put("books", JSONArray().put(record)).toString(), builtIn = false).single(), replaced)
    }

    fun remove(id: String) {
        File(dir, "$id.book.json").delete()
        writeIndex(records().filter { it.getString("id") != id })
    }

    private fun records(): List<JSONObject> =
        runCatching { JSONObject(index.readText()).getJSONArray("books").objects() }.getOrDefault(emptyList())

    // The shelf shows the reader's books in the same order as the built-in ones: by year.
    private fun writeIndex(books: List<JSONObject>) {
        dir.mkdirs()
        val sorted = books.sortedWith(compareBy({ it.getInt("year") }, { it.getString("title") }))
        writeAtomically(index, JSONObject().put("books", JSONArray(sorted)).toString(1))
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw ImportException("Не вдалося зберегти книгу на пристрої.")
        }
    }

    private companion object {
        // The id names the book's file and its reading settings, so nothing that could leave the folder.
        val ID = Regex("[a-z0-9][a-z0-9-]{0,63}")
        const val MAX_BYTES = 30 * 1024 * 1024
    }
}
