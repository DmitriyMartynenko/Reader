package ua.reader.othello

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** One entry of the continuous reading list. */
sealed interface ReaderRow {
    val scene: Scene
    val key: String

    data class ActHeader(override val scene: Scene) : ReaderRow {
        override val key get() = "act${scene.act}"
    }
    data class SceneHeader(override val scene: Scene) : ReaderRow {
        override val key get() = "scene${scene.index}"
    }
    /** A line the reader sees. [watching] marks lines a lens character sees but cannot hear. */
    data class Speech(override val scene: Scene, val line: Line, val watching: Boolean = false, val runStart: Boolean = false) : ReaderRow {
        override val key get() = "l${line.id}"
    }
    data class Direction(override val scene: Scene, val line: Line, val watching: Boolean = false, val runStart: Boolean = false) : ReaderRow {
        override val key get() = "l${line.id}"
    }

    /** Consecutive lines the lens character misses for the same [reason], folded into one row. */
    data class Hidden(override val scene: Scene, val lines: List<Line>, val reason: Perception) : ReaderRow {
        override val key get() = "h${lines.first().id}"
    }
}

/** The text as rows; through a [lens], what the character does not perceive is folded away. */
class ReaderLayout(play: Play, lens: Lens? = null) {
    val rows: List<ReaderRow>
    val sceneStart: Map<Int, Int>
    val lineRow: Map<Int, Int>
    private val firstLine: Map<Int, Int> = play.scenes.associate { it.index to it.lines.first().id }

    init {
        val rows = mutableListOf<ReaderRow>()
        val sceneStart = mutableMapOf<Int, Int>()
        val lineRow = mutableMapOf<Int, Int>()
        for (scene in play.scenes) {
            sceneStart[scene.index] = rows.size
            if (scene.number == 1) rows += ReaderRow.ActHeader(scene)
            rows += ReaderRow.SceneHeader(scene)

            val missed = mutableListOf<Line>()
            var missedReason: Perception? = null
            fun fold() {
                if (missed.isEmpty()) return
                missed.forEach { lineRow[it.id] = rows.size }
                rows += ReaderRow.Hidden(scene, missed.toList(), missedReason!!)
                missed.clear()
            }
            var watchingBefore = false
            for (line in scene.lines) {
                val p = lens?.perception(line)
                if (p == null || p.perceives) {
                    fold()
                    val watching = p == Perception.Sees
                    lineRow[line.id] = rows.size
                    rows += if (line.isDirection) {
                        ReaderRow.Direction(scene, line, watching, watching && !watchingBefore)
                    } else {
                        ReaderRow.Speech(scene, line, watching, watching && !watchingBefore)
                    }
                    watchingBefore = watching
                } else {
                    if (missedReason != p) fold()
                    missed += line
                    missedReason = p
                    watchingBefore = false
                }
            }
            fold()
        }
        this.rows = rows
        this.sceneStart = sceneStart
        this.lineRow = lineRow
    }

    /** The place a row stands for; headings stand for the scene's first line, folds for their first line. */
    fun focusAt(row: Int): Focus = when (val r = rows[row.coerceIn(0, rows.lastIndex)]) {
        is ReaderRow.Speech -> Focus(r.scene.index, r.line.id)
        is ReaderRow.Direction -> Focus(r.scene.index, r.line.id)
        is ReaderRow.Hidden -> Focus(r.scene.index, r.lines.first().id)
        else -> Focus(r.scene.index, firstLine.getValue(r.scene.index))
    }

    /** The row to scroll to for a place; the start of a scene shows its heading. */
    fun rowFor(focus: Focus): Int =
        if (focus.line == firstLine[focus.scene]) sceneStart.getValue(focus.scene) else lineRow.getValue(focus.line)
}

sealed interface Sheet {
    /** [scene] sets how far the spoiler-free profile may look; null means the reading position. */
    data class Profile(val id: String, val scene: Int? = null) : Sheet
    data object Cast : Sheet
    data object AllCharacters : Sheet
    data object Settings : Sheet
    data object Lens : Sheet
    /** A guide's notice and the reader's own copy. */
    data object Guide : Sheet
}

