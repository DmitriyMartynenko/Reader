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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
                val play by produceState<Play?>(null) {
                    value = withContext(Dispatchers.Default) { Play.load(applicationContext) }
                }
                val loaded = play
                if (loaded == null) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    App(loaded, prefs, updates)
                }
            }
        }
    }
}
