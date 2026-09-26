package ua.reader.othello

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** The app shell: one tier of the pyramid at a time, the tier bar at the bottom, sheets on top. */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun App(
    play: Play,
    prefs: Prefs,
    updates: UpdateController,
    startTier: Tier = if (prefs.startAtIdea) Tier.Idea else Tier.Text,
) {
    val index = remember(play) { StoryIndex(play) }
    val navigator = remember {
        val line = prefs.savedLine?.takeIf(play.lines::containsKey)
            ?: ReaderLayout(play).focusAt(prefs.legacyRow).line
        Navigator(startTier, index.lineFocus(line), prefs.lens?.takeIf(play.perspectives::containsKey))
    }
    val lens = remember(navigator.lens) { navigator.lens?.let(play.perspectives::get)?.let { Lens(play, it) } }
    val layout = remember(lens) { ReaderLayout(play, lens) }
    // A new lens folds the text differently, so the list starts over at the same place in the story.
    val savedOffset = remember { intArrayOf(prefs.savedOffset) }
    val listState = remember(layout) {
        LazyListState(layout.rowFor(navigator.readerFocus), savedOffset[0]).also { savedOffset[0] = 0 }
    }
    val scope = rememberCoroutineScope()
    var sheets by remember { mutableStateOf(listOf<Sheet>()) }
    val tier = navigator.tier

    LaunchedEffect(updates) { updates.checkOnLaunch(scope) }
    LaunchedEffect(navigator.lens) { prefs.updateLens(navigator.lens) }

    LaunchedEffect(listState, layout) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { navigator.onReaderMoved(layout.focusAt(it)) }
    }
    LaunchedEffect(listState, layout) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(400)
            .collect { (row, offset) -> prefs.savePosition(layout.focusAt(row).line, offset) }
    }
    // Zooming into the text from a tier opens the text at the chosen place.
    LaunchedEffect(tier, navigator.pendingJump, layout) {
        if (tier == Tier.Text && navigator.pendingJump) {
            listState.scrollToItem(layout.rowFor(navigator.focus))
            navigator.onJumped()
        }
    }

    BackHandler(enabled = tier != Tier.Idea) { navigator.zoomOut() }

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
                    onUp = { navigator.zoomOut() },
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
                PyramidNavItems(tier) { navigator.go(it) }
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
        )
    }

    UpdateDialogs(updates, scope)
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
        Tier.Idea -> "Отелло" to "Вільям Шекспір · трагедія"
        Tier.Text -> "Акт ${ROMAN[readingScene.act]} · Сцена ${readingScene.number}" to readingScene.place
        Tier.Moments -> "Ключові моменти" to tierSubtitle(play, tier)
        else -> tier.label to tierSubtitle(play, tier)
    }
    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            if (tier != Tier.Idea) {
                IconButton(onClick = onUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "На рівень вище")
                }
            }
        },
        title = {
            Column(Modifier.clickable(enabled = tier == Tier.Text, onClick = onTitle)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
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