/** The base of the pyramid: the full text as one continuous list, or what [lens] perceives of it. */
@Composable
fun PlayText(
    play: Play,
    layout: ReaderLayout,
    lens: Lens?,
    prefs: Prefs,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onCharacter: (String) -> Unit,
    onMoment: (Moment) -> Unit,
    onIdea: () -> Unit,
    /** Opens a chapter of the reader's own imported copy; null when there is none. */
    onReadCopy: ((Int) -> Unit)? = null,
    hasCopy: (Int) -> Boolean = { false },
) {
    val moments = remember(play) { play.pyramid.moments.associateBy { it.lineId } }
    val expanded = remember(layout) { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(layout.rows, key = { it.key }) { row ->
            when (row) {
                is ReaderRow.ActHeader -> ActHeader(play.terms, row.scene.act, play.pyramid.act(row.scene.act).title)
                is ReaderRow.SceneHeader -> SceneHeader(
                    play, row.scene, play.pyramid.scenes[row.scene.index].title, onCharacter,
                    onReadCopy = onReadCopy?.takeIf { hasCopy(row.scene.index) }?.let { read -> { read(row.scene.index) } },
                )
                is ReaderRow.Direction -> LineBlock(moments[row.line.id], lens?.eventsByLine?.get(row.line.id), onMoment) {
                    Watching(lens, row.watching, row.runStart) { DirectionRow(play, row.line, prefs, onCharacter) }
                }
                is ReaderRow.Speech -> LineBlock(moments[row.line.id], lens?.eventsByLine?.get(row.line.id), onMoment) {
                    Watching(lens, row.watching, row.runStart) {
                        if (row.line.isProse) ProseRow(play, row.line, prefs, onCharacter)
                        else SpeechRow(play, row.line, prefs, onCharacter, own = lens != null && row.line.speaker == lens.id)
                    }
                }
                is ReaderRow.Hidden -> if (lens != null) {
                    HiddenRow(play, lens, row, prefs, expanded[row.key] == true, onCharacter) {
                        expanded[row.key] = expanded[row.key] != true
                    }
                }
            }
        }
        item(key = "end") { TheEnd(play, onIdea) }
    }
}

/**
 * Marks key moments of the pyramid (the label leads one tier up) and, through a lens,
 * the turning points of the character's life.
 */
