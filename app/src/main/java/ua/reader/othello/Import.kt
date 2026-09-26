package ua.reader.othello

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/*
 * The reader's own copy of a guided book. A guide carries no text of the work; the reader may add
 * an e-book they own (FB2, FB2 in a zip, or EPUB). It is split into chapters here, matched to the
 * guide's units and kept in the app's private storage on the phone only.
 */

/** One heading of an e-book and the paragraphs under it. */
data class CopySection(val title: String, val paragraphs: List<String>)

/** The imported copy, one section per unit of the book (by scene index). */
class OwnCopy(val units: List<CopySection>) {
    fun toJson(): String = JSONObject().put(
        "units",
        JSONArray(units.map { u -> JSONObject().put("title", u.title).put("p", JSONArray(u.paragraphs)) }),
    ).toString()

    companion object {
        fun fromJson(text: String) = OwnCopy(
            JSONObject(text).getJSONArray("units").objects().map { CopySection(it.getString("title"), it.getJSONArray("p").strings()) },
        )
    }
}

class ImportException(message: String) : Exception(message)

object CopyImport {
    private val PROLOGUE = Regex("^\\s*(пролог|prolog)", RegexOption.IGNORE_CASE)
    private val CALENDAR = Regex("^\\s*(календар|calendar)", RegexOption.IGNORE_CASE)
    private val CHAPTER = Regex("^\\s*(глава|розділ|chapter|\\d+)", RegexOption.IGNORE_CASE)
    private val PART = Regex("^\\s*(часть|частина|part|книга|book)\\b", RegexOption.IGNORE_CASE)

    fun kindOf(title: String): String = when {
        PROLOGUE.containsMatchIn(title) -> "prologue"
        CALENDAR.containsMatchIn(title) -> "calendar"
        PART.containsMatchIn(title) -> "part"
        else -> "chapter"
    }

