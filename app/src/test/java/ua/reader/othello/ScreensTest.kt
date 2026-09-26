package ua.reader.othello

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Renders the main screens on the JVM so the UI can be checked without a device. */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 100.0,
    )

    private val play = Play.parse(
        File("src/main/assets/play.json").readText(),
        File("src/main/assets/characters.json").readText(),
    )
    private val layout = ReaderLayout(play)
    private val temptationScene = play.scenes.first { it.act == 3 && it.number == 3 }

    @Test
    fun data_isComplete() {
        assertEquals(15, play.scenes.size)
        assertEquals(1391, play.scenes.sumOf { it.lines.size })
        for (scene in play.scenes) for (line in scene.lines) {
            assertTrue("line ${line.id} has no translation", line.uk.isNotBlank())
            if (!line.isDirection) assertTrue("unknown speaker ${line.speaker}", line.speaker in play.characters)
        }
        for (c in play.characters.values) for (r in c.relations) {
            assertTrue("${c.id} -> ${r.characterId}", r.characterId in play.characters)
        }
    }

    @Test
    fun nameRegex_matchesInflectedNamesOnly() {
        val regex = play.nameRegex!!
        val found = regex.findAll("Мавр кохає Дездемону, а Яго радить маврові; Мавританія — ні.")
            .map { play.characterForName(it.value) }.toList()
        assertEquals(listOf("othello", "desdemona", "iago", "othello"), found)
    }

    @Test
    fun reader_start() = snapReader(row = 0)

    @Test
    fun reader_temptationScene() = snapReader(row = layout.lineRow.getValue(542))

    @Test
    fun reader_withOriginal() = snapReader(row = layout.lineRow.getValue(542), original = true)

    @Test
    fun reader_dark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapReader(row = layout.lineRow.getValue(1195))
    }

    @Test
    fun sheet_profileIago() = snapSheet(Sheet.Profile("iago"))

    @Test
    fun sheet_profileDesdemona() = snapSheet(Sheet.Profile("desdemona"))

    @Test
    fun sheet_cast() = snapSheet(Sheet.Cast)

    @Test
    fun sheet_contents() = snapSheet(Sheet.Contents)

    @Test
    fun sheet_settings() = snapSheet(Sheet.Settings)

    private fun snapReader(row: Int, original: Boolean = false) {
        val prefs = Prefs(FakePrefs(mapOf("row" to row, "showOriginal" to original)))
        paparazzi.snapshot { TestHost { ReaderApp(play, prefs, UpdateController(null, prefs)) } }
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

    @Test
    fun versions_compareNumerically() {
        assertTrue(Versions.compare("1.10.0", "1.9.2") > 0)
        assertTrue(Versions.compare("v1.1", "1.1.0") == 0)
        assertTrue(Versions.compare("1.0", "1.1.0") < 0)
    }

    @Test
    fun githubRelease_parsesApkAsset() {
        val json = """
            {"tag_name":"v1.2.0","body":"Нове","assets":[
              {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":1},
              {"name":"Othello-Reader.apk","browser_download_url":"https://x/app.apk","size":1234}]}
        """.trimIndent()
        assertEquals(Release("1.2.0", "Нове", "https://x/app.apk", 1234), parseGithubRelease(json))
        assertEquals(null, parseGithubRelease("""{"tag_name":"v1","body":"","assets":[]}"""))
    }

    private val sampleRelease = Release(
        version = "1.2.0",
        notes = "• Виправлено помилки перекладу в акті IV\n• Нові профілі другорядних персонажів",
        apkUrl = "https://example.com/app.apk",
        apkSize = 1_300_000,
    )

    private fun snapSheet(sheet: Sheet, update: Release? = null) {
        val prefs = Prefs(null)
        val updates = UpdateController(null, prefs).apply { available = update }
        paparazzi.snapshot {
            OthelloTheme {
                SheetFrame {
                    SheetContent(play, prefs, updates, sheet, canGoBack = false, currentScene = temptationScene)
                }
            }
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
