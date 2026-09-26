package ua.reader.othello

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * The play as a pyramid: one idea on top, then acts, scenes and key moments,
 * with the full text as the base. Every tier retells the whole story at its own resolution.
 */
enum class Tier(val label: String) {
    Idea("Ідея"),
    Acts("Акти"),
    Scenes("Сцени"),
    Moments("Моменти"),
    Text("Текст");

    val up: Tier? get() = entries.getOrNull(ordinal - 1)
    val down: Tier? get() = entries.getOrNull(ordinal + 1)
}

data class ActSummary(val act: Int, val title: String, val stage: String, val summary: String)

data class SceneSummary(val sceneIndex: Int, val title: String, val summary: String, val moments: List<Moment>)

data class Moment(val index: Int, val sceneIndex: Int, val lineId: Int, val text: String)

class Pyramid(
    val idea: String,
    val ideaNote: String,
    val acts: List<ActSummary>,
    val scenes: List<SceneSummary>,
) {
    val moments: List<Moment> = scenes.flatMap { it.moments }

    fun act(act: Int): ActSummary = acts.first { it.act == act }

    companion object {
        fun parse(text: String, scenes: List<Scene>): Pyramid {
            val json = JSONObject(text)
            val acts = json.getJSONArray("acts").let { arr ->
                List(arr.length()) { i ->
                    val a = arr.getJSONObject(i)
                    ActSummary(a.getInt("act"), a.getString("title"), a.getString("stage"), a.getString("summary"))
                }
            }
            var momentIndex = 0
            val summaries = json.getJSONArray("scenes").let { arr ->
                List(arr.length()) { i ->
                    val s = arr.getJSONObject(i)
                    val scene = scenes.first { it.act == s.getInt("act") && it.number == s.getInt("scene") }
                    val ms = s.getJSONArray("moments")
                    SceneSummary(
                        sceneIndex = scene.index,
                        title = s.getString("title"),
                        summary = s.getString("summary"),
                        moments = List(ms.length()) { j ->
                            val m = ms.getJSONObject(j)
                            Moment(momentIndex++, scene.index, m.getInt("line"), m.getString("text"))
                        },
                    )
                }
            }.sortedBy { it.sceneIndex }
            return Pyramid(json.getString("idea"), json.getString("ideaNote"), acts, summaries)
        }
    }
}

/** A place in the story: the scene and the line within it. */
data class Focus(val scene: Int, val line: Int)

/** Maps a place in the story to the item that covers it on every tier. */
class StoryIndex(private val play: Play) {
    private val pyramid get() = play.pyramid
    private val sceneOfLine: Map<Int, Int> =
        play.scenes.flatMap { s -> s.lines.map { it.id to s.index } }.toMap()
    private val allLines: List<Int> = play.scenes.flatMap { s -> s.lines.map { it.id } }

    fun sceneStart(sceneIndex: Int) = Focus(sceneIndex, play.scenes[sceneIndex].lines.first().id)

    fun actStart(act: Int) = sceneStart(play.scenes.first { it.act == act }.index)

    fun lineFocus(lineId: Int) = Focus(sceneOfLine.getValue(lineId), lineId)

    fun actOf(focus: Focus) = play.scenes[focus.scene].act

    /** The last key moment at or before the focus within its scene, or the scene's first moment. */
    fun momentOf(focus: Focus): Moment {
        val inScene = pyramid.scenes[focus.scene].moments
        return inScene.lastOrNull { it.lineId <= focus.line } ?: inScene.first()
    }

    fun momentFocus(moment: Moment) = Focus(moment.sceneIndex, moment.lineId)

    /** Share of the play's lines before the focus, 0..1. */
    fun progress(focus: Focus): Float {
        val i = allLines.indexOf(focus.line)
        return if (i < 0) 0f else i.toFloat() / (allLines.size - 1)
    }
}

/**
 * Where the user is in the pyramid.
 *
 * [readerFocus] is where the full text is scrolled to (the reading position). [focus] is what the
 * tiers are looking at: it follows the text while the text is open and moves to whatever item the
 * user zooms into, so going down to the text afterwards opens that place.
 */
class Navigator(start: Tier, readerFocus: Focus, lens: String? = null) {
    var tier: Tier by mutableStateOf(start)
        private set

    /** Whose eyes the story is seen with: a character id, or null for the all-seeing author. */
    var lens: String? by mutableStateOf(lens)
        private set

    fun useLens(id: String?) {
        lens = id
    }
    var focus: Focus by mutableStateOf(readerFocus)
        private set
    var readerFocus: Focus by mutableStateOf(readerFocus)
        private set

    /** The text has to scroll to [focus] when it is shown; the reader clears it after scrolling. */
    var pendingJump: Boolean by mutableStateOf(false)
        private set

    /** Direction of the last move, for the zoom animation: +1 deeper, -1 higher, 0 same tier. */
    var direction: Int = 0
        private set

    fun go(tier: Tier, focus: Focus = this.focus) {
        direction = tier.ordinal.compareTo(this.tier.ordinal)
        this.focus = focus
        this.tier = tier
        if (tier == Tier.Text && focus != readerFocus) pendingJump = true
    }

    /** Called by the text as it scrolls. */
    fun onReaderMoved(position: Focus) {
        readerFocus = position
        if (tier == Tier.Text && !pendingJump) focus = position
    }

    fun onJumped() {
        pendingJump = false
        readerFocus = focus
    }

    /** Back = one level up the pyramid; false at the top so the system can close the app. */
    fun zoomOut(): Boolean {
        val up = tier.up ?: return false
        go(up)
        return true
    }
}

/** Ukrainian plural: 1 сцена, 2 сцени, 5 сцен. */
fun uaPlural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}
