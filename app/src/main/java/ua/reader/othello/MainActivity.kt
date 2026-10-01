package ua.reader.othello

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

                // The built-in books come with the app; the reader's own live on this phone only.
                val builtIn = remember { LibraryEntry.load(applicationContext) }
                val myBooks = remember { MyBooks(File(filesDir, "library"), builtIn.map { it.id }.toSet()) }
                var mine by remember { mutableStateOf(myBooks.entries()) }
                var notice by remember { mutableStateOf<String?>(null) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        val message = withContext(Dispatchers.IO) {
                            try {
                                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                                val added = myBooks.add(bytes)
                                "${if (added.replaced) "Оновлено" else "Додано"} «${added.entry.title}»"
                            } catch (e: ImportException) {
                                e.message
                            } catch (e: Exception) {
                                "Не вдалося прочитати файл."
                            }
                        }
                        mine = myBooks.entries()
                        notice = message
                    }
                }

                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                val shelf = builtIn + mine
                val open = shelf.firstOrNull { it.id == openId }
                if (open == null) {
                    LibraryScreen(
                        shelf = shelf,
                        shelfState = { bookPrefs(it, prefs).let { b -> ShelfState(b.progress, b.savedLine != null || b.legacyRow > 0) } },
                        prefs = prefs,
                        updates = updates,
                        onOpen = { openId = it.id },
                        onAddBook = { picker.launch(arrayOf("*/*")) },
                        onRemove = { entry ->
                            removeBook(myBooks, entry.id)
                            mine = myBooks.entries()
                            notice = "Видалено «${entry.title}»"
                        },
                        notice = notice,
                        onNoticeShown = { notice = null },
                    )
                } else {
                    BookScreen(
                        open, myBooks, prefs, updates,
                        onFailed = {
                            openId = null
                            notice = "Не вдалося відкрити «${open.title}»."
                        },
                    ) { openId = null }
                }
                UpdateDialogs(updates, scope)
            }
        }
    }

    private fun bookPrefs(id: String, prefs: Prefs) = BookPrefs(getSharedPreferences("book_$id", MODE_PRIVATE)).also {
        // Before the library the app held Othello alone; its reading position moves into Othello's own settings.
        if (id == "othello") prefs.takeSingleBookState()?.let(it::adopt)
    }

    /** A removed book leaves nothing behind: its file, its reading position, an imported copy. */
    private fun removeBook(myBooks: MyBooks, id: String) {
        myBooks.remove(id)
        deleteSharedPreferences("book_$id")
        CopyStore(File(filesDir, "copies")).remove(id)
    }

    @Composable
    private fun BookScreen(
        entry: LibraryEntry,
        myBooks: MyBooks,
        prefs: Prefs,
        updates: UpdateController,
        onFailed: () -> Unit,
        onLibrary: () -> Unit,
    ) {
        val book = remember(entry.id) { bookPrefs(entry.id, prefs) }
        val play by produceState<Play?>(null, entry.id) {
            // A reader's own book was checked when added, but its file lives outside the app and may still fail.
            val loaded = withContext(Dispatchers.Default) {
                runCatching { if (entry.builtIn) Play.load(applicationContext, entry.file) else Play.parse(myBooks.read(entry)) }.getOrNull()
            }
            if (loaded == null) onFailed() else value = loaded
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