@Composable
private fun LineBlock(moment: Moment?, event: LifeEvent?, onMoment: (Moment) -> Unit, content: @Composable () -> Unit) {
    if (moment == null && event == null) {
        content()
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val bar = if (moment != null) accent else event!!.tone.color.forTheme()
    Column(
        Modifier.drawBehind {
            drawLine(bar, Offset(8.dp.toPx(), 0f), Offset(8.dp.toPx(), size.height), strokeWidth = 3.dp.toPx())
        },
    ) {
        if (event != null) {
            val color = event.tone.color.forTheme()
            Row(Modifier.padding(start = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(event.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        if (moment != null) {
            Row(
                Modifier
                    .padding(start = 14.dp, top = if (event == null) 8.dp else 2.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onMoment(moment) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Ключовий момент",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    letterSpacing = 0.05.em,
                )
            }
        }
        content()
    }
}

/** Lines the lens character watches from hiding: shown faded, with a note at the start of the stretch. */
@Composable
private fun Watching(lens: Lens?, watching: Boolean, runStart: Boolean, content: @Composable () -> Unit) {
    if (lens == null || !watching) {
        content()
        return
    }
    Column {
        if (runStart) {
            Text(
                "${lens.character.name} бачить, але не чує",
                style = MaterialTheme.typography.labelSmall,
                color = lens.character.color.forTheme(),
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            )
        }
        Box(Modifier.alpha(0.6f)) { content() }
    }
}

/** A folded stretch of text the lens character does not perceive; opens on tap, faded. */
@Composable
private fun HiddenRow(
    play: Play,
    lens: Lens,
    row: ReaderRow.Hidden,
    prefs: Prefs,
    expanded: Boolean,
    onCharacter: (String) -> Unit,
    onToggle: () -> Unit,
) {
    val name = lens.character.name
    val why = when (row.reason) {
        Perception.Aside -> {
            val speakers = row.lines.mapNotNull { it.speaker }.distinct().mapNotNull { play.characters[it]?.name }
            val verb = if (speakers.size > 1) "говорять" else "говорить"
            "${speakers.joinToString(" і ")} $verb убік — $name не чує"
        }
        Perception.Absent -> "$name ${play.terms.offstage}"
        Perception.Unconscious -> "$name без свідомості"
        Perception.Asleep -> "$name спить"
        Perception.Dead -> "$name уже немає серед живих"
        Perception.Hears, Perception.Sees -> ""
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(why, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    play.terms.lines(row.lines.size) + " поза цим поглядом",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (expanded) "Сховати" else "Показати",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(Modifier.alpha(0.5f)) {
                for (line in row.lines) {
                    when {
                        line.isDirection -> DirectionRow(play, line, prefs, onCharacter)
                        line.isProse -> ProseRow(play, line, prefs, onCharacter)
                        else -> SpeechRow(play, line, prefs, onCharacter)
                    }
                }
            }
        }
    }
}

@Composable
private fun TheEnd(play: Play, onIdea: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Кінець", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(20.dp))
        Text(
            "«${play.pyramid.idea}»",
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 19.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onIdea) { Text("До вершини піраміди") }
        Spacer(Modifier.height(12.dp))
        Text(
            "${play.meta.author} · «${play.meta.fullTitle}»\n${play.meta.credits}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActHeader(terms: Terms, act: Int, title: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            terms.actLabel(act),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.width(48.dp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SceneHeader(play: Play, scene: Scene, title: String, onCharacter: (String) -> Unit, onReadCopy: (() -> Unit)?) {
    val terms = play.terms
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            terms.sceneLabel(scene).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.15.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
        )
        Text(
            scene.place,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val pov = scene.pov?.let(play.characters::get)
        if (pov != null) {
            Text(
                "Очима: ${pov.name}",
                style = MaterialTheme.typography.labelMedium,
                color = pov.color.forTheme(),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onCharacter(pov.id) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (onReadCopy != null) {
            TextButton(onClick = onReadCopy) { Text("Читати у вашому примірнику") }
        }
    }
}

/** A beat of a retelling: plain prose, names clickable. */
@Composable
private fun ProseRow(play: Play, line: Line, prefs: Prefs, onCharacter: (String) -> Unit) {
    Text(
        formatText(line.uk, play, prefs.highlightNames, onCharacter),
        fontFamily = FontFamily.Serif,
        fontSize = prefs.fontSize.sp,
        lineHeight = (prefs.fontSize * 1.45f).sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun SpeechRow(play: Play, line: Line, prefs: Prefs, onCharacter: (String) -> Unit, own: Boolean = false) {
    val fontSize = prefs.fontSize
    val speaker = play.characters[line.speaker]
    val color = (speaker?.color ?: MaterialTheme.colorScheme.primary).forTheme()
    Column(
        Modifier
            .fillMaxWidth()
            // Through a lens, the character's own words stand out.
            .then(if (own) Modifier.background(color.copy(alpha = 0.08f)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .clickable { line.speaker?.let(onCharacter) }
                .padding(vertical = 2.dp),
        ) {
            Text(
                (speaker?.name ?: line.speaker.orEmpty()).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.em,
                color = color,
            )
        }
        Text(
            formatText(line.uk, play, prefs.highlightNames, onCharacter),
            fontFamily = FontFamily.Serif,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.45f).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (prefs.showOriginal) {
            Text(
                formatText(line.en, play = null, highlight = false, onCharacter = {}),
                fontFamily = FontFamily.Serif,
                fontSize = (fontSize * 0.8f).sp,
                lineHeight = (fontSize * 1.15f).sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 12.dp),
            )
        }
    }
}

@Composable
private fun DirectionRow(play: Play, line: Line, prefs: Prefs, onCharacter: (String) -> Unit) {
    val text = line.uk.removeSurrounding("[_", "_]").removeSurrounding("[_", "]")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            formatText(text, play, prefs.highlightNames, onCharacter),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = (prefs.fontSize * 0.88f).sp,
            lineHeight = (prefs.fontSize * 1.3f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// Inline stage directions look like "[_Убік._]"; songs are wrapped in underscores.
private val MARKUP = Regex("\\[_(.*?)_?]|_([^_]+)_", RegexOption.DOT_MATCHES_ALL)

@Composable
fun formatText(
    text: String,
    play: Play?,
    highlight: Boolean,
    onCharacter: (String) -> Unit,
): AnnotatedString {
    val dirColor = MaterialTheme.colorScheme.onSurfaceVariant
    val characters = play?.characters
    val nameColors = characters?.mapValues { it.value.color.forTheme() }.orEmpty()
    val pressed = MaterialTheme.colorScheme.primaryContainer

    return buildAnnotatedString {
        fun appendWithNames(chunk: String) {
            val regex = play?.nameRegex
            if (!highlight || regex == null) { append(chunk); return }
            var last = 0
            for (m in regex.findAll(chunk)) {
                append(chunk.substring(last, m.range.first))
                val id = play.characterForName(m.value)
                if (id == null) append(m.value)
                else withLink(
                    LinkAnnotation.Clickable(
                        tag = id,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = nameColors[id] ?: Color.Unspecified, fontWeight = FontWeight.SemiBold),
                            pressedStyle = SpanStyle(background = pressed),
                        ),
                    ) { onCharacter(id) },
                ) { append(m.value) }
                last = m.range.last + 1
            }
            append(chunk.substring(last))
        }

        var last = 0
        for (m in MARKUP.findAll(text)) {
            appendWithNames(text.substring(last, m.range.first))
            val direction = m.groups[1]
            if (direction != null) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = dirColor)) {
                    appendWithNames(direction.value.replace("_", ""))
                }
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendWithNames(m.groupValues[2]) }
            }
            last = m.range.last + 1
        }
        appendWithNames(text.substring(last))
    }
}

@Composable
fun Avatar(character: Character, size: Int = 44) {
    val color = character.color.forTheme()
    Box(
        Modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            character.name.take(1),
            color = color,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
        )
    }
}

val CardShape = RoundedCornerShape(16.dp)