    /** Reads an e-book into sections, whatever the format. */
    fun read(bytes: ByteArray): List<CopySection> {
        if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val entries = unzip(bytes)
            entries["META-INF/container.xml"]?.let { return readEpub(entries, it) }
            val fb2 = entries.entries.firstOrNull { it.key.endsWith(".fb2", ignoreCase = true) }
                ?: throw ImportException("В архіві немає ні EPUB, ні FB2.")
            return readFb2(fb2.value)
        }
        val head = String(bytes, 0, minOf(bytes.size, 400), Charsets.ISO_8859_1)
        if ("FictionBook" in head || "<?xml" in head) return readFb2(bytes)
        throw ImportException("Це не схоже на FB2 чи EPUB.")
    }

    /** Matches the sections to the units of [scenes] by their kinds, in order. */
    fun match(sections: List<CopySection>, scenes: List<Scene>): OwnCopy {
        val usable = sections.filter { it.paragraphs.isNotEmpty() && kindOf(it.title) != "part" }
        val firstKind = scenes.first().kind ?: "chapter"
        var i = usable.indexOfFirst { kindOf(it.title) == firstKind }
        if (i < 0) throw ImportException("Не знайшов початку книги (${label(firstKind)}).")
        val out = mutableListOf<CopySection>()
        for (scene in scenes) {
            val want = scene.kind ?: "chapter"
            val got = usable.getOrNull(i)
                ?: throw ImportException("Розпізнано ${out.size} з ${scenes.size} розділів: файл закінчився раніше.")
            if (kindOf(got.title) != want) {
                throw ImportException(
                    "Розпізнано ${out.size} з ${scenes.size} розділів: після «${out.lastOrNull()?.title ?: "початку"}» " +
                        "очікувався ${label(want)}, а знайдено «${got.title}».",
                )
            }
            out += got
            i++
        }
        return OwnCopy(out)
    }

    private fun label(kind: String) = when (kind) {
        "prologue" -> "пролог"
        "calendar" -> "календар"
        else -> "розділ"
    }

    fun readFb2(bytes: ByteArray): List<CopySection> {
        val xml = decode(bytes)
        val notes = Regex("<body[^>]*name=\"notes\"").find(xml)?.range?.first ?: xml.length
        val start = xml.indexOf("<body").takeIf { it >= 0 } ?: 0
        val body = xml.substring(start, notes.coerceAtLeast(start))
        val token = Regex(
            "<title>(.*?)</title>|<(p|v|subtitle)\\b[^>]*>(.*?)</\\2>",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val out = mutableListOf<CopySection>()
        var title: String? = null
        var paragraphs = mutableListOf<String>()
        fun flush() { title?.let { out += CopySection(it, paragraphs) }; paragraphs = mutableListOf() }
        for (m in token.findAll(body)) {
            val t = m.groups[1]
            if (t != null) {
                flush()
                title = Regex("<p\\b[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).findAll(t.value)
                    .map { clean(it.groupValues[1]) }.filter(String::isNotBlank).joinToString(" ")
                    .ifBlank { clean(t.value) }
            } else {
                val text = clean(m.groupValues[3])
                if (text.isNotBlank()) paragraphs += text
            }
        }
        flush()
        return out
    }

    fun readEpub(entries: Map<String, ByteArray>, container: ByteArray): List<CopySection> {
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(String(container, Charsets.UTF_8))?.groupValues?.get(1)
            ?: throw ImportException("Пошкоджений EPUB: немає опису книги.")
        val opf = String(entries[opfPath] ?: throw ImportException("Пошкоджений EPUB."), Charsets.UTF_8)
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = Regex("<item\\b[^>]*>").findAll(opf).associate { m ->
            fun attr(name: String) = Regex("\\b$name=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1).orEmpty()
            attr("id") to attr("href")
        }
        val spine = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        val token = Regex("<h([1-6])\\b[^>]*>(.*?)</h\\1>|<p\\b[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val out = mutableListOf<CopySection>()
        var title: String? = null
        var partNamed = false
        var paragraphs = mutableListOf<String>()
        fun flush() { title?.let { out += CopySection(it, paragraphs) }; paragraphs = mutableListOf() }
        for (id in spine) {
            val href = manifest[id] ?: continue
            val doc = entries[normalize(base + href.substringBefore('#'))] ?: continue
            val html = String(doc, Charsets.UTF_8).substringAfter("<body")
            for (m in token.findAll(html)) {
                val heading = m.groups[2]
                if (heading != null) {
                    val text = clean(heading.value)
                    if (text.isBlank()) continue
                    // "Часть первая" then its name: the name belongs to the same heading, once.
                    if (title != null && paragraphs.isEmpty() && kindOf(title!!) == "part" && !partNamed &&
                        kindOf(text) == "chapter" && !CHAPTER.containsMatchIn(text)
                    ) {
                        title = "$title $text"
                        partNamed = true
                    } else {
                        flush()
                        title = text
                        partNamed = false
                    }
                } else {
                    val text = clean(m.groupValues[3])
                    if (text.isNotBlank()) paragraphs += text
                }
            }
        }
        flush()
        return out
    }

    private fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        for (p in path.split('/')) when (p) {
            "", "." -> {}
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(java.net.URLDecoder.decode(p, "UTF-8"))
        }
        return parts.joinToString("/")
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) out[e.name] = zip.readBytes()
            }
        }
        return out
    }

    private fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val name = Regex("encoding=[\"']([^\"']+)").find(head)?.groupValues?.get(1)
        val charset = runCatching { Charset.forName(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    /** Markup to plain text: footnote marks dropped, entities decoded, spaces collapsed. */
    fun clean(s: String): String = s
        .replace(Regex("<a\\b[^>]*type=\"note\"[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<sup>.*?</sup>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .replace(Regex(" ([,.;:!?»)])"), "$1")
        .trim()
}

/** Imported copies in the app's private storage, one file per book; never part of the app itself. */
class CopyStore(private val dir: File) {
    private fun file(bookId: String) = File(dir, "$bookId.json")

    fun load(bookId: String): OwnCopy? = file(bookId).takeIf(File::exists)?.let {
        runCatching { OwnCopy.fromJson(it.readText()) }.getOrNull()
    }

    fun save(bookId: String, copy: OwnCopy) {
        dir.mkdirs()
        file(bookId).writeText(copy.toJson())
    }

    fun remove(bookId: String) { file(bookId).delete() }
}
