package ua.reader.othello

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Settings of the whole library; a null [sp] keeps them in memory only (used by tests). */
class Prefs(private val sp: SharedPreferences?) {
    var fontSize by mutableFloatStateOf(sp?.getFloat("fontSize", 18f) ?: 18f)
        private set
    var showOriginal by mutableStateOf(sp?.getBoolean("showOriginal", false) ?: false)
        private set
    var highlightNames by mutableStateOf(sp?.getBoolean("highlightNames", true) ?: true)
        private set
    var startAtIdea by mutableStateOf(sp?.getBoolean("startAtIdea", true) ?: true)
        private set

    /** The personal pyramid shows where along the story the character is, instead of how much. */
    var pyramidTimeline by mutableStateOf(sp?.getBoolean("pyramidTimeline", false) ?: false)
        private set

    val lastUpdateCheck get() = sp?.getLong("lastUpdateCheck", 0L) ?: 0L

    fun updateFontSize(v: Float) { fontSize = v; sp?.edit()?.putFloat("fontSize", v)?.apply() }
    fun updateShowOriginal(v: Boolean) { showOriginal = v; sp?.edit()?.putBoolean("showOriginal", v)?.apply() }
    fun updateHighlightNames(v: Boolean) { highlightNames = v; sp?.edit()?.putBoolean("highlightNames", v)?.apply() }
    fun updateStartAtIdea(v: Boolean) { startAtIdea = v; sp?.edit()?.putBoolean("startAtIdea", v)?.apply() }
    fun updatePyramidTimeline(v: Boolean) { pyramidTimeline = v; sp?.edit()?.putBoolean("pyramidTimeline", v)?.apply() }
    fun updateLastUpdateCheck(v: Long) { sp?.edit()?.putLong("lastUpdateCheck", v)?.apply() }

    /**
     * Up to 1.3 the app held one book and kept its reading position and lens here. Hands them
     * over once, to be moved into that book's own [BookPrefs].
     */
    fun takeSingleBookState(): Map<String, Any>? {
        val sp = sp ?: return null
        val keys = listOf("line", "row", "offset", "lens").filter(sp::contains)
        if (keys.isEmpty()) return null
        val state = keys.associateWith { sp.all.getValue(it)!! }
        sp.edit()?.apply { keys.forEach(::remove) }?.apply()
        return state
    }
}

/** What the library remembers about one book: where the reader is and whose eyes they read with. */
class BookPrefs(private val sp: SharedPreferences?) {
    /** Whose eyes the story was last seen with; null for the author. */
    val lens: String? get() = sp?.getString("lens", null)

    /** The reading position as a line id; before 1.3 a row of the full text was saved instead ([legacyRow]). */
    val savedLine: Int? get() = sp?.takeIf { it.contains("line") }?.getInt("line", 1)
    val legacyRow get() = sp?.getInt("row", 0) ?: 0
    val savedOffset get() = sp?.getInt("offset", 0) ?: 0

    /** Share of the book read, 0..1, shown on the library shelf. */
    val progress get() = sp?.getFloat("progress", 0f) ?: 0f

    fun updateLens(id: String?) { sp?.edit()?.putString("lens", id)?.apply() }

    fun savePosition(line: Int, offset: Int, progress: Float) {
        sp?.edit()?.putInt("line", line)?.putInt("offset", offset)?.putFloat("progress", progress)?.apply()
    }

    /** Takes over the state that [Prefs.takeSingleBookState] handed out. */
    fun adopt(state: Map<String, Any>) {
        val edit = sp?.edit() ?: return
        for ((key, value) in state) when (value) {
            is Int -> edit.putInt(key, value)
            is String -> edit.putString(key, value)
        }
        edit.apply()
    }
}
