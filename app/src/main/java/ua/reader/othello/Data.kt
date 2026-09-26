package ua.reader.othello

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class Line(
    val id: Int,
    val uk: String,
    val en: String,
    val speaker: String?,
) {
    val isDirection get() = speaker == null
}

data class Scene(
    val index: Int,
    val act: Int,
    val number: Int,
    val place: String,
    val placeEn: String,
    val lines: List<Line>,
)

data class Relation(val characterId: String, val text: String)

data class Character(
    val id: String,
    val name: String,
    val role: String,
    val color: Color,
    val main: Boolean,
    val names: List<String>,
    val who: String,
    val traits: List<String>,
    val relations: List<Relation>,
    val arc: Map<Int, String>,
    val quote: String?,
)

/** Where a character speaks in the play: line count and where their first line is. */
data class Appearance(val lines: Int, val firstScene: Int, val firstLineId: Int)

class Play(
    val scenes: List<Scene>,
    val characters: Map<String, Character>,
    pyramidText: String,
) {
    val pyramid: Pyramid = Pyramid.parse(pyramidText, scenes)
    val appearances: Map<String, Appearance>
    private val nameToId: Map<String, String>
    val nameRegex: Regex?

    init {
        val counts = mutableMapOf<String, Int>()
        val first = mutableMapOf<String, Pair<Int, Int>>()
        for (scene in scenes) for (line in scene.lines) {
            val sp = line.speaker ?: continue
            counts[sp] = (counts[sp] ?: 0) + 1
            first.putIfAbsent(sp, scene.index to line.id)
        }
        appearances = counts.mapValues { (id, n) ->
            val (sceneIndex, lineId) = first.getValue(id)
            Appearance(n, sceneIndex, lineId)
        }

        nameToId = buildMap {
            for (c in characters.values) for (n in c.names) put(n.lowercase(), c.id)
        }
        // Longest names first so "Дездемоною" wins over a shorter prefix; letters or an apostrophe
        // on either side mean we're inside another word (e.g. "Мавританія" must not match "мавр").
        val alternatives = nameToId.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        nameRegex = if (alternatives.isEmpty()) null
        else Regex("(?<![\\p{L}'’])($alternatives)(?![\\p{L}'’])", setOf(RegexOption.IGNORE_CASE))
    }

    fun characterForName(match: String): String? = nameToId[match.lowercase()]

    fun speakersOf(scene: Scene): List<Character> =
        scene.lines.mapNotNull { it.speaker }.distinct().mapNotNull { characters[it] }

    companion object {
        fun load(context: Context): Play = parse(
            context.assets.open("play.json").bufferedReader().readText(),
            context.assets.open("characters.json").bufferedReader().readText(),
            context.assets.open("pyramid.json").bufferedReader().readText(),
        )

        fun parse(playText: String, charactersText: String, pyramidText: String): Play {
            val playJson = JSONObject(playText)
            val charJson = JSONObject(charactersText)

            val scenes = playJson.getJSONArray("scenes").objects().mapIndexed { i, s ->
                Scene(
                    index = i,
                    act = s.getInt("act"),
                    number = s.getInt("scene"),
                    place = s.getString("place"),
                    placeEn = s.getString("placeEn"),
                    lines = s.getJSONArray("items").objects().map { l ->
                        Line(
                            id = l.getInt("id"),
                            uk = l.getString("uk"),
                            en = l.getString("en"),
                            speaker = if (l.has("d")) null else l.getString("sp"),
                        )
                    },
                )
            }

            val characters = charJson.getJSONArray("characters").objects().associate { c ->
                val arcObj = c.getJSONObject("arc")
                c.getString("id") to Character(
                    id = c.getString("id"),
                    name = c.getString("name"),
                    role = c.getString("role"),
                    color = Color(android.graphics.Color.parseColor(c.getString("color"))),
                    main = c.getBoolean("main"),
                    names = c.getJSONArray("names").strings(),
                    who = c.getString("who"),
                    traits = c.getJSONArray("traits").strings(),
                    relations = c.getJSONArray("relations").objects().map {
                        Relation(it.getString("id"), it.getString("text"))
                    },
                    arc = arcObj.keys().asSequence().associate { it.toInt() to arcObj.getString(it) },
                    quote = c.optString("quote").ifEmpty { null },
                )
            }
            return Play(scenes, characters, pyramidText)
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

val ROMAN = listOf("", "I", "II", "III", "IV", "V")
