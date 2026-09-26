package ua.reader.othello

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(
    play: Play,
    prefs: Prefs,
    sheet: Sheet,
    canGoBack: Boolean,
    currentScene: Scene,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (Sheet) -> Unit,
    onJumpToLine: (Int) -> Unit,
    lens: String?,
    onLens: (String?) -> Unit,
    updates: UpdateController,
    copyState: CopyState = CopyState.None,
    onImport: () -> Unit = {},
    onRemoveCopy: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (sheet == Sheet.Guide) GuideSheet(play, copyState, onImport, onRemoveCopy)
            else SheetContent(play, prefs, updates, sheet, canGoBack, currentScene, onBack, onOpen, onJumpToLine, lens, onLens)
        }
    }
}

@Composable
fun SheetContent(
    play: Play,
    prefs: Prefs,
    updates: UpdateController,
    sheet: Sheet,
    canGoBack: Boolean,
    currentScene: Scene,
    onBack: () -> Unit = {},
    onOpen: (Sheet) -> Unit = {},
    onJumpToLine: (Int) -> Unit = {},
    lens: String? = null,
    onLens: (String?) -> Unit = {},
) {
    when (sheet) {
        is Sheet.Profile -> ProfileSheet(
            play, sheet.id, sheet.scene?.let(play.scenes::get) ?: currentScene, canGoBack, onBack, onOpen, onJumpToLine,
            onLens = onLens.takeIf { sheet.id in play.perspectives && sheet.id != lens },
        )
        Sheet.Cast -> CastSheet(play, currentScene, canGoBack, onBack, onOpen)
        Sheet.AllCharacters -> AllCharactersSheet(play, canGoBack, onBack, onOpen)
        Sheet.Settings -> SettingsSheet(prefs, updates)
        Sheet.Lens -> LensChooser(play, lens, onLens)
        Sheet.Guide -> {}
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null, canGoBack: Boolean = false, onBack: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (canGoBack) 4.dp else 24.dp, end = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CharacterRow(character: Character, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(character, 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(character.name, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CastSheet(play: Play, scene: Scene, canGoBack: Boolean, onBack: () -> Unit, onOpen: (Sheet) -> Unit) {
    SheetTitle(
        "Хто тут",
        "${play.terms.place(scene)} · ${scene.place}",
        canGoBack, onBack,
    )
    val cast = play.speakersOf(scene).filter { it.main || it.arc.isNotEmpty() || it.id in MINOR_WITH_ROLE }
    for (c in cast) CharacterRow(c, c.role) { onOpen(Sheet.Profile(c.id)) }
    val others = play.speakersOf(scene) - cast.toSet()
    if (others.isNotEmpty()) {
        Text(
            "Також: " + others.joinToString { it.name.lowercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
    TextButton(onClick = { onOpen(Sheet.AllCharacters) }, modifier = Modifier.padding(horizontal = 12.dp)) {
        Text("Усі персонажі твору")
    }
}

private val MINOR_WITH_ROLE = setOf("duke", "clown")

@Composable
private fun AllCharactersSheet(play: Play, canGoBack: Boolean, onBack: () -> Unit, onOpen: (Sheet) -> Unit) {
    SheetTitle("Дійові особи", canGoBack = canGoBack, onBack = onBack)
    val all = play.characters.values.filter { it.main || it.arc.isNotEmpty() }
    for (c in all.filter { it.main }) CharacterRow(c, c.role) { onOpen(Sheet.Profile(c.id)) }
    Text(
        "Другорядні",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
    for (c in all.filter { !it.main }) CharacterRow(c, c.role) { onOpen(Sheet.Profile(c.id)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSheet(
    play: Play,
    id: String,
    scene: Scene,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpen: (Sheet) -> Unit,
    onJumpToLine: (Int) -> Unit,
    onLens: ((String?) -> Unit)?,
) {
    val c = play.characters[id] ?: return
    val appearance = play.appearances[id]
    var spoilers by remember(id) { mutableStateOf(false) }
    val color = c.color.forTheme()

    Row(
        Modifier.fillMaxWidth().padding(start = if (canGoBack) 4.dp else 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        }
        Avatar(c, 56)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif, color = color)
            Text(c.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val perspective = play.perspectives[id]
    if (onLens != null && perspective != null) {
        OutlinedButton(
            onClick = { onLens(id) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            EyeIcon(color, Modifier.size(width = 18.dp, height = 12.dp))
            Spacer(Modifier.width(8.dp))
            Text("Подивитися на історію очима ${perspective.genitive}")
        }
    }

    Section("Хто це") {
        Text(c.who, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Serif)
    }

    if (c.traits.isNotEmpty()) {
        FlowRow(
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (t in c.traits) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        t,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (c.arc.isNotEmpty()) {
        val known = c.arc.filterKeys { it <= scene.act }.toSortedMap()
        val later = c.arc.filterKeys { it > scene.act }.toSortedMap()
        Section("Що відомо на цей момент", "до ${play.terms.upTo(scene.act)} включно") {
            if (known.isEmpty()) {
                Text(
                    "Цей персонаж ще не з'являвся на сцені.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for ((act, text) in known) ArcEntry(play.terms.actShort(act), text, color)
            if (later.isNotEmpty()) {
                if (spoilers) {
                    for ((act, text) in later) ArcEntry(play.terms.actShort(act), text, MaterialTheme.colorScheme.outline, spoiler = true)
                    TextButton(onClick = { spoilers = false }) { Text("Сховати, що буде далі") }
                } else {
                    OutlinedButton(onClick = { spoilers = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Показати, що буде далі (спойлер)")
                    }
                }
            }
        }
    }

    if (c.relations.isNotEmpty()) {
        Section("Стосунки") {
            for (r in c.relations) {
                val other = play.characters[r.characterId] ?: continue
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(Sheet.Profile(other.id, scene.index)) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(other, 32)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(other.name, style = MaterialTheme.typography.titleSmall, color = other.color.forTheme())
                        Text(r.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    if (c.quote != null) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(color))
            Text(
                "«${c.quote}»",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }

    if (appearance != null) {
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        val first = play.scenes[appearance.firstScene]
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${play.terms.line[2].replaceFirstChar(Char::uppercase)} у творі: ${appearance.lines}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Уперше: ${play.terms.place(first)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onJumpToLine(appearance.firstLineId) }) { Text("Перейти") }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    "  · $subtitle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ArcEntry(act: String, text: String, accent: androidx.compose.ui.graphics.Color, spoiler: Boolean = false) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier
                .size(width = 44.dp, height = 26.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(act, color = accent, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Serif)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (spoiler) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SettingsSheet(prefs: Prefs, updates: UpdateController) {
    SheetTitle("Налаштування читання")
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text("Розмір шрифту: ${prefs.fontSize.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = prefs.fontSize,
            onValueChange = { prefs.updateFontSize(it.roundToInt().toFloat()) },
            valueRange = 14f..28f,
            steps = 13,
        )
        Text(
            "Так виглядатиме текст.",
            fontFamily = FontFamily.Serif,
            fontSize = prefs.fontSize.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        SettingSwitch(
            "Показувати оригінал",
            "Текст мовою оригіналу під кожною реплікою",
            prefs.showOriginal,
            prefs::updateShowOriginal,
        )
        SettingSwitch(
            "Виділяти імена в тексті",
            "Натисніть на ім'я, щоб дізнатися, хто це",
            prefs.highlightNames,
            prefs::updateHighlightNames,
        )
        SettingSwitch(
            "Починати з вершини піраміди",
            "Під час запуску книги показувати її ідею, а не текст",
            prefs.startAtIdea,
            prefs::updateStartAtIdea,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        UpdateSection(updates)
    }
}

@Composable
private fun UpdateSection(updates: UpdateController) {
    val scope = rememberCoroutineScope()
    val available = updates.available
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Версія ${updates.currentVersion}", style = MaterialTheme.typography.bodyLarge)
            val status = when {
                available != null -> "Доступна версія ${available.version}"
                updates.ui == UpdateUi.Checking -> "Перевіряю…"
                updates.ui == UpdateUi.UpToDate -> "У вас остання версія"
                else -> "Оновлення перевіряються раз на день"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (available != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (available != null) {
            Button(onClick = { updates.ui = UpdateUi.Available(available) }) { Text("Оновити") }
        } else {
            OutlinedButton(
                onClick = { updates.check(scope) },
                enabled = updates.ui != UpdateUi.Checking,
            ) { Text("Перевірити") }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
