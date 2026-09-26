package ua.reader.othello

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Width of each pyramid layer, top edge and bottom edge, as a share of the full width.
internal fun layerTop(i: Int) = 0.22f + 0.156f * i
internal fun layerBottom(i: Int) = if (i == Tier.entries.lastIndex) 1f else layerTop(i + 1)

internal fun trapezoid(top: Float, bottom: Float) = GenericShape { size, _ ->
    moveTo(size.width * (1 - top) / 2, 0f)
    lineTo(size.width * (1 + top) / 2, 0f)
    lineTo(size.width * (1 + bottom) / 2, size.height)
    lineTo(size.width * (1 - bottom) / 2, size.height)
    close()
}

/** How many items each tier holds, e.g. "15 сцен". */
fun tierCount(play: Play, tier: Tier): String = when (tier) {
    Tier.Idea -> "1 речення"
    Tier.Acts -> play.terms.acts(play.pyramid.acts.size)
    Tier.Scenes -> play.terms.scenes(play.scenes.size)
    Tier.Moments -> uaPlural(play.pyramid.moments.size, "момент", "моменти", "моментів")
    Tier.Text -> play.terms.lines(play.scenes.sumOf { s -> s.lines.count { !it.isDirection } })
}

fun tierSubtitle(play: Play, tier: Tier): String = when (tier) {
    Tier.Idea -> "Увесь твір в одному реченні"
    Tier.Acts -> "Історія в ${play.pyramid.acts.size} ${play.terms.actsLocative}"
    Tier.Scenes -> "Історія в ${play.scenes.size} ${play.terms.scenesLocative}"
    Tier.Moments -> "Історія в ${play.pyramid.moments.size} ключових моментах"
    Tier.Text -> if (play.meta.guide != null) "Переказ своїми словами" else "Повний текст"
}

private val DarkInk = Color(0xFF3A0B13)
private val LightInk = Color(0xFFFFF4F5)

