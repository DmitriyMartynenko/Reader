package ua.reader.othello

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** An eye: the author's all-seeing view, used where a character's avatar would stand for a lens. */
@Composable
fun EyeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lid = Path().apply {
            moveTo(0f, h / 2)
            quadraticTo(w / 2, -h * 0.25f, w, h / 2)
            quadraticTo(w / 2, h * 1.25f, 0f, h / 2)
            close()
        }
        drawPath(lid, color, style = Stroke(width = h * 0.12f))
        drawCircle(color, radius = h * 0.22f, center = Offset(w / 2, h / 2))
    }
}

/** The lens control in the top bar: an eye for the author, the character's avatar for a lens. */
@Composable
fun LensButton(lens: Lens?, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (lens == null) {
            EyeIcon(MaterialTheme.colorScheme.onSurface, Modifier.size(width = 24.dp, height = 16.dp))
        } else {
            Avatar(lens.character, 30)
        }
    }
}

@Composable
fun PhaseChip(phase: Phase, prefix: String = "") {
    val color = phase.tone.color.forTheme()
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(prefix + phase.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

/** Shown under the top bar while a lens is on: whose eyes, and what state they are in right now. */
@Composable
fun LensBanner(lens: Lens, phase: Phase?, onOpen: () -> Unit, onClose: () -> Unit) {
    val color = lens.character.color.forTheme()
    // Opaque: the lists scroll underneath the top bars.
    Surface(color = color.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(lens.character, 28)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Очима ${lens.perspective.genitive}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    phase?.let { "зараз: " + it.label.replaceFirstChar(Char::lowercase) } ?: "ще не з'явився в історії",
                    style = MaterialTheme.typography.labelSmall,
                    color = phase?.tone?.color?.forTheme() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Повернутися до погляду автора")
            }
        }
    }
}

