package ua.reader.othello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OthelloTheme {
                val prefs = remember { Prefs(getSharedPreferences("reader", MODE_PRIVATE)) }
                val updates = remember { UpdateController(Updater(applicationContext, BuildConfig.GITHUB_REPO), prefs) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(updates) { updates.checkOnLaunch(scope) }

                val shelf = remember { LibraryEntry.load(applicationContext) }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                val open = shelf.firstOrNull { it.id == openId }
                if (open == null) {
                    LibraryScreen(
                        shelf = shelf,
                        shelfState = { bookPrefs(it, prefs).let { b -> ShelfState(b.progress, b.savedLine != null || b.legacyRow > 0) } },
                        prefs = prefs,
                        updates = updates,
                        onOpen = { openId = it.id },
                    )
                } else {
                    BookScreen(open, prefs, updates) { openId = null }
                }
                UpdateDialogs(updates, scope)
            }
        }
    }

    private fun bookPrefs(id: String, prefs: Prefs) = BookPrefs(getSharedPreferences("book_$id", MODE_PRIVATE)).also {
        // Before the library the app held Othello alone; its reading position moves into Othello's own settings.
        if (id == "othello") prefs.takeSingleBookState()?.let(it::adopt)
    }

    @Composable
    private fun BookScreen(entry: LibraryEntry, prefs: Prefs, updates: UpdateController, onLibrary: () -> Unit) {
        val book = remember(entry.id) { bookPrefs(entry.id, prefs) }
        val play by produceState<Play?>(null, entry.id) {
            value = withContext(Dispatchers.Default) { Play.load(applicationContext, entry.file) }
        }
        val loaded = play
        if (loaded == null) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            App(loaded, prefs, book, updates, onLibrary = onLibrary)
        }
    }
}
