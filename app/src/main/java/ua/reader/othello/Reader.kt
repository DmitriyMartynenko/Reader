package ua.reader.othello

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

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
    data class Speech(override val scene: Scene, val line: Line) : ReaderRow {
        override val key get() = "l${line.id}"
    }
    data class Direction(override val scene: Scene, val line: Line) : ReaderRow {
        override val key get() = "l${line.id}"
    }
}

class ReaderLayout(play: Play) {
    val rows: List<ReaderRow>
    val sceneStart: Map<Int, Int>
    val lineRow: Map<Int, Int>

    init {
        val rows = mutableListOf<ReaderRow>()
        val sceneStart = mutableMapOf<Int, Int>()
        val lineRow = mutableMapOf<Int, Int>()
        for (scene in play.scenes) {
            if (scene.number == 1) {
                sceneStart[scene.index] = rows.size
                rows += ReaderRow.ActHeader(scene)
            } else {
                sceneStart[scene.index] = rows.size
            }
            rows += ReaderRow.SceneHeader(scene)
            for (line in scene.lines) {
                lineRow[line.id] = rows.size
                rows += if (line.isDirection) ReaderRow.Direction(scene, line) else ReaderRow.Speech(scene, line)
            }
        }
        this.rows = rows
        this.sceneStart = sceneStart
        this.lineRow = lineRow
    }
}

sealed interface Sheet {
    data class Profile(val id: String) : Sheet
    data object Cast : Sheet
    data object AllCharacters : Sheet
    data object Contents : Sheet
    data object Settings : Sheet
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderApp(play: Play, prefs: Prefs, updates: UpdateController) {
    val layout = remember(play) { ReaderLayout(play) }
    val listState = rememberLazyListState(prefs.savedRow.coerceIn(0, layout.rows.lastIndex), prefs.savedOffset)
    val scope = rememberCoroutineScope()
    var sheets by remember { mutableStateOf(listOf<Sheet>()) }

    LaunchedEffect(updates) { updates.checkOnLaunch(scope) }

    val currentScene by remember {
        derivedStateOf { layout.rows[listState.firstVisibleItemIndex.coerceIn(0, layout.rows.lastIndex)].scene }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(400)
            .collect { (row, offset) -> prefs.savePosition(row, offset) }
    }

    fun open(sheet: Sheet) { sheets = sheets + sheet }
    fun jumpTo(row: Int) {
        sheets = emptyList()
        scope.launch { listState.scrollToItem(row) }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column(Modifier.clickable { open(Sheet.Contents) }) {
                        Text(
                            "Акт ${ROMAN[currentScene.act]} · Сцена ${currentScene.number}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            currentScene.place,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { open(Sheet.Contents) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Зміст")
                    }
                    IconButton(onClick = { open(Sheet.AllCharacters) }) {
                        Icon(Icons.Filled.Person, contentDescription = "Усі персонажі")
                    }
                    IconButton(onClick = { open(Sheet.Settings) }) {
                        BadgedBox(badge = { if (updates.available != null) Badge() }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Налаштування")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { open(Sheet.Cast) },
                icon = { Icon(Icons.Filled.Face, contentDescription = null) },
                text = { Text("Хто тут?") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        PlayText(
            play = play,
            layout = layout,
            prefs = prefs,
            listState = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            onCharacter = { open(Sheet.Profile(it)) },
        )
    }

    if (sheets.isNotEmpty()) {
        SheetHost(
            play = play,
            prefs = prefs,
            sheet = sheets.last(),
            canGoBack = sheets.size > 1,
            currentScene = currentScene,
            onBack = { sheets = sheets.dropLast(1) },
            onDismiss = { sheets = emptyList() },
            onOpen = ::open,
            onJumpToScene = { jumpTo(layout.sceneStart.getValue(it)) },
            onJumpToLine = { jumpTo(layout.lineRow.getValue(it)) },
            updates = updates,
        )
    }

    UpdateDialogs(updates, scope)
}

@Composable
private fun PlayText(
    play: Play,
    layout: ReaderLayout,
    prefs: Prefs,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onCharacter: (String) -> Unit,
) {
    val fontSize = prefs.fontSize.sp
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(layout.rows, key = { it.key }) { row ->
            when (row) {
                is ReaderRow.ActHeader -> ActHeader(row.scene.act)
                is ReaderRow.SceneHeader -> SceneHeader(row.scene)
                is ReaderRow.Direction -> DirectionRow(play, row.line, prefs, onCharacter)
                is ReaderRow.Speech -> SpeechRow(play, row.line, prefs, fontSize.value, onCharacter)
            }
        }
        item(key = "end") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Кінець", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Вільям Шекспір · «Отелло, венеційський мавр»\nПереклад українською для цього застосунку",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActHeader(act: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Акт ${ROMAN[act]}",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.width(48.dp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SceneHeader(scene: Scene) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Сцена ${scene.number}",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.08.em,
        )
        Text(
            scene.place,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpeechRow(play: Play, line: Line, prefs: Prefs, fontSize: Float, onCharacter: (String) -> Unit) {
    val speaker = play.characters[line.speaker]
    val color = (speaker?.color ?: MaterialTheme.colorScheme.primary).forTheme()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
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
