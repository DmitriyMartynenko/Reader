package ua.reader.othello

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every book of the library must satisfy to fit the pyramid architecture. New books are
 * picked up from assets/books/library.json, so adding a book runs these checks on it too.
 */
class BookContractTest {
    private val books = TestData.shelf.map { it to TestData.book(it) }

    @Test
    fun libraryListsEveryBookFile() {
        assertTrue(books.isNotEmpty())
        for ((entry, play) in books) {
            assertEquals(entry.id, play.meta.id)
            assertEquals(entry.title, play.meta.title)
            assertEquals(entry.idea, play.pyramid.idea)
        }
    }

    @Test
    fun textIsCompleteAndSpokenByKnownCharacters() {
        for ((entry, play) in books) {
            assertTrue(entry.id, play.scenes.isNotEmpty())
            for (line in play.lines.values) {
                assertTrue("${entry.id} #${line.id} is empty", line.uk.isNotBlank())
                if (!line.isDirection && !line.isProse) assertTrue("${entry.id} #${line.id}: ${line.speaker}", line.speaker in play.characters)
                for (id in line.hear) assertTrue("${entry.id} #${line.id}: $id", id in play.characters)
                line.noteKind?.let { assertTrue("${entry.id} #${line.id}: note kind $it", it in play.terms.noteKinds) }
            }
            for (c in play.characters.values) for (r in c.relations) {
                assertTrue("${entry.id} ${c.id} -> ${r.characterId}", r.characterId in play.characters)
            }
        }
    }

    @Test
    fun pyramidCoversTheWholeStoryInOrder() {
        for ((entry, play) in books) {
            val pyramid = play.pyramid
            assertTrue(entry.id, pyramid.idea.isNotBlank())
            assertEquals(entry.id, play.scenes.map { it.act }.distinct(), pyramid.acts.map { it.act })
            assertEquals(entry.id, play.scenes.map { it.index }, pyramid.scenes.map { it.sceneIndex })
            for (s in pyramid.scenes) {
                assertTrue("${entry.id} scene ${s.sceneIndex} has no moments", s.moments.isNotEmpty())
                val lines = play.scenes[s.sceneIndex].lines.map { it.id }.toSet()
                for (m in s.moments) assertTrue("${entry.id} moment #${m.lineId} outside its scene", m.lineId in lines)
            }
            assertEquals(entry.id, pyramid.moments.map { it.lineId }.sorted(), pyramid.moments.map { it.lineId })
        }
    }

    @Test
    fun perspectivesMatchWhereCharactersAre() {
        for ((entry, play) in books) {
            for ((id, p) in play.perspectives) {
                assertTrue("${entry.id}: $id has no profile", id in play.characters)
                val lens = Lens(play, p)
                assertEquals("${entry.id} $id scenes", lens.presentScenes.toSet(), p.scenes.keys)
                assertEquals("${entry.id} $id acts", lens.presentActs, p.acts.keys)
                for (e in p.events + p.phases.map { LifeEvent(it.lineId, it.label, it.tone) }) {
                    assertTrue("${entry.id} $id #${e.lineId}", e.lineId in play.lines)
                }
            }
        }
    }

    @Test
    fun everyBookOpensInTheReader() {
        for ((entry, play) in books) {
            val layout = ReaderLayout(play)
            val index = StoryIndex(play)
            for (scene in play.scenes) {
                val start = index.sceneStart(scene.index)
                assertEquals(entry.id, start, layout.focusAt(layout.rowFor(start)))
            }
        }
    }
}
