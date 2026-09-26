package ua.reader.othello

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Reader settings; a null [sp] keeps them in memory only (used by screenshot tests). */
class Prefs(private val sp: SharedPreferences?) {
    var fontSize by mutableFloatStateOf(sp?.getFloat("fontSize", 18f) ?: 18f)
        private set
    var showOriginal by mutableStateOf(sp?.getBoolean("showOriginal", false) ?: false)
        private set
    var highlightNames by mutableStateOf(sp?.getBoolean("highlightNames", true) ?: true)
        private set
    var startAtIdea by mutableStateOf(sp?.getBoolean("startAtIdea", true) ?: true)
        private set

    /** Whose eyes the story was last seen with; null for the author. */
    val lens: String? get() = sp?.getString("lens", null)

    val lastUpdateCheck get() = sp?.getLong("lastUpdateCheck", 0L) ?: 0L

    /**
     * The reading position as a line id. Versions up to 1.2 saved a row of the full text instead;
     * that row is still readable through [legacyRow] until the first new save.
     */
    val savedLine: Int? get() = sp?.takeIf { it.contains("line") }?.getInt("line", 1)
    val legacyRow get() = sp?.getInt("row", 0) ?: 0
    val savedOffset get() = sp?.getInt("offset", 0) ?: 0

    fun updateFontSize(v: Float) { fontSize = v; sp?.edit()?.putFloat("fontSize", v)?.apply() }
    fun updateShowOriginal(v: Boolean) { showOriginal = v; sp?.edit()?.putBoolean("showOriginal", v)?.apply() }
    fun updateHighlightNames(v: Boolean) { highlightNames = v; sp?.edit()?.putBoolean("highlightNames", v)?.apply() }
    fun updateStartAtIdea(v: Boolean) { startAtIdea = v; sp?.edit()?.putBoolean("startAtIdea", v)?.apply() }
    fun updateLens(id: String?) { sp?.edit()?.putString("lens", id)?.apply() }
    fun updateLastUpdateCheck(v: Long) { sp?.edit()?.putLong("lastUpdateCheck", v)?.apply() }
    fun savePosition(line: Int, offset: Int) { sp?.edit()?.putInt("line", line)?.putInt("offset", offset)?.apply() }
}
