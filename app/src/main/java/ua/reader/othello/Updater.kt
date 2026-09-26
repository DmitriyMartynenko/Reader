package ua.reader.othello

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

object Versions {
    /** Compares dotted versions numerically ("1.10.0" > "1.9.2"); a leading "v" is ignored. */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun parts(v: String) = v.trim().trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
}

/** Parses a GitHub "latest release" response; null when the release has no APK attached. */
fun parseGithubRelease(json: String): Release? {
    val obj = JSONObject(json)
    val assets = obj.getJSONArray("assets")
    for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        if (a.getString("name").endsWith(".apk")) {
            return Release(
                version = obj.getString("tag_name").trimStart('v', 'V'),
                notes = obj.optString("body").trim(),
                apkUrl = a.getString("browser_download_url"),
                apkSize = a.optLong("size"),
            )
        }
    }
    return null
}

/** Talks to GitHub Releases and hands the downloaded APK to the system installer. */
class Updater(private val context: Context, private val repo: String) {
    private val dir get() = File(context.cacheDir, "updates")

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/$repo/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                200 -> parseGithubRelease(conn.inputStream.bufferedReader().readText())
                404 -> null // no releases published yet
                else -> throw IOException("GitHub відповів кодом ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "othello-${release.version}.apk")
        val conn = open(release.apkUrl)
        try {
            if (conn.responseCode != 200) throw IOException("Завантаження не вдалося (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            if (release.apkSize > 0 && file.length() != release.apkSize) {
                throw IOException("Файл завантажився не повністю")
            }
            file
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent() = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "OthelloReader/${BuildConfig.VERSION_NAME}")
    }
}

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val release: Release) : UpdateUi
    data class Downloading(val release: Release, val progress: Float) : UpdateUi
    data class NeedsPermission(val release: Release, val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/** Holds update state for the UI. [updater] is null in screenshot tests, where nothing hits the network. */
class UpdateController(private val updater: Updater?, private val prefs: Prefs) {
    var ui: UpdateUi by mutableStateOf(UpdateUi.Idle)

    /** A newer release found by the last check; kept after the dialog is dismissed. */
    var available: Release? by mutableStateOf(null)

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** Checks at most once a day on launch; a silent check never shows errors or "up to date". */
    fun checkOnLaunch(scope: CoroutineScope) {
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < DAY_MS) return
        check(scope, silent = true)
    }

    fun check(scope: CoroutineScope, silent: Boolean = false) {
        val updater = updater ?: return
        if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading) return
        if (!silent) ui = UpdateUi.Checking
        scope.launch {
            ui = try {
                val release = updater.latest()
                prefs.updateLastUpdateCheck(System.currentTimeMillis())
                available = release?.takeIf { Versions.compare(it.version, currentVersion) > 0 }
                when {
                    available != null -> UpdateUi.Available(available!!)
                    silent -> UpdateUi.Idle
                    else -> UpdateUi.UpToDate
                }
            } catch (e: Exception) {
                if (silent) UpdateUi.Idle else UpdateUi.Failed("Не вдалося перевірити оновлення. Перевірте інтернет.")
            }
        }
    }

    fun download(scope: CoroutineScope, release: Release) {
        val updater = updater ?: return
        ui = UpdateUi.Downloading(release, 0f)
        scope.launch {
            ui = try {
                val file = updater.download(release) { p -> ui = UpdateUi.Downloading(release, p) }
                installOrAsk(release, file)
            } catch (e: Exception) {
                UpdateUi.Failed("Не вдалося завантажити оновлення. Спробуйте ще раз.")
            }
        }
    }

    /** Called when the user comes back from the "install unknown apps" settings screen. */
    fun onPermissionResult() {
        val state = ui as? UpdateUi.NeedsPermission ?: return
        ui = installOrAsk(state.release, state.file)
    }

    fun permissionIntent() = updater?.permissionIntent()

    fun dismiss() { ui = UpdateUi.Idle }

    private fun installOrAsk(release: Release, file: File): UpdateUi {
        val updater = updater ?: return UpdateUi.Idle
        if (!updater.canInstall()) return UpdateUi.NeedsPermission(release, file)
        updater.install(file)
        return UpdateUi.Idle
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** Dialogs for an available update, its download and the install permission. */
@Composable
fun UpdateDialogs(updates: UpdateController, scope: CoroutineScope) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.onPermissionResult()
    }
    when (val state = updates.ui) {
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text("Доступна версія ${state.release.version}") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("У вас версія ${updates.currentVersion}.", style = MaterialTheme.typography.bodyMedium)
                    if (state.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Що нового", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(state.release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { updates.download(scope, state.release) }) { Text("Оновити") } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text("Пізніше") } },
        )
        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Завантаження версії ${state.release.version}") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )
        is UpdateUi.NeedsPermission -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text("Потрібен дозвіл") },
            text = {
                Text(
                    "Щоб встановити оновлення, дозвольте застосунку «Отелло» встановлювати застосунки. " +
                        "Після цього поверніться назад — встановлення почнеться автоматично.",
                )
            },
            confirmButton = {
                TextButton(onClick = { updates.permissionIntent()?.let(permissionLauncher::launch) }) {
                    Text("Відкрити налаштування")
                }
            },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text("Скасувати") } },
        )
        is UpdateUi.Failed -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text("Оновлення") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = updates::dismiss) { Text("Гаразд") } },
        )
        else -> Unit
    }
}