/** Horizontal row of characters to look through, for the author's idea screen. */
@Composable
fun LensPickerRow(play: Play, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "ПОДИВИТИСЯ ОЧИМА ПЕРСОНАЖА",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.15.em,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(play.perspectives.keys.toList()) { id ->
                val c = play.characters.getValue(id)
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(id) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(c, 52)
                    Spacer(Modifier.height(4.dp))
                    Text(c.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Sheet content: pick whose eyes to see the story with. */
@Composable
fun LensChooser(play: Play, current: String?, onPick: (String?) -> Unit) {
    Text(
        "Чиїми очима дивитися",
        style = MaterialTheme.typography.titleLarge,
        fontFamily = FontFamily.Serif,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
    )
    Text(
        "Кожен бачить лише частину історії. Оберіть персонажа — і вся піраміда перебудується навколо того, що він бачив, чув і пережив.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
    ChooserRow(selected = current == null, onClick = { onPick(null) }, leading = {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            EyeIcon(MaterialTheme.colorScheme.onSecondaryContainer, Modifier.size(width = 22.dp, height = 15.dp))
        }
    }) {
        Text("Автор", style = MaterialTheme.typography.titleMedium)
        Text("Бачить усе: кожну сцену, кожне слово вбік", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for ((id, p) in play.perspectives) {
        val lens = Lens(play, p)
        ChooserRow(selected = current == id, onClick = { onPick(id) }, leading = { Avatar(lens.character, 40) }) {
            Text(lens.character.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "«${p.idea}»",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${lens.presentScenes.size} з ${play.terms.scenes(play.scenes.size)} · бачить ${lens.seenMoments} з ${play.pyramid.moments.size} моментів",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChooserRow(selected: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}

/**
 * The character's life across the play: one cell per scene, grouped by act. A cell is coloured by
 * the state the character is in at the end of the scene and filled by how much of it they witness.
 */
@Composable
fun LifeLine(play: Play, lens: Lens, readingScene: Int, onScene: (Int) -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val acts = play.scenes.groupBy { it.act }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((_, scenes) in acts) {
                Row(Modifier.weight(scenes.size.toFloat()).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (s in scenes) {
                        val presence = lens.scenePresence[s.index]
                        val phase = lens.phaseAtSceneEnd(s.index)
                        val gone = lens.isDeadAt(s.lines.first().id)
                        val base = (phase?.tone?.color ?: lens.character.color).forTheme()
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    when {
                                        gone -> Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                        presence > 0f -> Modifier.background(base.copy(alpha = 0.35f + 0.65f * presence))
                                        else -> Modifier.border(1.dp, outline, RoundedCornerShape(4.dp))
                                    },
                                )
                                .clickable { onScene(s.index) },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((_, scenes) in acts) {
                Row(Modifier.weight(scenes.size.toFloat()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (s in scenes) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (s.index == readingScene) {
                                Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((act, scenes) in acts) {
                Text(
                    play.terms.actShort(act),
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(scenes.size.toFloat()),
                )
            }
        }
    }
}

/** The states the character goes through, in order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhaseFlow(lens: Lens) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lens.perspective.phases.forEachIndexed { i, phase ->
            if (i > 0) {
                Text(
                    "→",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            PhaseChip(phase)
        }
    }
}

/** Turning points of the character's life; each leads to its line in the text. */
@Composable
fun LifeEvents(play: Play, lens: Lens, onEvent: (Int) -> Unit) {
    val sceneOf = play.scenes.flatMap { s -> s.lines.map { it.id to s } }.toMap()
    Column(Modifier.fillMaxWidth()) {
        for (e in lens.perspective.events) {
            val scene = sceneOf.getValue(e.lineId)
            val color = e.tone.color.forTheme()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEvent(e.lineId) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(
                    play.terms.sceneShort(scene),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
                Text(e.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The character's own pyramid, in two modes. Share: each layer is filled by how much of that tier
 * the character witnesses. Timeline: each layer is the story from left (act I) to right (act V)
 * with the stretches the character takes part in marked, finer on every layer down; a tap there
 * opens that place of the story on that tier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensPyramid(
    play: Play,
    lens: Lens,
    readingLine: Int,
    timeline: Boolean,
    onTimeline: (Boolean) -> Unit,
    onGo: (Tier, Int?) -> Unit,
) {
    val name = lens.character.name
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SegmentedButton(
            selected = !timeline,
            onClick = { onTimeline(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Скільки бачить") }
        SegmentedButton(
            selected = timeline,
            onClick = { onTimeline(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Де в історії") }
    }
    LensPyramidDiagram(play, lens, readingLine, timeline, onGo, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text(
        if (timeline) {
            "Кожен шар — уся історія зліва направо, від першої репліки до останньої. Колір — де саме бере участь $name, " +
                "пунктир — межі актів, яскрава лінія — де ви зараз. Натисніть на будь-яке місце шару, щоб перейти туди."
        } else {
            "Заповнена частина рівня — те, що $name бачить і чує на власні очі. Решту історії від цього погляду приховано."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun LensPyramidDiagram(
    play: Play,
    lens: Lens,
    readingLine: Int,
    timeline: Boolean,
    onGo: (Tier, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = lens.character.color.forTheme()
    val guide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val here = MaterialTheme.colorScheme.primary
    val chip = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
    val axis = remember(play) { StoryAxis(play) }
    val totalSpeeches = play.scenes.sumOf { s -> s.lines.count { !it.isDirection } }
    val firstScene = play.scenes[lens.presentScenes.first()]
    val lastScene = play.scenes[lens.presentScenes.last()]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (tier in Tier.entries) {
            val (fraction, label) = when (tier) {
                Tier.Idea -> 1f to if (timeline) {
                    "${play.terms.sceneShort(firstScene)} — ${play.terms.sceneShort(lastScene)}"
                } else {
                    "власна ідея"
                }
                Tier.Acts -> lens.presentActs.size / play.pyramid.acts.size.toFloat() to "${lens.presentActs.size} з ${play.pyramid.acts.size}"
                Tier.Scenes -> lens.presentScenes.size / play.scenes.size.toFloat() to "${lens.presentScenes.size} з ${play.scenes.size}"
                Tier.Moments -> lens.seenMoments / play.pyramid.moments.size.toFloat() to "${lens.seenMoments} з ${play.pyramid.moments.size}"
                Tier.Text -> lens.heardSpeeches / totalSpeeches.toFloat() to "${lens.heardSpeeches} з ${play.terms.lines(totalSpeeches)}"
            }
            val spans = remember(lens, tier) { lens.spans(tier, axis) }
            val i = tier.ordinal
            val top = layerTop(i)
            val bottom = layerBottom(i)
            // Width share of the trapezoid at height y, and where a point t of the story lies at that height.
            fun share(y: Float, h: Float) = top + (bottom - top) * y / h
            fun xAt(t: Float, y: Float, w: Float, h: Float) = w * (1 - share(y, h)) / 2 + t * w * share(y, h)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(trapezoid(top, bottom))
                    .pointerInput(timeline) {
                        detectTapGestures { p ->
                            if (!timeline) {
                                onGo(tier, null)
                            } else {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val t = ((p.x - xAt(0f, p.y, w, h)) / (w * share(p.y, h))).coerceIn(0f, 1f)
                                onGo(tier, axis.lineAt(t))
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    fun strip(a: Float, b: Float, c: Color) {
                        drawPath(
                            Path().apply {
                                moveTo(xAt(a, 0f, w, h), 0f)
                                lineTo(xAt(b, 0f, w, h), 0f)
                                lineTo(xAt(b, h, w, h), h)
                                lineTo(xAt(a, h, w, h), h)
                                close()
                            },
                            c,
                        )
                    }
                    fun slanted(t: Float, c: Color, width: Float, dashed: Boolean = false) = drawLine(
                        c,
                        Offset(xAt(t, 0f, w, h), 0f),
                        Offset(xAt(t, h, w, h), h),
                        strokeWidth = width,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                    )

                    drawRect(color.copy(alpha = 0.12f))
                    if (!timeline) {
                        // The witnessed share of the layer, filled from the left edge of each row of the trapezoid.
                        strip(0f, fraction, color.copy(alpha = 0.55f))
                    } else {
                        if (tier == Tier.Moments) {
                            for (s in spans) {
                                val mid = (s.start + s.end) / 2
                                if (s.strength > 0f) slanted(mid, color, 2.5.dp.toPx())
                                else slanted(mid, guide.copy(alpha = 0.25f), 1.5.dp.toPx())
                            }
                        } else {
                            for (s in spans) strip(s.start, s.end, color.copy(alpha = 0.25f + 0.6f * s.strength))
                        }
                        for (a in axis.actStarts) slanted(a, guide, 1.dp.toPx(), dashed = true)
                        slanted(axis.start(readingLine), here, 2.5.dp.toPx())
                    }
                }
                Column(
                    Modifier.then(
                        if (timeline) Modifier.background(chip, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp) else Modifier,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(play.terms.tier(tier), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (timeline) {
            // Act names under the base, where the story axis spans the full width.
            Row(Modifier.fillMaxWidth()) {
                for ((act, scenes) in play.scenes.groupBy { it.act }) {
                    Text(
                        play.terms.actShort(act),
                    maxLines = 1,
                    softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(scenes.sumOf { it.lines.size }.toFloat()),
                    )
                }
            }
        }
    }
}

/** The apex through a character's eyes: their own idea, their life line and their pyramid. */
@Composable
fun LensIdeaScreen(
    play: Play,
    index: StoryIndex,
    navigator: Navigator,
    lens: Lens,
    prefs: Prefs,
    padding: PaddingValues,
    onCharacter: (String) -> Unit,
) {
    val color = lens.character.color.forTheme()
    val reading = navigator.readerFocus
    val progress = index.progress(reading)
    val readingScene = play.scenes[reading.scene]
    val name = lens.character.name
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(lens.character, 64)
        Spacer(Modifier.height(10.dp))
        Text(
            "ІДЕЯ ОЧИМА ${lens.perspective.genitive.uppercase()}",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.15.em,
            color = color,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "«${lens.perspective.idea}»",
            fontFamily = FontFamily.Serif,
            fontSize = 25.sp,
            lineHeight = 33.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            formatText(lens.perspective.note, play, highlight = true, onCharacter = onCharacter),
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ідея твору: «${play.pyramid.idea}»",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("ЛІНІЯ ЖИТТЯ", color)
        LifeLine(play, lens, reading.scene) { navigator.go(Tier.Scenes, index.sceneStart(it)) }
        Spacer(Modifier.height(4.dp))
        Text(
            "Клітинка — ${play.terms.scene[0]}. Колір — стан $name наприкінці, насиченість — яку частину видно цими очима. Крапка — де ви зараз.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        PhaseFlow(lens)
        Spacer(Modifier.height(12.dp))
        LifeEvents(play, lens) { navigator.go(Tier.Text, index.lineFocus(it)) }

        SectionLabel("ПІРАМІДА ОЧИМА ${lens.perspective.genitive.uppercase()}", color)
        LensPyramid(
            play = play,
            lens = lens,
            readingLine = reading.line,
            timeline = prefs.pyramidTimeline,
            onTimeline = prefs::updatePyramidTimeline,
            onGo = { tier, line -> navigator.go(tier, line?.let(index::lineFocus) ?: navigator.focus) },
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            onClick = { navigator.go(Tier.Text, reading) },
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    if (progress > 0f) "Читати далі очима ${lens.perspective.genitive}" else "Почати читання очима ${lens.perspective.genitive}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${play.terms.place(readingScene)} · ${play.pyramid.scenes[reading.scene].title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = { navigator.useLens(null) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Повернутися до погляду автора")
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 0.15.em,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp),
        textAlign = TextAlign.Center,
    )
}
