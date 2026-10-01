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
    /** A retelling beat of a guide book: prose without a speaker; [hear] holds who takes part. */
    val isProse: Boolean = false,
    /** A beat that steps out of the story to explain it, headed by [Terms.noteLabel]. */
    val isNote: Boolean = false,
    /** Which of [Terms.noteKinds] heads the note instead, when a book keeps several kinds apart. */
    val noteKind: String? = null,
) {
    val isDirection get() = speaker == null && !isProse

    /** Who acts in the line: the speaker of a play, the people in a beat of a retelling. */
    val actors: List<String> get() = speaker?.let(::listOf) ?: if (isProse) hear.toList() else emptyList()
}

data class Scene(
    val index: Int,
    val act: Int,
    val number: Int,
    val place: String,
    val placeEn: String,
    val lines: List<Line>,
    /** How a novel names the unit instead of "Сцена N": "Пролог", "Розділ 14", "Календар". */
    val label: String? = null,
    /** Short tag for tight spots, e.g. "14" or "К". */
    val short: String? = null,
    /** prologue, chapter or calendar: used to match an imported copy to the units. */
    val kind: String? = null,
    /** Whose eyes a chapter is told through. */
    val pov: String? = null,
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
    /** Per-act names where "Акт II" does not fit: a novel's "Пролог", "Частина I"… */
    val actLabels: Map<Int, String> = emptyMap(),
    val actShorts: Map<Int, String> = emptyMap(),
    val actGenitiveLabels: Map<Int, String> = emptyMap(),
    /** The base tier: "Текст" for a full text, "Переказ" for a guide. */
    val textTier: String = "Текст",
    /** Why a character misses a line: "немає на сцені" in a play. */
    val offstage: String = "немає на сцені",
    /** The heading of a guide's explanatory beats, e.g. "Механіка пастки". */
    val noteLabel: String = "Примітка",
    /** Kinds of notes a book keeps apart, each with its own heading and colour: "Тіньова влада"… */
    val noteKinds: Map<String, NoteKind> = emptyMap(),
) {
    /** The kind that heads a note; null means the book's one [noteLabel]. */
    fun noteKind(line: Line): NoteKind? = line.noteKind?.let(noteKinds::get)

    fun acts(n: Int) = uaPlural(n, act[0], act[1], act[2])
    fun scenes(n: Int) = uaPlural(n, scene[0], scene[1], scene[2])
    fun lines(n: Int) = uaPlural(n, line[0], line[1], line[2])

    /** "Акт III", "Частина II", "Пролог". */
    fun actLabel(act: Int) = actLabels[act] ?: "$actTitle ${roman(act)}"

    /** "III", "Пр". */
    fun actShort(act: Int) = actShorts[act] ?: roman(act)

    /** "акту III", "прологу": for "до … включно". */
    fun upTo(act: Int) = actGenitiveLabels[act] ?: "$actGenitive ${roman(act)}"

    /** "Сцена 3", "Розділ 14". */
    fun sceneLabel(scene: Scene) = scene.label ?: "$sceneTitle ${scene.number}"

    /** "III.3", "14". */
    fun sceneShort(scene: Scene) = scene.short ?: "${roman(scene.act)}.${scene.number}"

    /** "Акт III · Сцена 3"; a unit named like its act ("Пролог") is named once. */
    fun place(scene: Scene): String {
        val act = actLabel(scene.act)
        val unit = sceneLabel(scene)
        return if (act == unit) act else "$act · $unit"
    }

    fun tier(tier: Tier) = when (tier) {
        Tier.Idea -> "Ідея"
        Tier.Acts -> actsTier
        Tier.Scenes -> scenesTier
        Tier.Moments -> "Моменти"
        Tier.Text -> textTier
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
            actLabels = o.intMap("actLabels"),
            actShorts = o.intMap("actShorts"),
            actGenitiveLabels = o.intMap("actGenitiveLabels"),
            textTier = o.optString("textTier").ifEmpty { "Текст" },
            offstage = o.optString("offstage").ifEmpty { "немає на сцені" },
            noteLabel = o.optString("noteLabel").ifEmpty { "Примітка" },
            noteKinds = o.optJSONObject("noteKinds")?.let { k ->
                k.keys().asSequence().associateWith { NoteKind.parse(k.getJSONObject(it)) }
            }.orEmpty(),
        )
    }
}

/** One kind of a guide's notes, e.g. "Як було насправді" in brown. */
data class NoteKind(val label: String, val color: Color) {
    companion object {
        fun parse(o: JSONObject) = NoteKind(o.getString("label"), hexColor(o.getString("color")))
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
    /** Set for a guide to a work still under copyright: the app carries no text of its own. */
    val guide: Guide? = null,
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
            guide = o.optJSONObject("guide")?.let { Guide(it.getString("notice"), it.optString("importHint").ifEmpty { null }) },
        )
    }
}

/** Why a book is a guide and, if the reader may add their own copy of the work, how. */
data class Guide(val notice: String, val importHint: String?)

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
        for (scene in scenes) for (line in scene.lines) for (sp in line.actors) {
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

    /** Who speaks in a scene of a play, or takes part in a chapter of a guide. */
    fun speakersOf(scene: Scene): List<Character> =
        scene.lines.flatMap { it.actors }.distinct().mapNotNull { characters[it] }

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
                    placeEn = s.optString("placeEn"),
                    label = s.optString("label").ifEmpty { null },
                    short = s.optString("short").ifEmpty { null },
                    kind = s.optString("kind").ifEmpty { null },
                    pov = s.optString("pov").ifEmpty { null },
                    lines = s.getJSONArray("items").objects().map { l ->
                        fun ids(key: String) = l.optJSONArray(key)?.strings()?.toSet().orEmpty()
                        Line(
                            id = l.getInt("id"),
                            uk = l.getString("uk"),
                            en = l.optString("en"),
                            speaker = if (l.has("d") || l.has("p")) null else l.getString("sp"),
                            hear = ids("h"),
                            see = ids("s"),
                            unconscious = ids("u"),
                            asleep = ids("z"),
                            shutOut = ids("x"),
                            isProse = l.has("p"),
                            isNote = l.has("n"),
                            noteKind = l.optString("k").ifEmpty { null },
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

/**
 * A book on the library shelf, read from a library index without opening the book: the built-in
 * index in assets/books/library.json, or the index of books the reader added to this phone.
 */
data class LibraryEntry(
    val id: String,
    val title: String,
    val author: String,
    val genre: String,
    val year: Int,
    val cover: Color,
    val idea: String,
    val file: String,
    /** Shipped inside the app; otherwise added by the reader and removable. */
    val builtIn: Boolean = true,
) {
    companion object {
        fun parseAll(text: String, builtIn: Boolean = true): List<LibraryEntry> = JSONObject(text).getJSONArray("books").objects().map {
            LibraryEntry(
                id = it.getString("id"),
                title = it.getString("title"),
                author = it.getString("author"),
                genre = it.getString("genre"),
                year = it.getInt("year"),
                cover = hexColor(it.getString("cover")),
                idea = it.getString("idea"),
                file = it.getString("file"),
                builtIn = builtIn,
            )
        }

        fun load(context: Context) = parseAll(context.assets.open("books/library.json").bufferedReader().readText())
    }
}

internal fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
internal fun JSONObject.intMap(key: String): Map<Int, String> =
    optJSONObject(key)?.let { o -> o.keys().asSequence().associate { it.toInt() to o.getString(it) } }.orEmpty()

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
