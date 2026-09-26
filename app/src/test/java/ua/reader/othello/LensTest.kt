package ua.reader.othello

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The story through one character's eyes: what they perceive, how they change, how the text folds. */
class LensTest {
    private val play = TestData.play
    private val index = StoryIndex(play)

    @Test
    fun everyCharacterIsInTheScenesThePlayPutsThemIn() {
        val expected = mapOf(
            "othello" to 12, "desdemona" to 9, "iago" to 13, "cassio" to 9,
            "emilia" to 8, "roderigo" to 7, "brabantio" to 3, "bianca" to 3,
        )
        assertEquals(expected.keys, play.perspectives.keys)
        for ((id, scenes) in expected) assertEquals(id, scenes, TestData.lens(id).presentScenes.size)
    }

    @Test
    fun firstPersonTextsCoverExactlyTheCharactersOwnScenesAndActs() {
        for (id in play.perspectives.keys) {
            val lens = TestData.lens(id)
            assertEquals(id, lens.presentScenes.toSet(), lens.perspective.scenes.keys)
            assertEquals(id, lens.presentActs, lens.perspective.acts.keys)
            assertTrue(id, lens.perspective.idea.isNotBlank())
        }
    }

    @Test
    fun iagoKnowsTheMostAndDesdemonaTheLeast() {
        val heard = play.perspectives.keys.associateWith { TestData.lens(it).heardSpeeches }
        val main = listOf("othello", "desdemona", "iago", "cassio", "emilia")
        assertEquals("iago", main.maxBy { heard.getValue(it) })
        assertTrue(heard.getValue("othello") > heard.getValue("desdemona"))
    }

    @Test
    fun perceptionFollowsTheStageDirections() {
        val othello = TestData.lens("othello")
        assertEquals(Perception.Absent, othello.perception(186))      // Iago's plan at the end of I.3
        assertEquals(Perception.Aside, othello.perception(277))       // «O, you are well tun'd now»
        assertEquals(Perception.Absent, othello.perception(584))      // Emilia finds the handkerchief
        assertEquals(Perception.Unconscious, othello.perception(783)) // «Work on, my medicine»
        assertEquals(Perception.Sees, othello.perception(834))        // hidden, he sees Bianca throw it
        assertEquals(Perception.Hears, othello.perception(868))       // «strangle her in her bed»
        assertEquals(Perception.Dead, othello.perception(1390))

        val desdemona = TestData.lens("desdemona")
        assertEquals(Perception.Asleep, desdemona.perception(1195))   // «It is the cause»
        assertEquals(Perception.Hears, desdemona.perception(1219))
        assertEquals(Perception.Unconscious, desdemona.perception(1250))
        assertEquals(Perception.Dead, desdemona.perception(1310))
    }

    @Test
    fun phasesTrackTheCharacterThroughTime() {
        val othello = TestData.lens("othello")
        assertNull(othello.phaseAt(10))
        assertEquals("Кохає і певен себе", othello.phaseAt(300)!!.label)
        assertEquals("Сумнівається", othello.phaseAt(546)!!.label)
        assertEquals("Вірить у зраду", othello.phaseAt(700)!!.label)
        assertEquals("Знає правду", othello.phaseAt(1339)!!.label)
        assertEquals("end", othello.phaseAtSceneEnd(play.scenes.last().index)!!.tone.key)
    }

    @Test
    fun foldedTextKeepsEveryLineExactlyOnce() {
        for (id in play.perspectives.keys) {
            val layout = ReaderLayout(play, TestData.lens(id))
            val shown = layout.rows.flatMap { row ->
                when (row) {
                    is ReaderRow.Speech -> listOf(row.line.id)
                    is ReaderRow.Direction -> listOf(row.line.id)
                    is ReaderRow.Hidden -> row.lines.map { it.id }
                    else -> emptyList()
                }
            }
            assertEquals(id, play.lines.keys.sorted(), shown)
            // Every place in the story still has a row to scroll to, and that row leads back there.
            for (scene in play.scenes) {
                val start = index.sceneStart(scene.index)
                assertEquals(id, start, layout.focusAt(layout.rowFor(start)))
            }
        }
    }

    @Test
    fun foldsSayWhyTheCharacterMissesTheLines() {
        val layout = ReaderLayout(play, TestData.lens("othello"))
        fun rowOf(line: Int) = layout.rows[layout.lineRow.getValue(line)]
        assertEquals(Perception.Aside, (rowOf(277) as ReaderRow.Hidden).reason)
        assertEquals(Perception.Unconscious, (rowOf(783) as ReaderRow.Hidden).reason)
        assertEquals(Perception.Absent, (rowOf(584) as ReaderRow.Hidden).reason)
        // The trance fold holds everything Othello misses while unconscious, including Cassio's visit.
        assertTrue((rowOf(783) as ReaderRow.Hidden).lines.map { it.id }.containsAll(listOf(784, 786, 790)))
        // Hidden and watching, Othello's view of the scene is kept but marked.
        val watched = rowOf(834) as ReaderRow.Speech
        assertTrue(watched.watching)
    }

    @Test
    fun storyAxisMapsLinesBothWays() {
        val axis = StoryAxis(play)
        for (id in listOf(1, 546, 1195, 1391)) assertEquals(id, axis.lineAt(axis.start(id)))
        assertEquals(4, axis.actStarts.size)
        assertEquals(axis.actStarts.sorted(), axis.actStarts)
    }

    @Test
    fun timelineSpansShowWhereTheCharacterIs() {
        val axis = StoryAxis(play)
        val othello = TestData.lens("othello")
        // Othello first appears in I.2 and is there until his death at the very end.
        val life = othello.spans(Tier.Idea, axis).single()
        assertEquals(axis.start(52), life.start)
        assertEquals(12, othello.spans(Tier.Scenes, axis).size)
        assertEquals(5, othello.spans(Tier.Acts, axis).size)
        assertEquals(play.pyramid.moments.size, othello.spans(Tier.Moments, axis).size)
        assertEquals(othello.seenMoments, othello.spans(Tier.Moments, axis).count { it.strength > 0f })

        for (id in play.perspectives.keys) {
            val lens = TestData.lens(id)
            for (tier in Tier.entries) {
                val spans = lens.spans(tier, axis)
                for (s in spans) assertTrue("$id $tier $s", s.start in 0f..1f && s.end in s.start..1f)
                if (tier != Tier.Moments) {
                    // Ordered along the story and never overlapping.
                    spans.zipWithNext().forEach { (a, b) -> assertTrue("$id $tier", a.end <= b.start + 1e-6f) }
                }
            }
            // The text layer marks exactly the lines the character perceives.
            val marked = lens.spans(Tier.Text, axis).sumOf { ((it.end - it.start) * axis.size).toDouble() }
            val perceived = play.lines.values.count { lens.perception(it).perceives }
            assertEquals(id, perceived.toDouble(), marked, 0.01)
        }
    }

    @Test
    fun navigatorKeepsThePlaceWhenTheLensChanges() {
        val nav = Navigator(Tier.Text, index.lineFocus(584))
        nav.useLens("othello")
        val layout = ReaderLayout(play, TestData.lens("othello"))
        // Line 584 is folded away for Othello; the list opens on the fold that holds it.
        val row = layout.rowFor(nav.readerFocus)
        assertTrue(layout.rows[row] is ReaderRow.Hidden)
        assertEquals("othello", nav.lens)
    }
}
