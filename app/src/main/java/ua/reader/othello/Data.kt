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

/** The words a book uses for its units: a play has acts and scenes, a novel might have parts and chapters. */
data class Terms(
    val act: List<String>,
    val actTitle: String,
    val actGenitive: String,
    val actsTier: String,
    val actsLocative: String,
    val scene: List<String>,
    val sceneTitle: String,
    val scenesTier: String,
    val scenesLocative: String,
    val line: List<String>,
) {
    fun acts(n: Int) = uaPlural(n, act[0], act[1], act[2])
    fun scenes(n: Int) = uaPlural(n, scene[0], scene[1], scene[2])
    fun lines(n: Int) = uaPlural(n, line[0], line[1], line[2])

    /** "Акт III · Сцена 3" */
    fun place(scene: Scene) = "$actTitle ${roman(scene.act)} · $sceneTitle ${scene.number}"

    fun tier(tier: Tier) = when (tier) {
        Tier.Idea -> "Ідея"
        Tier.Acts -> actsTier
        Tier.Scenes -> scenesTier
        Tier.Moments -> "Моменти"
        Tier.Text -> "Текст"
    }

    companion object {
        fun parse(o: JSONObject) = Terms(
            act = o.getJSONArray("act").strings(),
            actTitle = o.getString("actTitle"),
            actGenitive = o.getString("actGenitive"),
            actsTier = o.getString("actsTier"),
            actsLocative = o.getString("actsLocative"),
            scene = o.getJSONArray("scene").strings(),
            sceneTitle = o.getString("sceneTitle"),
            scenesTier = o.getString("scenesTier"),
            scenesLocative = o.getString("scenesLocative"),
            line = o.getJSONArray("line").strings(),
        )
    }
}

/** A book's passport: what the library shelf and the book's own screens say about it. */
data class BookMeta(
    val id: String,
    val title: String,
    val fullTitle: String,
    val author: String,
    val genre: String,
    val year: Int,
    val cover: Color,
    val originalLabel: String,
    val originalHint: String,
    val credits: String,
    val terms: Terms,
) {
    companion object {
        fun parse(o: JSONObject) = BookMeta(
            id = o.getString("id"),
            title = o.getString("title"),
            fullTitle = o.getString("fullTitle"),
            author = o.getString("author"),
            genre = o.getString("genre"),
            year = o.getInt("year"),
            cover = hexColor(o.getString("cover")),
            originalLabel = o.getString("originalLabel"),
            originalHint = o.getString("originalHint"),
            credits = o.getString("credits"),
            terms = Terms.parse(o.getJSONObject("terms")),
        )
    }
}

/** One book of the library, fully loaded: text, characters, pyramid and perspectives. */
class Play(
    val meta: BookMeta,
    val scenes: List<Scene>,
    val characters: Map<String, Character>,
    pyramidJson: JSONObject,
    perspectivesJson: JSONObject,
) {
    val terms: Terms get() = meta.terms
    val pyramid: Pyramid = Pyramid.parse(pyramidJson, scenes)
    val lines: Map<Int, Line> = scenes.flatMap { it.lines }.associateBy { it.id }
    val perspectives: Map<String, Perspective> = Perspective.parseAll(perspectivesJson, scenes)
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
        /** Opens a book of the library: assets/books/<file>, as written by tools/build-book.js. */
        fun load(context: Context, file: String): Play =
            parse(context.assets.open("books/$file").bufferedReader().readText())

        fun parse(bookText: String): Play {
            val book = JSONObject(bookText)
            val playJson = book.getJSONObject("play")

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

            val characters = book.getJSONArray("characters").objects().associate { c ->
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
            return Play(
                meta = BookMeta.parse(book.getJSONObject("meta")),
                scenes = scenes,
                characters = characters,
                pyramidJson = book.getJSONObject("pyramid"),
                perspectivesJson = book.getJSONObject("perspectives"),
            )
        }
    }
}

/** A book on the library shelf, read from assets/books/library.json without opening the book. */
data class LibraryEntry(
    val id: String,
    val title: String,
    val author: String,
    val genre: String,
    val year: Int,
    val cover: Color,
    val idea: String,
    val file: String,
) {
    companion object {
        fun parseAll(text: String): List<LibraryEntry> = JSONObject(text).getJSONArray("books").objects().map {
            LibraryEntry(
                id = it.getString("id"),
                title = it.getString("title"),
                author = it.getString("author"),
                genre = it.getString("genre"),
                year = it.getInt("year"),
                cover = hexColor(it.getString("cover")),
                idea = it.getString("idea"),
                file = it.getString("file"),
            )
        }

        fun load(context: Context) = parseAll(context.assets.open("books/library.json").bufferedReader().readText())
    }
}

internal fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
internal fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

/** "#RRGGBB" to an opaque colour, without Android's parser so plain JVM tests can load the data. */
fun hexColor(hex: String): Color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000L)

/** Roman numeral for act and part numbers. */
fun roman(n: Int): String {
    var rest = n
    return buildString {
        for ((value, digits) in listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")) {
            while (rest >= value) { append(digits); rest -= value }
        }
    }
}
