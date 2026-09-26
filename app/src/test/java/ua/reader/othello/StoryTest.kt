package ua.reader.othello

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Data integrity and the pyramid's navigation rules, without any UI. */
class StoryTest {
    private val play = TestData.play
    private val index = StoryIndex(play)
    private val layout = ReaderLayout(play)

    @Test
    fun data_isComplete() {
        assertEquals(15, play.scenes.size)
        assertEquals(1391, play.scenes.sumOf { it.lines.size })
        for (scene in play.scenes) for (line in scene.lines) {
            assertTrue("line ${line.id} has no translation", line.uk.isNotBlank())
            if (!line.isDirection) assertTrue("unknown speaker ${line.speaker}", line.speaker in play.characters)
        }
        for (c in play.characters.values) for (r in c.relations) {
            assertTrue("${c.id} -> ${r.characterId}", r.characterId in play.characters)
        }
    }

    @Test
    fun pyramid_coversEveryActAndScene() {
        val pyramid = play.pyramid
        assertTrue(pyramid.idea.isNotBlank())
        assertEquals((1..5).toList(), pyramid.acts.map { it.act })
        assertEquals(play.scenes.map { it.index }, pyramid.scenes.map { it.sceneIndex })
        for (s in pyramid.scenes) {
            assertTrue("scene ${s.sceneIndex} has no moments", s.moments.isNotEmpty())
            val lines = play.scenes[s.sceneIndex].lines.map { it.id }.toSet()
            for (m in s.moments) assertTrue("moment ${m.lineId} is outside its scene", m.lineId in lines)
        }
        // The whole story reads in order on the moments tier.
        assertEquals(pyramid.moments.map { it.lineId }.sorted(), pyramid.moments.map { it.lineId })
        assertEquals(pyramid.moments.indices.toList(), pyramid.moments.map { it.index })
    }

    @Test
    fun pyramid_widensTowardsTheBase() {
        val sizes = listOf(1, play.pyramid.acts.size, play.scenes.size, play.pyramid.moments.size, 1391)
        assertEquals(sizes.sorted(), sizes)
    }

    @Test
    fun momentOf_picksLastMomentAtOrBeforeTheLine() {
        val temptation = play.scenes.first { it.act == 3 && it.number == 3 }.index
        assertEquals(546, index.momentOf(Focus(temptation, 546)).lineId)
        assertEquals(546, index.momentOf(Focus(temptation, 550)).lineId)
        // Before the scene's first moment: still that scene's first moment, not the previous scene's.
        assertEquals(487, index.momentOf(index.sceneStart(temptation)).lineId)
    }

    @Test
    fun readerLayout_roundTripsFocus() {
        for (scene in play.scenes) {
            val start = index.sceneStart(scene.index)
            // A scene start scrolls to the heading, and the heading stands for that start.
            assertEquals(start, layout.focusAt(layout.rowFor(start)))
            val line = scene.lines.last().id
            assertEquals(Focus(scene.index, line), layout.focusAt(layout.rowFor(index.lineFocus(line))))
        }
    }

    @Test
    fun navigator_jumpsOnlyWhenTheTextMustMove() {
        val reading = index.lineFocus(546)
        val nav = Navigator(Tier.Text, reading)

        // Up to the acts and straight back: the text stays where it was.
        nav.go(Tier.Acts)
        nav.go(Tier.Text)
        assertFalse(nav.pendingJump)

        // Zoom into act V, then down to the text: the text opens act V.
        nav.go(Tier.Acts)
        nav.go(Tier.Scenes, index.actStart(5))
        assertFalse(nav.pendingJump)
        nav.go(Tier.Text)
        assertTrue(nav.pendingJump)
        nav.onJumped()
        assertEquals(index.actStart(5), nav.readerFocus)
    }

    @Test
    fun navigator_backClimbsThePyramid() {
        val nav = Navigator(Tier.Text, index.lineFocus(546))
        val climbed = mutableListOf<Tier>()
        while (nav.zoomOut()) climbed += nav.tier
        assertEquals(listOf(Tier.Moments, Tier.Scenes, Tier.Acts, Tier.Idea), climbed)
        assertEquals(index.lineFocus(546), nav.focus)
    }

    @Test
    fun navigator_followsTheTextOnlyWhileItIsOpen() {
        val nav = Navigator(Tier.Text, index.lineFocus(546))
        nav.onReaderMoved(index.lineFocus(600))
        assertEquals(600, nav.focus.line)
        nav.go(Tier.Moments, index.lineFocus(1195))
        nav.onReaderMoved(index.lineFocus(601)) // stray report from the text animating out
        assertEquals(1195, nav.focus.line)
        assertEquals(601, nav.readerFocus.line)
    }

    @Test
    fun uaPlural_usesUkrainianForms() {
        assertEquals("1 сцена", uaPlural(1, "сцена", "сцени", "сцен"))
        assertEquals("3 сцени", uaPlural(3, "сцена", "сцени", "сцен"))
        assertEquals("15 сцен", uaPlural(15, "сцена", "сцени", "сцен"))
        assertEquals("21 сцена", uaPlural(21, "сцена", "сцени", "сцен"))
        assertEquals("1391 репліка", uaPlural(1391, "репліка", "репліки", "реплік"))
        assertEquals("112 реплік", uaPlural(112, "репліка", "репліки", "реплік"))
    }

    @Test
    fun nameRegex_matchesInflectedNamesOnly() {
        val found = play.nameRegex!!.findAll("Мавр кохає Дездемону, а Яго радить маврові; Мавританія — ні.")
            .map { play.characterForName(it.value) }.toList()
        assertEquals(listOf("othello", "desdemona", "iago", "othello"), found)
    }

    @Test
    fun versions_compareNumerically() {
        assertTrue(Versions.compare("1.10.0", "1.9.2") > 0)
        assertTrue(Versions.compare("v1.1", "1.1.0") == 0)
        assertTrue(Versions.compare("1.0", "1.1.0") < 0)
    }

    @Test
    fun githubRelease_parsesApkAsset() {
        val json = """
            {"tag_name":"v1.2.0","body":"Нове","assets":[
              {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":1},
              {"name":"Othello-Reader.apk","browser_download_url":"https://x/app.apk","size":1234}]}
        """.trimIndent()
        assertEquals(Release("1.2.0", "Нове", "https://x/app.apk", 1234), parseGithubRelease(json))
        assertEquals(null, parseGithubRelease("""{"tag_name":"v1","body":"","assets":[]}"""))
    }
}
