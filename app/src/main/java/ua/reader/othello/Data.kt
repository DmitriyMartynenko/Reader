package ua.reader.othello

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * One speech or stage direction. The perception sets come from tools/presence.js and follow the
 * stage directions: who hears it, who only watches (hidden), who is on stage but unconscious or
 * asleep, and who is on stage but shut out of an aside.
 */
data class Line(
    val id: Int,
    val uk: String,
    val en: String,
    val speaker: String?,
    val hear: Set<String> = emptySet(),
    val see: Set<String> = emptySet(),
    val unconscious: Set<String> = emptySet(),
    val asleep: Set<String> = emptySet(),
    val shutOut: Set<String> = emptySet(),
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
    perspectivesText: String,
) {
    val pyramid: Pyramid = Pyramid.parse(pyramidText, scenes)
    val lines: Map<Int, Line> = scenes.flatMap { it.lines }.associateBy { it.id }
    val perspectives: Map<String, Perspective> = Perspective.parseAll(perspectivesText, scenes)
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
        fun load(context: Context): Play {
            fun asset(name: String) = context.assets.open(name).bufferedReader().readText()
            return parse(asset("play.json"), asset("characters.json"), asset("pyramid.json"), asset("perspectives.json"))
        }

        fun parse(playText: String, charactersText: String, pyramidText: String, perspectivesText: String): Play {
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
                        fun ids(key: String) = l.optJSONArray(key)?.strings()?.toSet().orEmpty()
                        Line(
                            id = l.getInt("id"),
                            uk = l.getString("uk"),
                            en = l.getString("en"),
                            speaker = if (l.has("d")) null else l.getString("sp"),
                            hear = ids("h"),
                            see = ids("s"),
                            unconscious = ids("u"),
                            asleep = ids("z"),
                            shutOut = ids("x"),
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
                    color = hexColor(c.getString("color")),
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
            return Play(scenes, characters, pyramidText, perspectivesText)
        }
    }
}

internal fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
internal fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

/** "#RRGGBB" to an opaque colour, without Android's parser so plain JVM tests can load the data. */
fun hexColor(hex: String): Color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000L)

val ROMAN = listOf("", "I", "II", "III", "IV", "V")
