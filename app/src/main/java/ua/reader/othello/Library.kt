package ua.reader.othello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The top of the whole app: the shelf of books. Every book on it is a pyramid of its own,
 * packed into one file by tools/build-book.js and listed in assets/books/library.json.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    shelf: List<LibraryEntry>,
    shelfState: (String) -> ShelfState,
    prefs: Prefs,
    updates: UpdateController,
    onOpen: (LibraryEntry) -> Unit,
) {
    var settings by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Бібліотека", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                        Text(
                            uaPlural(shelf.size, "книга", "книги", "книг"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { settings = true }) {
                        BadgedBox(badge = { if (updates.available != null) Badge() }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Налаштування")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "intro") {
                Text(
                    "Кожна книга тут — піраміда: від однієї ідеї на вершині до повного тексту в основі. Її можна читати очима кожного з героїв.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            items(shelf, key = { it.id }) { entry ->
                BookCard(entry, shelfState(entry.id)) { onOpen(entry) }
            }
            item(key = "more") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Нові книги з'являтимуться на цій полиці з оновленнями застосунку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (settings) {
        ModalBottomSheet(
            onDismissRequest = { settings = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                SettingsSheet(prefs, updates)
            }
        }
    }
}

/** What the shelf shows about a book's reading: share read, and whether it was opened at all. */
data class ShelfState(val progress: Float, val started: Boolean)

@Composable
private fun BookCard(entry: LibraryEntry, state: ShelfState, onClick: () -> Unit) {
    val progress = state.progress
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Cover(entry, Modifier.size(width = 88.dp, height = 128.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Text(
                    "${entry.author} · ${entry.genre} · ${entry.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "«${entry.idea}»",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                if (progress > 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Прочитано ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(if (state.started) "Розпочато" else "Ще не розпочато", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** A plain cloth cover in the book's colour, with its title and author stamped on it. */
@Composable
private fun Cover(entry: LibraryEntry, modifier: Modifier) {
    Box(
        modifier
            .background(entry.cover, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(4.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.author.uppercase(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    letterSpacing = 0.1.em,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp,
                )
            }
        }
    }
}
