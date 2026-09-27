package ua.reader.othello

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where the reader's own copy stands for the book sheet. */
sealed interface CopyState {
    data object None : CopyState
    data object Importing : CopyState
    data class Ready(val copy: OwnCopy) : CopyState
    data class Failed(val message: String) : CopyState
}

/** About a guide: why there is no text, and the reader's own copy where the guide takes one. */
@Composable
fun GuideSheet(play: Play, state: CopyState, onImport: () -> Unit, onRemove: () -> Unit) {
    val guide = play.meta.guide ?: return
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(guideTitle(guide), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        Text(guide.notice, style = MaterialTheme.typography.bodyMedium)
        val hint = guide.importHint ?: return@Column
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            CopyState.None -> Button(onClick = onImport) { Text("Додати свій примірник (FB2 або EPUB)") }
            CopyState.Importing -> Row { CircularProgressIndicator(); Text("  Розбираю книгу…") }
            is CopyState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onImport) { Text("Спробувати інший файл") }
            }
            is CopyState.Ready -> {
                Text(
                    "Додано: ${play.terms.scenes(state.copy.units.size)}. Текст зберігається лише на цьому телефоні.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = onRemove) { Text("Прибрати примірник") }
            }
        }
    }
}

/** What the guide sheet and its button are called: a guide that takes no copy is just a guide. */
fun guideTitle(guide: Guide) = if (guide.importHint == null) "Про путівник" else "Путівник і ваш примірник"

/** One unit of the reader's own copy, full screen, with names that open profiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyChapterScreen(
    play: Play,
    copy: OwnCopy,
    sceneIndex: Int,
    prefs: Prefs,
    onScene: (Int) -> Unit,
    onCharacter: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val scene = play.scenes[sceneIndex]
    val unit = copy.units[sceneIndex]
    val listState = rememberLazyListState()
    LaunchedEffect(sceneIndex) { listState.scrollToItem(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "До переказу") }
                },
                title = {
                    Column {
                        Text(play.terms.place(scene), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ваш примірник · ${play.pyramid.scenes[sceneIndex].title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    unit.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            itemsIndexed(unit.paragraphs) { _, p ->
                Text(
                    formatText(p, play, prefs.highlightNames, onCharacter),
                    fontFamily = FontFamily.Serif,
                    fontSize = prefs.fontSize.sp,
                    lineHeight = (prefs.fontSize * 1.45f).sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { onScene(sceneIndex - 1) }, enabled = sceneIndex > 0) { Text("← Попередній") }
                    TextButton(onClick = { onScene(sceneIndex + 1) }, enabled = sceneIndex < copy.units.lastIndex) { Text("Наступний →") }
                }
                Text(
                    "Текст із вашого файлу, показаний лише на цьому пристрої.",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}
