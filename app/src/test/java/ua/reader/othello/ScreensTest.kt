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
    fun lensPyramid_shareOthello() = snapLensPyramid("othello", timeline = false)

    @Test
    fun lensPyramid_timelineOthello() = snapLensPyramid("othello", timeline = true)

    @Test
    fun lensPyramid_timelineDesdemona() = snapLensPyramid("desdemona", timeline = true)

    @Test
    fun lensPyramid_timelineBiancaDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapLensPyramid("bianca", timeline = true)
    }

    private fun snapLensPyramid(id: String, timeline: Boolean) {
        paparazzi.snapshot {
            TestHost {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        LensPyramid(play, TestData.lens(id), readingLine = 542, timeline = timeline, onTimeline = {}, onGo = { _, _ -> })
                    }
                }
            }
        }
    }

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
    private fun snapApp(tier: Tier, row: Int = readingRow, original: Boolean = false, line: Int? = null, lens: String? = null, book: Play = play) {
        val prefs = Prefs(FakePrefs(mapOf("showOriginal" to original)))
        val saved = BookPrefs(
            FakePrefs(
                buildMap {
                    if (line != null) put("line", line) else put("row", row)
                    if (lens != null) put("lens", lens)
                },
            ),
        )
        paparazzi.snapshot { TestHost { App(book, prefs, saved, UpdateController(null, prefs), startTier = tier) } }
    }

    // A guide to a work under copyright: the pyramid in our own words, no text of the novel.
    private val guide = TestData.book(TestData.shelf.first { it.id == "la-confidential" })
    private fun guideBeat(act: Int, number: Int, n: Int = 0) = guide.scenes.first { it.act == act && it.number == number }.lines[n].id

    @Test
    fun guide0_idea() = snapApp(Tier.Idea, line = guideBeat(3, 3), book = guide)

    @Test
    fun guide1_parts() = snapApp(Tier.Acts, line = guideBeat(3, 3), book = guide)

    @Test
    fun guide2_chapters() = snapApp(Tier.Scenes, line = guideBeat(3, 3), book = guide)

    @Test
    fun guide3_moments() = snapApp(Tier.Moments, line = guideBeat(3, 3), book = guide)

    @Test
    fun guide4_retelling() = snapApp(Tier.Text, line = guideBeat(3, 3), book = guide)

    @Test
    fun guide4_retellingThroughWhite() = snapApp(Tier.Text, line = guideBeat(3, 4, 1), lens = "white", book = guide)

    @Test
    fun guideLens_exley() = snapApp(Tier.Idea, line = guideBeat(5, 5), lens = "exley", book = guide)

    @Test
    fun guide_lensChooser() = snapSheet(Sheet.Lens, book = guide, scene = guide.scenes.first { it.act == 3 && it.number == 3 })

    @Test
    fun guideLens_dudley() = snapApp(Tier.Idea, line = guideBeat(3, 3, 3), lens = "smith", book = guide)

    @Test
    fun guideLens_lynnScenes() = snapApp(Tier.Scenes, line = guideBeat(3, 20), lens = "bracken", book = guide)

    @Test
    fun guideLens_inezRetelling() = snapApp(Tier.Text, line = guideBeat(3, 7), lens = "soto", book = guide)

    @Test
    fun guide_sheetWithCopy() {
        val copy = OwnCopy(guide.scenes.map { CopySection("Розділ", listOf("…")) })
        paparazzi.snapshot { TestHost { SheetFrame { GuideSheet(guide, CopyState.Ready(copy), {}, {}) } } }
    }

    @Test
    fun guide_sheetFailed() = paparazzi.snapshot {
        TestHost { SheetFrame { GuideSheet(guide, CopyState.Failed("Розпізнано 14 з 83 розділів: після «Глава 13» очікувався календар."), {}, {}) } }
    }

    @Test
    fun guide_ownCopyChapter() {
        // Placeholder paragraphs stand in for the reader's own file; the app ships no text of the novel.
        val copy = OwnCopy(guide.scenes.map { CopySection("ГЛАВА", listOf("Тут буде текст із вашого файлу. Ексле, Вайт і Дадлі підсвічуються.", "Другий абзац.")) })
        paparazzi.snapshot {
            TestHost { CopyChapterScreen(guide, copy, guide.scenes.first { it.act == 3 && it.number == 3 }.index, Prefs(null), {}, {}, {}) }
        }
    }

    // A guide to a public-domain novel, retold in our own words, with cards that take each trap apart.
    private val monteCristo = TestData.book(TestData.shelf.first { it.id == "monte-cristo" })
    private fun chapterBeat(chapter: Int, n: Int = 0) = monteCristo.scenes.first { it.short == "$chapter" }.lines[n].id

    @Test
    fun monteCristo0_idea() = snapApp(Tier.Idea, line = chapterBeat(4), book = monteCristo)

    @Test
    fun monteCristo1_parts() = snapApp(Tier.Acts, line = chapterBeat(4), book = monteCristo)

    @Test
    fun monteCristo4_trapCard() = snapApp(Tier.Text, line = chapterBeat(4, 3), book = monteCristo)

    @Test
    fun monteCristo4_trapCardDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Text, line = chapterBeat(4, 3), book = monteCristo)
    }

    @Test
    fun monteCristo4_trapThroughTheCount() = snapApp(Tier.Text, line = chapterBeat(26, 2), lens = "dantes", book = monteCristo)

    @Test
    fun monteCristoLens_count() = snapApp(Tier.Idea, line = chapterBeat(63), lens = "dantes", book = monteCristo)

    @Test
    fun monteCristo_guideSheet() = paparazzi.snapshot {
        TestHost { SheetFrame { GuideSheet(monteCristo, CopyState.None, {}, {}) } }
    }

    // A guide to a whodunit: spoiler-safe cards while the story runs, the full breakdown in the last part.
    private val christie = TestData.book(TestData.shelf.first { it.id == "and-then-there-were-none" })
    private fun unitBeat(short: String, n: Int = 0) = christie.scenes.first { it.short == short }.lines[n].id

    @Test
    fun christie0_idea() = snapApp(Tier.Idea, line = unitBeat("3"), book = christie)

    @Test
    fun christie1_parts() = snapApp(Tier.Acts, line = unitBeat("3"), book = christie)

    @Test
    fun christie4_storyCard() = snapApp(Tier.Text, line = unitBeat("13", 4), book = christie)

    @Test
    fun christie4_trapBreakdown() = snapApp(Tier.Text, line = unitBeat("П8", 1), book = christie)

    @Test
    fun christie_lensChooser() = snapSheet(Sheet.Lens, book = christie, scene = christie.scenes.first { it.short == "9" })

    // Real history laid out as a pyramid, with cards that separate the record from the legend.
    private val tombstone = TestData.book(TestData.shelf.first { it.id == "tombstone-1881" })
    private fun chronicleBeat(short: String, n: Int = 0) = tombstone.scenes.first { it.short == short }.lines[n].id

    @Test
    fun tombstone0_idea() = snapApp(Tier.Idea, line = chronicleBeat("18"), book = tombstone)

    @Test
    fun tombstone1_parts() = snapApp(Tier.Acts, line = chronicleBeat("18"), book = tombstone)

    @Test
    fun tombstone4_gunfight() = snapApp(Tier.Text, line = chronicleBeat("18", 7), book = tombstone)

    @Test
    fun tombstoneLens_ike() = snapApp(Tier.Text, line = chronicleBeat("18", 1), lens = "ike", book = tombstone)

    // A TV series retold with three kinds of cards: shadow power, the plan that outlives its author, the real history.
    private val deadwood = TestData.book(TestData.shelf.first { it.id == "deadwood" })
    private fun episodeBeat(short: String, n: Int = 0) = deadwood.scenes.first { it.short == short }.lines[n].id

    @Test
    fun deadwood0_idea() = snapApp(Tier.Idea, line = episodeBeat("1×6"), book = deadwood)

    @Test
    fun deadwood1_parts() = snapApp(Tier.Acts, line = episodeBeat("1×6"), book = deadwood)

    @Test
    fun deadwood4_threeCards() = snapApp(Tier.Text, line = episodeBeat("1×9", 3), book = deadwood)

    @Test
    fun deadwood4_threeCardsDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Text, line = episodeBeat("1×9", 3), book = deadwood)
    }

    @Test
    fun deadwood4_planAfterHearstLeaves() = snapApp(Tier.Text, line = episodeBeat("3×12", 6), book = deadwood)

    @Test
    fun deadwoodLens_hearst() = snapApp(Tier.Text, line = episodeBeat("Ф2"), lens = "hearst", book = deadwood)

    @Test
    fun deadwood_guideSheet() = paparazzi.snapshot {
        TestHost { SheetFrame { GuideSheet(deadwood, CopyState.None, {}, {}) } }
    }

    // A film guide with four kinds of cards, among them how the comic told it and what really happened in 1605.
    private val vendetta = TestData.book(TestData.shelf.first { it.id == "v-for-vendetta" })
    private fun vendettaBeat(short: String, n: Int = 0) = vendetta.scenes.first { it.short == short }.lines[n].id

    @Test
    fun vendetta0_idea() = snapApp(Tier.Idea, line = vendettaBeat("4"), book = vendetta)

    @Test
    fun vendetta1_parts() = snapApp(Tier.Acts, line = vendettaBeat("4"), book = vendetta)

    @Test
    fun vendetta4_prologue() = snapApp(Tier.Text, line = vendettaBeat("Пр", 2), book = vendetta)

    @Test
    fun vendetta4_rookwood() = snapApp(Tier.Text, line = vendettaBeat("16", 2), book = vendetta)

    @Test
    fun vendetta4_trainDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Text, line = vendettaBeat("22", 4), book = vendetta)
    }

    @Test
    fun vendettaLens_evey() = snapApp(Tier.Text, line = vendettaBeat("14"), lens = "evey", book = vendetta)

    // A five-season series with flashbacks, and a closing part on what really happened.
    private val boardwalk = TestData.book(TestData.shelf.first { it.id == "boardwalk-empire" })
    private fun boardwalkBeat(short: String, n: Int = 0) = boardwalk.scenes.first { it.short == short }.lines[n].id

    @Test
    fun boardwalk0_idea() = snapApp(Tier.Idea, line = boardwalkBeat("1×1"), book = boardwalk)

    @Test
    fun boardwalk1_parts() = snapApp(Tier.Acts, line = boardwalkBeat("1×1"), book = boardwalk)

    @Test
    fun boardwalk4_prohibition() = snapApp(Tier.Text, line = boardwalkBeat("1×1", 5), book = boardwalk)

    @Test
    fun boardwalk4_theDeal1897() = snapApp(Tier.Text, line = boardwalkBeat("5×8"), book = boardwalk)

    @Test
    fun boardwalk4_conferenceDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Text, line = boardwalkBeat("П3", 2), book = boardwalk)
    }

    @Test
    fun boardwalkLens_nucky() = snapApp(Tier.Text, line = boardwalkBeat("2×12", 6), lens = "nucky", book = boardwalk)

    // A film trilogy told in story order, with five kinds of cards and a closing part on the real Mafia.
    private val godfather = TestData.book(TestData.shelf.first { it.id == "the-godfather" })
    private fun godfatherBeat(short: String, n: Int = 0) = godfather.scenes.first { it.short == short }.lines[n].id

    @Test
    fun godfather0_idea() = snapApp(Tier.Idea, line = godfatherBeat("6"), book = godfather)

    @Test
    fun godfather1_parts() = snapApp(Tier.Acts, line = godfatherBeat("6"), book = godfather)

    @Test
    fun godfather4_weddingHierarchy() = snapApp(Tier.Text, line = godfatherBeat("6", 9), book = godfather)

    @Test
    fun godfather4_baptism() = snapApp(Tier.Text, line = godfatherBeat("20", 1), book = godfather)

    @Test
    fun godfather4_havanaDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        snapApp(Tier.Text, line = godfatherBeat("23", 6), book = godfather)
    }

    @Test
    fun godfather4_realRanks() = snapApp(Tier.Text, line = godfatherBeat("П2", 4), book = godfather)

    @Test
    fun godfatherLens_michael() = snapApp(Tier.Text, line = godfatherBeat("20", 8), lens = "michael", book = godfather)

    @Test
    fun library_shelf() {
        val prefs = Prefs(null)
        paparazzi.snapshot {
            TestHost {
                LibraryScreen(TestData.shelf, shelfState = { ShelfState(0.38f, true) }, prefs = prefs, updates = UpdateController(null, prefs), onOpen = {})
            }
        }
    }

    @Test
    fun library_shelfDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        val prefs = Prefs(null)
        paparazzi.snapshot {
            TestHost {
                LibraryScreen(TestData.shelf, shelfState = { ShelfState(0f, false) }, prefs = prefs, updates = UpdateController(null, prefs), onOpen = {})
            }
        }
    }

    private fun snapSheet(sheet: Sheet, update: Release? = null, book: Play = play, scene: Scene = temptationScene) {
        val prefs = Prefs(null)
        val updates = UpdateController(null, prefs).apply { available = update }
        paparazzi.snapshot {
            TestHost {
                SheetFrame {
                    SheetContent(book, prefs, updates, sheet, canGoBack = false, currentScene = scene)
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
