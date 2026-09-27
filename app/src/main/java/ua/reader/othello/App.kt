package ua.reader.othello

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * One open book: one tier of its pyramid at a time, the tier bar at the bottom, sheets on top.
 * Above the book's idea there is only the library, reached with back or the arrow at the top.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun App(
    play: Play,
    prefs: Prefs,
    book: BookPrefs,
    updates: UpdateController,
    startTier: Tier = if (prefs.startAtIdea) Tier.Idea else Tier.Text,
    onLibrary: () -> Unit = {},
) {
    val index = remember(play) { StoryIndex(play) }
    val navigator = remember {
        val line = book.savedLine?.takeIf(play.lines::containsKey)
            ?: ReaderLayout(play).focusAt(book.legacyRow).line
        Navigator(startTier, index.lineFocus(line), book.lens?.takeIf(play.perspectives::containsKey))
    }
    val lens = remember(navigator.lens) { navigator.lens?.let(play.perspectives::get)?.let { Lens(play, it) } }
    val layout = remember(lens) { ReaderLayout(play, lens) }
    // A new lens folds the text differently, so the list starts over at the same place in the story.
    val savedOffset = remember { intArrayOf(book.savedOffset) }
    val listState = remember(layout) {
        LazyListState(layout.rowFor(navigator.readerFocus), savedOffset[0]).also { savedOffset[0] = 0 }
    }
    var sheets by remember { mutableStateOf(listOf<Sheet>()) }
    val tier = navigator.tier

    // A guide's text lives only in the reader's own imported copy, kept in private storage.
    val context = LocalContext.current
    val store = remember { CopyStore(File(context.filesDir, "copies")) }
    var copyState by remember(play) {
        mutableStateOf(if (play.meta.guide?.importHint == null) CopyState.None else store.load(play.meta.id)?.let { CopyState.Ready(it) } ?: CopyState.None)
    }
    var copyChapter by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        copyState = CopyState.Importing
        scope.launch {
            copyState = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val copy = CopyImport.match(CopyImport.read(bytes), play.scenes)
                    store.save(play.meta.id, copy)
                    CopyState.Ready(copy)
                } catch (e: ImportException) {
                    CopyState.Failed(e.message.orEmpty())
                } catch (e: Exception) {
                    CopyState.Failed("Не вдалося прочитати файл.")
                }
            }
        }
    }
    val ownCopy = (copyState as? CopyState.Ready)?.copy

    LaunchedEffect(navigator.lens) { book.updateLens(navigator.lens) }

    LaunchedEffect(listState, layout) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { navigator.onReaderMoved(layout.focusAt(it)) }
    }
    LaunchedEffect(listState, layout) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(400)
            .collect { (row, offset) ->
                val focus = layout.focusAt(row)
                book.savePosition(focus.line, offset, index.progress(focus))
            }
    }
    // Zooming into the text from a tier opens the text at the chosen place.
    LaunchedEffect(tier, navigator.pendingJump, layout) {
        if (tier == Tier.Text && navigator.pendingJump) {
            listState.scrollToItem(layout.rowFor(navigator.focus))
            navigator.onJumped()
        }
    }

    BackHandler { if (!navigator.zoomOut()) onLibrary() }

    fun open(sheet: Sheet) { sheets = sheets + sheet }
    val openProfile: (String, Int) -> Unit = { id, scene -> open(Sheet.Profile(id, scene)) }
    val readingScene = play.scenes[navigator.readerFocus.scene]

    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val bottomScroll = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    LaunchedEffect(tier) {
        topScroll.state.heightOffset = 0f
        bottomScroll.state.heightOffset = 0f
    }

    Scaffold(
        modifier = if (tier == Tier.Text) {
            Modifier.nestedScroll(topScroll.nestedScrollConnection).nestedScroll(bottomScroll.nestedScrollConnection)
        } else {
            Modifier
        },
        topBar = {
            Column {
                AppTopBar(
                    play = play,
                    tier = tier,
                    lens = lens,
                    readingScene = readingScene,
                    scrollBehavior = if (tier == Tier.Text) topScroll else null,
                    updateAvailable = updates.available != null,
                    onUp = { if (!navigator.zoomOut()) onLibrary() },
                    onTitle = { if (tier == Tier.Text) navigator.go(Tier.Scenes) },
                    onOpen = ::open,
                )
                if (lens != null && tier != Tier.Idea) {
                    LensBanner(
                        lens = lens,
                        phase = lens.phaseAt(navigator.readerFocus.line),
                        onOpen = { navigator.go(Tier.Idea) },
                        onClose = { navigator.useLens(null) },
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentPadding = PaddingValues(horizontal = 8.dp),
                scrollBehavior = if (tier == Tier.Text) bottomScroll else null,
            ) {
                PyramidNavItems(play.terms, tier) { navigator.go(it) }
            }
        },
        floatingActionButton = {
            if (tier == Tier.Text) {
                ExtendedFloatingActionButton(
                    onClick = { open(Sheet.Cast) },
                    icon = { Icon(Icons.Filled.Face, contentDescription = null) },
                    text = { Text("Хто тут?") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tier,
            transitionSpec = {
                // Going down the pyramid zooms in, going up zooms out.
                val deeper = targetState.ordinal > initialState.ordinal
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = if (deeper) 0.92f else 1.08f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = if (deeper) 1.08f else 0.92f))
            },
            label = "tier",
        ) { shown ->
            when (shown) {
                Tier.Idea -> {
                    val onName: (String) -> Unit = { open(Sheet.Profile(it, navigator.readerFocus.scene)) }
                    if (lens == null) IdeaScreen(play, index, navigator, padding, onName)
                    else LensIdeaScreen(play, index, navigator, lens, prefs, padding, onName)
                }
                Tier.Acts -> ActsScreen(play, index, navigator, lens, padding, openProfile)
                Tier.Scenes -> ScenesScreen(play, index, navigator, lens, padding, openProfile)
                Tier.Moments -> MomentsScreen(play, index, navigator, lens, padding, openProfile)
                Tier.Text -> PlayText(
                    play = play,
                    layout = layout,
                    lens = lens,
                    prefs = prefs,
                    listState = listState,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    onCharacter = { open(Sheet.Profile(it)) },
                    onMoment = { navigator.go(Tier.Moments, index.momentFocus(it)) },
                    onIdea = { navigator.go(Tier.Idea) },
                    onReadCopy = if (ownCopy != null) { i -> copyChapter = i } else null,
                    hasCopy = { ownCopy != null && it < ownCopy.units.size },
                )
            }
        }
    }

    if (sheets.isNotEmpty()) {
        SheetHost(
            play = play,
            prefs = prefs,
            sheet = sheets.last(),
            canGoBack = sheets.size > 1,
            currentScene = readingScene,
            onBack = { sheets = sheets.dropLast(1) },
            onDismiss = { sheets = emptyList() },
            onOpen = ::open,
            onJumpToLine = {
                sheets = emptyList()
                navigator.go(Tier.Text, index.lineFocus(it))
            },
            lens = navigator.lens,
            onLens = {
                sheets = emptyList()
                navigator.useLens(it)
            },
            updates = updates,
            copyState = copyState,
            onImport = { picker.launch(arrayOf("*/*")) },
            onRemoveCopy = {
                store.remove(play.meta.id)
                copyState = CopyState.None
            },
        )
    }

    val chapter = copyChapter
    if (ownCopy != null && chapter != null) {
        CopyChapterScreen(
            play = play,
            copy = ownCopy,
            sceneIndex = chapter,
            prefs = prefs,
            onScene = { copyChapter = it },
            onCharacter = { open(Sheet.Profile(it, chapter)) },
            onClose = {
                copyChapter = null
                navigator.go(Tier.Text, index.sceneStart(chapter))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    play: Play,
    tier: Tier,
    lens: Lens?,
    readingScene: Scene,
    scrollBehavior: TopAppBarScrollBehavior?,
    updateAvailable: Boolean,
    onUp: () -> Unit,
    onTitle: () -> Unit,
    onOpen: (Sheet) -> Unit,
) {
    val (title, subtitle) = when (tier) {
        Tier.Idea -> play.meta.title to "${play.meta.author} · ${play.meta.genre}"
        Tier.Text -> play.terms.place(readingScene) to readingScene.place
        Tier.Moments -> "Ключові моменти" to tierSubtitle(play, tier)
        else -> play.terms.tier(tier) to tierSubtitle(play, tier)
    }
    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (tier == Tier.Idea) "До бібліотеки" else "На рівень вище",
                )
            }
        },
        title = {
            Column(Modifier.clickable(enabled = tier == Tier.Text, onClick = onTitle)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            val guide = play.meta.guide
            if (guide != null) {
                IconButton(onClick = { onOpen(Sheet.Guide) }) {
                    Icon(Icons.Filled.Info, contentDescription = guideTitle(guide))
                }
            }
            LensButton(lens) { onOpen(Sheet.Lens) }
            IconButton(onClick = { onOpen(Sheet.AllCharacters) }) {
                Icon(Icons.Filled.Person, contentDescription = "Усі персонажі")
            }
            IconButton(onClick = { onOpen(Sheet.Settings) }) {
                BadgedBox(badge = { if (updateAvailable) Badge() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Налаштування")
                }
            }
        },
    )
}
