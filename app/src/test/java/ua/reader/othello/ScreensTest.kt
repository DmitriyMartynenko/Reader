package ua.reader.othello

import android.content.SharedPreferences
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test

/** Renders the screens on the JVM so the UI can be checked without a device. */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 100.0,
    )

    private val play = TestData.play
    private val layout = ReaderLayout(play)
    private val temptationScene = play.scenes.first { it.act == 3 && it.number == 3 }

    // The reader sits in the temptation scene for every tier, so "ви тут" shows up on each level.
    private val readingRow = layout.lineRow.getValue(542)

    @Test
    fun tier0_idea() = snapApp(Tier.Idea)

    @Test
    fun tier1_acts() = snapApp(Tier.Acts)

    @Test
    fun tier2_scenes() = snapApp(Tier.Scenes)

    @Test
    fun tier3_moments() = snapApp(Tier.Moments)

    @Test
    fun tier4_text() = snapApp(Tier.Text)

    @Test
    fun tier4_textAtStart() = snapApp(Tier.Text, row = 0)

    @Test
    fun tier4_textWithOriginal() = snapApp(Tier.Text, original = true)

    @Test
    fun tier4_textAtKeyMoment() = snapApp(Tier.Text, row = layout.lineRow.getValue(1195) - 1)

    @Test
    fun tier0_ideaDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Idea)
    }

    @Test
    fun tier3_momentsDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Moments)
    }

    @Test
    fun lens0_ideaOthello() = snapApp(Tier.Idea, lens = "othello")

    @Test
    fun lens0_ideaIagoDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Idea, lens = "iago")
    }

    @Test
    fun lens1_actsEmilia() = snapApp(Tier.Acts, line = 224, lens = "emilia")

    @Test
    fun lens2_scenesDesdemona() = snapApp(Tier.Scenes, lens = "desdemona")

    @Test
    fun lens3_momentsOthello() = snapApp(Tier.Moments, lens = "othello")

    @Test
    fun lens4_textOthelloInTrance() = snapApp(Tier.Text, line = 779, lens = "othello")

    @Test
    fun lens4_textOthelloHiding() = snapApp(Tier.Text, line = 803, lens = "othello")

    @Test
    fun lens4_textDesdemonaAsleep() = snapApp(Tier.Text, line = 1193, lens = "desdemona")

    @Test
    fun sheet_lensChooser() = snapSheet(Sheet.Lens)

    @Test
    fun sheet_profileIago() = snapSheet(Sheet.Profile("iago"))

    @Test
    fun sheet_profileFromActV() = snapSheet(Sheet.Profile("emilia", play.scenes.last().index))

    @Test
    fun sheet_cast() = snapSheet(Sheet.Cast)

    @Test
    fun sheet_settings() = snapSheet(Sheet.Settings)

    @Test
    fun sheet_settingsWithUpdate() = snapSheet(Sheet.Settings, update = sampleRelease)

    @Test
    fun dialog_updateAvailable() {
        val prefs = Prefs(null)
        val updates = UpdateController(null, prefs).apply { ui = UpdateUi.Available(sampleRelease) }
        paparazzi.snapshot {
            TestHost {
                SheetFrame { UpdateDialogs(updates, rememberCoroutineScope()) }
            }
        }
    }

    private val sampleRelease = Release(
        version = "1.2.0",
        notes = "• Виправлено помилки перекладу в акті IV\n• Нові профілі другорядних персонажів",
        apkUrl = "https://example.com/app.apk",
        apkSize = 1_300_000,
    )

    /** [row] is the pre-1.3 saved position (a row of the full text); [line] is the current one. */
    private fun snapApp(tier: Tier, row: Int = readingRow, original: Boolean = false, line: Int? = null, lens: String? = null) {
        val values = buildMap<String, Any> {
            put("showOriginal", original)
            if (line != null) put("line", line) else put("row", row)
            if (lens != null) put("lens", lens)
        }
        val prefs = Prefs(FakePrefs(values))
        paparazzi.snapshot { TestHost { App(play, prefs, UpdateController(null, prefs), startTier = tier) } }
    }

    private fun snapSheet(sheet: Sheet, update: Release? = null) {
        val prefs = Prefs(null)
        val updates = UpdateController(null, prefs).apply { available = update }
        paparazzi.snapshot {
            TestHost {
                SheetFrame {
                    SheetContent(play, prefs, updates, sheet, canGoBack = false, currentScene = temptationScene)
                }
            }
        }
    }

    /** Theme plus the activity-result registry that the update dialogs need outside a real Activity. */
    @Composable
    private fun TestHost(content: @Composable () -> Unit) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) = Unit
            }
        }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            OthelloTheme(content)
        }
    }

    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp)) { content() }
        }
    }
}

private class FakePrefs(private val values: Map<String, Any>) : SharedPreferences {
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = key in values
    override fun edit(): SharedPreferences.Editor? = null
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
