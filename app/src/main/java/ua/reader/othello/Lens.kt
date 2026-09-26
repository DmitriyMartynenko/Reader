package ua.reader.othello

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/*
 * A lens is the story seen through one character. It adds a third axis to the pyramid:
 * tiers are the level of detail, the play runs through time, and the lens picks whose eyes
 * the story is seen with — what they witnessed, how they told it, and how they changed.
 */

data class Tone(val key: String, val label: String, val color: Color)

/** A state the character is in from [lineId] until the next phase: "Сумнівається", "Вірить у зраду"… */
data class Phase(val lineId: Int, val label: String, val tone: Tone)

/** A turning point in the character's life, tied to the line where it happens. */
data class LifeEvent(val lineId: Int, val label: String, val tone: Tone)

class Perspective(
    val characterId: String,
    /** "Очима …": Отелло, Дездемони, Емілії. */
    val genitive: String,
    /** The character's own apex of the pyramid, in the first person. */
    val idea: String,
    val note: String,
    /** The line from which the character is dead, if they die. */
    val death: Int?,
    val acts: Map<Int, String>,
    /** First-person account per scene index, only for scenes the character is in. */
    val scenes: Map<Int, String>,
    val phases: List<Phase>,
    val events: List<LifeEvent>,
) {
    companion object {
        fun parseAll(text: String, scenes: List<Scene>): Map<String, Perspective> {
            val json = JSONObject(text)
            val tonesJson = json.getJSONObject("tones")
            val tones = tonesJson.keys().asSequence().associateWith { key ->
                val t = tonesJson.getJSONObject(key)
                Tone(key, t.getString("label"), hexColor(t.getString("color")))
            }
            return json.getJSONArray("perspectives").objects().associate { p ->
                val actsJson = p.getJSONObject("acts")
                val scenesJson = p.getJSONObject("scenes")
                fun marks(key: String) = p.getJSONArray(key).objects().map {
                    Triple(it.getInt("line"), it.getString("label"), tones.getValue(it.getString("tone")))
                }
                p.getString("id") to Perspective(
                    characterId = p.getString("id"),
                    genitive = p.getString("genitive"),
                    idea = p.getString("idea"),
                    note = p.getString("note"),
                    death = if (p.has("death")) p.getInt("death") else null,
                    acts = actsJson.keys().asSequence().associate { it.toInt() to actsJson.getString(it) },
                    scenes = scenesJson.keys().asSequence().associate { key ->
                        val (act, number) = key.split(".").map(String::toInt)
                        scenes.first { it.act == act && it.number == number }.index to scenesJson.getString(key)
                    },
                    phases = marks("phases").map { (line, label, tone) -> Phase(line, label, tone) },
                    events = marks("events").map { (line, label, tone) -> LifeEvent(line, label, tone) },
                )
            }
        }
    }
}

/** How a character takes in one line of the play. */
enum class Perception {
    Hears, Sees, Aside, Unconscious, Asleep, Absent, Dead;

    val perceives get() = this == Hears || this == Sees
}

class Lens(val play: Play, val perspective: Perspective) {
    val character: Character = play.characters.getValue(perspective.characterId)
    val id: String get() = character.id

    fun perception(line: Line): Perception = when {
        id in line.hear -> Perception.Hears
        id in line.see -> Perception.Sees
        id in line.unconscious -> Perception.Unconscious
        id in line.asleep -> Perception.Asleep
        id in line.shutOut -> Perception.Aside
        perspective.death != null && line.id >= perspective.death -> Perception.Dead
        else -> Perception.Absent
    }

    fun perception(lineId: Int) = perception(play.lines.getValue(lineId))

    /** Share of each scene's lines the character takes in, by scene index. */
    val scenePresence: List<Float> = play.scenes.map { s ->
        s.lines.count { perception(it).perceives }.toFloat() / s.lines.size
    }
    val presentScenes: List<Int> = scenePresence.indices.filter { scenePresence[it] > 0f }
    val presentActs: Set<Int> = presentScenes.map { play.scenes[it].act }.toSet()
    val seenMoments: Int = play.pyramid.moments.count { perception(it.lineId).perceives }
    val heardSpeeches: Int = play.scenes.sumOf { s -> s.lines.count { !it.isDirection && perception(it).perceives } }
    val eventsByLine: Map<Int, LifeEvent> = perspective.events.associateBy { it.lineId }

    /** The state the character is in at a line; null before they enter the story. */
    fun phaseAt(lineId: Int): Phase? = perspective.phases.lastOrNull { it.lineId <= lineId }

    fun phaseAtSceneEnd(sceneIndex: Int): Phase? = phaseAt(play.scenes[sceneIndex].lines.last().id)

    fun phaseAtActEnd(act: Int): Phase? = phaseAt(play.scenes.last { it.act == act }.lines.last().id)

    fun isDeadAt(lineId: Int) = perspective.death != null && lineId >= perspective.death

    /** Why the character misses a line, as a short phrase: "немає на сцені", "говорять убік"… */
    fun missReason(p: Perception): String = when (p) {
        Perception.Hears -> ""
        Perception.Sees -> "бачить, але не чує"
        Perception.Aside -> "говорять убік"
        Perception.Unconscious -> "без свідомості"
        Perception.Asleep -> "спить"
        Perception.Absent -> "немає на сцені"
        Perception.Dead -> "уже немає серед живих"
    }
}