/** The big pyramid on the idea screen: one tappable layer per tier. */
@Composable
fun PyramidDiagram(play: Play, current: Tier, onTier: (Tier) -> Unit, modifier: Modifier = Modifier) {
    val top = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.primaryContainer
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (tier in Tier.entries) {
            val i = tier.ordinal
            val fill = lerp(top, base, i / Tier.entries.lastIndex.toFloat())
            // The layers run from dark to light in one theme and the other way round in the other,
            // so pick the ink by the layer's own brightness.
            val ink = if (fill.luminance() > 0.4f) DarkInk else LightInk
            val shape = trapezoid(layerTop(i), layerBottom(i))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .background(fill)
                    .then(
                        if (tier == current) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, shape)
                        else Modifier,
                    )
                    .clickable { onTier(tier) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(play.terms.tier(tier), color = ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(tierCount(play, tier), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** A tiny pyramid with one layer lit; used as the tier icon in the navigation bar. */
@Composable
fun MiniPyramid(layer: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val lit = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val dim = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val n = Tier.entries.size
        val gap = size.height * 0.06f
        val h = (size.height - gap * (n - 1)) / n
        for (i in 0 until n) {
            val y = i * (h + gap)
            val t = layerTop(i) * size.width
            val b = layerBottom(i) * size.width
            val path = Path().apply {
                moveTo((size.width - t) / 2, y)
                lineTo((size.width + t) / 2, y)
                lineTo((size.width + b) / 2, y + h)
                lineTo((size.width - b) / 2, y + h)
                close()
            }
            drawPath(path, if (i == layer) lit else dim)
        }
    }
}

/** Bottom navigation: the five tiers of the pyramid. */
@Composable
fun RowScope.PyramidNavItems(terms: Terms, current: Tier, onTier: (Tier) -> Unit) {
    for (tier in Tier.entries) {
        val selected = tier == current
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onTier(tier) }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MiniPyramid(tier.ordinal, selected, Modifier.size(width = 26.dp, height = 20.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                terms.tier(tier),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Tier 0: the idea ----------

@Composable
fun IdeaScreen(
    play: Play,
    index: StoryIndex,
    navigator: Navigator,
    padding: PaddingValues,
    onCharacter: (String) -> Unit,
) {
    val reading = navigator.readerFocus
    val readingScene = play.scenes[reading.scene]
    val progress = index.progress(reading)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ІДЕЯ ТВОРУ",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.15.em,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "«${play.pyramid.idea}»",
            fontFamily = FontFamily.Serif,
            fontSize = 27.sp,
            lineHeight = 36.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            formatText(play.pyramid.ideaNote, play, highlight = true, onCharacter = onCharacter),
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        PyramidDiagram(play, current = Tier.Idea, onTier = { navigator.go(it) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            "Кожен рівень переказує всю історію — від одного речення до повного тексту. Натисніть на рівень, щоб спуститися до нього.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        LensPickerRow(play) { navigator.useLens(it) }
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = { navigator.go(Tier.Text, reading) },
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    if (progress > 0f) "Продовжити читання" else "Почати читання",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${play.terms.place(readingScene)} · ${play.pyramid.scenes[reading.scene].title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Прочитано ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- Shared pieces of the tier lists ----------

@Composable
private fun HereChip() {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
        Text(
            "ви тут",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The level above, shown at the top of a tier so the reader sees what it expands. */
@Composable
private fun ParentLine(label: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "↑ $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [faded]: the lens character is not in this part of the story. */
@Composable
private fun ItemCard(highlighted: Boolean, onClick: () -> Unit, faded: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).alpha(if (faded) 0.6f else 1f),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
    }
}

/** "Без Емілії" — marks a card of the story the lens character is not part of. */
@Composable
private fun WithoutLabel(lens: Lens) {
    Text(
        "Без ${lens.perspective.genitive}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = lens.character.color.forTheme(),
    )
}

/** Text written from the lens character's side, set apart by a bar in their colour. */
@Composable
private fun FirstPerson(lens: Lens, text: String, play: Play, onCharacter: (String) -> Unit, large: Boolean) {
    val color = lens.character.color.forTheme()
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(
            formatText(text, play, highlight = true, onCharacter = onCharacter),
            style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun ZoomHint(text: String) {
    Text(
        "$text  ›",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

// ---------- Tier 1: acts ----------

@Composable
fun ActsScreen(
    play: Play,
    index: StoryIndex,
    navigator: Navigator,
    lens: Lens?,
    padding: PaddingValues,
    onCharacter: (String, Int) -> Unit,
) {
    val pyramid = play.pyramid
    val readingAct = index.actOf(navigator.readerFocus)
    val state = rememberLazyListState((index.actOf(navigator.focus) - 1).coerceAtLeast(0))
    LazyColumn(state = state, contentPadding = padding.plusVertical(8.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "parent") {
            if (lens == null) ParentLine("Ідея", pyramid.idea) { navigator.go(Tier.Idea) }
            else ParentLine("Ідея очима ${lens.perspective.genitive}", lens.perspective.idea) { navigator.go(Tier.Idea) }
        }
        items(pyramid.acts, key = { it.act }) { act ->
            val scenes = play.scenes.filter { it.act == act.act }
            val moments = pyramid.moments.filter { play.scenes[it.sceneIndex].act == act.act }
            val sceneContext = index.actStart(act.act).scene
            val present = lens == null || act.act in lens.presentActs
            ItemCard(
                highlighted = act.act == readingAct,
                faded = !present,
                onClick = { navigator.go(Tier.Scenes, index.actStart(act.act)) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        play.terms.actLabel(act.act).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 0.12.em,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(act.stage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (act.act == readingAct) HereChip()
                }
                Spacer(Modifier.height(4.dp))
                Text(act.title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(6.dp))
                val ownText = lens?.perspective?.acts?.get(act.act)
                when {
                    lens == null -> Text(
                        formatText(act.summary, play, highlight = true) { onCharacter(it, sceneContext) },
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Serif,
                    )
                    ownText != null -> FirstPerson(lens, ownText, play, { onCharacter(it, sceneContext) }, large = true)
                    else -> {
                        WithoutLabel(lens)
                        Text(
                            formatText("Тим часом: ${act.summary}", play, highlight = true) { onCharacter(it, sceneContext) },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Serif,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (lens != null && present) {
                    val inScenes = scenes.count { lens.scenePresence[it.index] > 0f }
                    val seen = moments.count { lens.perception(it.lineId).perceives }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        lens.phaseAtActEnd(act.act)?.let { PhaseChip(it); Spacer(Modifier.width(8.dp)) }
                        ZoomHint("у $inScenes з ${scenes.size} ${play.terms.scene[2]} · бачить $seen з ${moments.size}")
                    }
                } else {
                    ZoomHint("${play.terms.scenes(scenes.size)} · ${uaPlural(moments.size, "момент", "моменти", "моментів")}")
                }
            }
        }
    }
}

// ---------- Tier 2: scenes ----------

private sealed interface SceneRow {
    data class ActTitle(val act: ActSummary) : SceneRow
    data class Item(val summary: SceneSummary) : SceneRow
}

@Composable
fun ScenesScreen(
    play: Play,
    index: StoryIndex,
    navigator: Navigator,
    lens: Lens?,
    padding: PaddingValues,
    onCharacter: (String, Int) -> Unit,
) {
    val pyramid = play.pyramid
    val rows = remember(play) {
        buildList {
            for (act in pyramid.acts) {
                add(SceneRow.ActTitle(act))
                pyramid.scenes.filter { play.scenes[it.sceneIndex].act == act.act }.forEach { add(SceneRow.Item(it)) }
            }
        }
    }
    // Open with the focused scene's act title on top, unless that would push the scene off screen.
    val focusScene = play.scenes[navigator.focus.scene]
    val titleRow = rows.indexOfFirst { it is SceneRow.ActTitle && it.act.act == focusScene.act }
    val focusRow = rows.indexOfFirst { it is SceneRow.Item && it.summary.sceneIndex == focusScene.index }
    val state = rememberLazyListState(listIndex(titleRow, focusRow, maxAbove = 3))
    val readingScene = navigator.readerFocus.scene
    LazyColumn(state = state, contentPadding = padding.plusVertical(8.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "parent") {
            ParentLine(play.terms.actsTier, "Історія по кроках: ${pyramid.acts.joinToString(" → ") { it.title }}") {
                navigator.go(Tier.Acts)
            }
        }
        items(rows, key = { if (it is SceneRow.Item) "s${it.summary.sceneIndex}" else "a${(it as SceneRow.ActTitle).act.act}" }) { row ->
            when (row) {
                is SceneRow.ActTitle -> TierGroupTitle("${play.terms.actLabel(row.act.act)} · ${row.act.title}") {
                    navigator.go(Tier.Acts, index.actStart(row.act.act))
                }
                is SceneRow.Item -> {
                    val s = row.summary
                    val scene = play.scenes[s.sceneIndex]
                    val presence = lens?.scenePresence?.get(s.sceneIndex) ?: 1f
                    ItemCard(
                        highlighted = s.sceneIndex == readingScene,
                        faded = presence == 0f,
                        onClick = { navigator.go(Tier.Moments, index.sceneStart(s.sceneIndex)) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                play.terms.sceneLabel(scene).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                letterSpacing = 0.12.em,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                scene.place,
                                style = MaterialTheme.typography.labelMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (s.sceneIndex == readingScene) HereChip()
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(s.title, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(4.dp))
                        val ownText = lens?.perspective?.scenes?.get(s.sceneIndex)
                        when {
                            lens == null -> Text(
                                formatText(s.summary, play, highlight = true) { onCharacter(it, s.sceneIndex) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Serif,
                            )
                            ownText != null -> FirstPerson(lens, ownText, play, { onCharacter(it, s.sceneIndex) }, large = false)
                            else -> {
                                WithoutLabel(lens)
                                Text(
                                    formatText(s.summary, play, highlight = true) { onCharacter(it, s.sceneIndex) },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Serif,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (lens != null && presence > 0f) {
                            val seen = s.moments.count { lens.perception(it.lineId).perceives }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                lens.phaseAtSceneEnd(s.sceneIndex)?.let { PhaseChip(it); Spacer(Modifier.width(8.dp)) }
                                ZoomHint("${if (presence > 0.9f) "від початку до кінця" else "частково"} · бачить $seen з ${s.moments.size}")
                            }
                        } else {
                            ZoomHint(uaPlural(s.moments.size, "ключовий момент", "ключові моменти", "ключових моментів"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierGroupTitle(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Serif,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
    )
}

// ---------- Tier 3: key moments ----------

private sealed interface MomentRow {
    data class SceneTitle(val summary: SceneSummary) : MomentRow
    data class Item(val moment: Moment, val last: Boolean) : MomentRow
}

@Composable
fun MomentsScreen(
    play: Play,
    index: StoryIndex,
    navigator: Navigator,
    lens: Lens?,
    padding: PaddingValues,
    onCharacter: (String, Int) -> Unit,
) {
    val pyramid = play.pyramid
    val rows = remember(play) {
        buildList {
            for (s in pyramid.scenes) {
                add(MomentRow.SceneTitle(s))
                s.moments.forEachIndexed { i, m -> add(MomentRow.Item(m, i == s.moments.lastIndex)) }
            }
        }
    }
    val focusMoment = index.momentOf(navigator.focus)
    val titleRow = rows.indexOfFirst { it is MomentRow.SceneTitle && it.summary.sceneIndex == focusMoment.sceneIndex }
    val focusRow = rows.indexOfFirst { it is MomentRow.Item && it.moment == focusMoment }
    val state = rememberLazyListState(listIndex(titleRow, focusRow, maxAbove = 4))
    val current = index.momentOf(navigator.readerFocus).index
    LazyColumn(state = state, contentPadding = padding.plusVertical(8.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "parent") {
            ParentLine(
                play.terms.scenesTier,
                if (lens == null) "Кожен момент веде до свого місця в тексті"
                else "Приглушено — те, чого ${lens.perspective.genitive} не бачить: відсутність, розмови вбік, непритомність",
            ) { navigator.go(Tier.Scenes) }
        }
        items(rows, key = { if (it is MomentRow.Item) "m${it.moment.index}" else "s${(it as MomentRow.SceneTitle).summary.sceneIndex}" }) { row ->
            when (row) {
                is MomentRow.SceneTitle -> {
                    val scene = play.scenes[row.summary.sceneIndex]
                    TierGroupTitle("${play.terms.place(scene)} · ${row.summary.title}") {
                        navigator.go(Tier.Scenes, index.sceneStart(scene.index))
                    }
                }
                is MomentRow.Item -> MomentItem(
                    play = play,
                    moment = row.moment,
                    state = row.moment.index.compareTo(current),
                    last = row.last,
                    lens = lens,
                    onClick = { navigator.go(Tier.Text, index.momentFocus(row.moment)) },
                    onCharacter = { onCharacter(it, row.moment.sceneIndex) },
                )
            }
        }
    }
}

/** One key moment on the timeline. [state] < 0 already read, 0 where the reader is, > 0 ahead. */
@Composable
private fun MomentItem(
    play: Play,
    moment: Moment,
    state: Int,
    last: Boolean,
    lens: Lens?,
    onClick: () -> Unit,
    onCharacter: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val rail = MaterialTheme.colorScheme.outlineVariant
    val paper = MaterialTheme.colorScheme.background
    val perception = lens?.perception(moment.lineId)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 20.dp)
            .alpha(if (perception == null || perception.perceives) 1f else 0.45f),
    ) {
        Box(Modifier.width(20.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val dotY = 22.dp.toPx()
                drawLine(rail, Offset(x, 0f), Offset(x, if (last) dotY else size.height), strokeWidth = 2.dp.toPx())
                val dot = Offset(x, dotY)
                when {
                    state < 0 -> drawCircle(accent.copy(alpha = 0.55f), 5.dp.toPx(), dot)
                    state == 0 -> {
                        drawCircle(accent, 8.dp.toPx(), dot)
                        drawCircle(paper, 3.dp.toPx(), dot)
                    }
                    else -> {
                        drawCircle(paper, 5.dp.toPx(), dot)
                        drawCircle(accent.copy(alpha = 0.6f), 5.dp.toPx(), dot, style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (state == 0) {
                HereChip()
                Spacer(Modifier.height(4.dp))
            }
            Text(
                formatText(moment.text, play, highlight = true, onCharacter = onCharacter),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
            )
            if (lens != null && perception != null && perception != Perception.Hears) {
                Text(
                    "${lens.character.name}: ${lens.missReason(perception)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (perception == Perception.Sees) lens.character.color.forTheme() else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * First visible list item when a tier opens. The lists start with the parent line, so rows[i] is
 * list item i + 1. Show the group title on top when the focused row is close enough under it,
 * otherwise start one row above the focused row.
 */
private fun listIndex(titleRow: Int, focusRow: Int, maxAbove: Int): Int = when {
    focusRow < 0 -> 0
    titleRow >= 0 && focusRow - titleRow <= maxAbove -> titleRow + 1
    else -> focusRow
}

private fun PaddingValues.plusVertical(extra: Dp) = PaddingValues(
    top = calculateTopPadding() + extra,
    bottom = calculateBottomPadding() + extra,
)
