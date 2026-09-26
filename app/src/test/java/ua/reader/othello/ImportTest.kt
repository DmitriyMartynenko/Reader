package ua.reader.othello

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Importing one's own copy of a guided book, on tiny made-up books (no text of any real work). */
class ImportTest {
    private fun scene(i: Int, kind: String) = Scene(i, 1, i + 1, "", "", emptyList(), kind = kind)
    private val units = listOf(scene(0, "prologue"), scene(1, "chapter"), scene(2, "chapter"), scene(3, "calendar"), scene(4, "chapter"))

    private val fb2 = """
        <?xml version="1.0" encoding="windows-1251"?>
        <FictionBook><body><title><p>Автор</p><p>Назва</p></title>
        <section><title><p>ПРОЛОГ</p></title><p>Перший абзац пролога<a l:href="#n1" type="note">[1]</a>.</p></section>
        <section><title><p>ЧАСТЬ ПЕРВАЯ</p><p>Назва частини</p></title><epigraph><p>Епіграф</p></epigraph>
          <section><title><p>ГЛАВА ПЕРВАЯ</p></title><p>Один &amp; два.</p><p>Три.</p></section>
          <section><title><p>ГЛАВА ВТОРАЯ</p></title><p>Чотири.</p></section>
          <section><title><p>КАЛЕНДАРЬ</p></title><p>Вирізка.</p></section>
        </section>
        <section><title><p>ЧАСТЬ ВТОРАЯ</p></title>
          <section><title><p>ГЛАВА ТРЕТЬЯ</p></title><p>П'ять.</p></section>
        </section></body>
        <body name="notes"><section><title><p>1</p></title><p>Примітка</p></section></body></FictionBook>
    """.trimIndent()

    @Test
    fun readsFb2InItsOwnEncoding() {
        val sections = CopyImport.read(fb2.toByteArray(charset("windows-1251")))
        val copy = CopyImport.match(sections, units)
        assertEquals(listOf("ПРОЛОГ", "ГЛАВА ПЕРВАЯ", "ГЛАВА ВТОРАЯ", "КАЛЕНДАРЬ", "ГЛАВА ТРЕТЬЯ"), copy.units.map { it.title })
        assertEquals(listOf("Перший абзац пролога."), copy.units[0].paragraphs)
        assertEquals(listOf("Один & два.", "Три."), copy.units[1].paragraphs)
        assertTrue(copy.units.none { u -> u.paragraphs.any { "Примітка" in it } })
    }

    @Test
    fun readsZippedFb2() {
        val zip = zip(mapOf("book.fb2" to fb2.toByteArray(charset("windows-1251"))))
        assertEquals(5, CopyImport.match(CopyImport.read(zip), units).units.size)
    }

    @Test
    fun readsEpubInSpineOrder() {
        fun page(body: String) = "<html><head><title>x</title></head><body>$body</body></html>".toByteArray()
        val epub = zip(
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""".toByteArray(),
                "OEBPS/content.opf" to """
                    <package><manifest>
                    <item id="b" href="Text/b.xhtml"/><item id="a" href="Text/a.xhtml"/>
                    </manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>
                """.toByteArray(),
                "OEBPS/Text/a.xhtml" to page("<h1>Prologue</h1><p>One.</p><h1>Part One</h1><h2>Bloody</h2><h2>Chapter 1</h2><p>Two.</p>"),
                "OEBPS/Text/b.xhtml" to page("<h2>Chapter 2</h2><p>Three.</p><h2>Calendar</h2><p>News.</p><h1>Part Two</h1><h2>3</h2><p>Four.</p>"),
            ),
        )
        val copy = CopyImport.match(CopyImport.read(epub), units)
        assertEquals(listOf("Prologue", "Chapter 1", "Chapter 2", "Calendar", "3"), copy.units.map { it.title })
        assertEquals(listOf("Four."), copy.units[4].paragraphs)
    }

    @Test
    fun explainsAMismatch() {
        val broken = fb2.replace("<section><title><p>КАЛЕНДАРЬ</p></title><p>Вирізка.</p></section>", "")
        try {
            CopyImport.match(CopyImport.read(broken.toByteArray(charset("windows-1251"))), units)
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message, e.message!!.contains("3 з 5"))
        }
    }

    @Test
    fun theGuideKnowsTheKindOfEveryUnit() {
        val guide = TestData.book(TestData.shelf.first { it.id == "la-confidential" })
        assertTrue(guide.scenes.all { it.kind in setOf("prologue", "chapter", "calendar") })
        assertTrue("a guide carries no text of the work", guide.lines.values.all { it.isProse && it.en.isEmpty() })
    }

    @Test
    fun copyRoundTripsThroughStorage() {
        val dir = createTempDir()
        val store = CopyStore(dir)
        store.save("b", OwnCopy(listOf(CopySection("T", listOf("a", "b")))))
        assertEquals(listOf("a", "b"), store.load("b")!!.units[0].paragraphs)
        store.remove("b")
        assertEquals(null, store.load("b"))
        dir.deleteRecursively()
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
