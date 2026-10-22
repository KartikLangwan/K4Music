package com.k4music.android.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.k4music.android.DatabaseDependency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import com.k4music.android.Database
import com.k4music.app.R
import com.k4music.android.internal
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.query
import com.k4music.android.service.PlayerService
import com.k4music.android.transaction
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.intent
import com.k4music.android.utils.snapshotDatabase
import com.k4music.android.utils.toast
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@SuppressLint("RestrictedApi")
@Route
@Composable
fun DatabaseSettings() = with(DataPreferences) {
    val context = LocalContext.current

    val eventsCount by remember { Database.eventsCount().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val blacklistLength by remember { Database.blacklistLength().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var transferInProgress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType = "application/vnd.sqlite3")
    ) { uri ->
        if (uri == null || transferInProgress) return@rememberLauncherForActivityResult
        transferInProgress = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val temp = java.io.File.createTempFile("backup-", ".db", context.cacheDir)
                    try {
                        snapshotDatabase(Database.internal.openHelper.writableDatabase, temp)
                        DatabaseDependency.validateBackup(temp)
                        val resolver = context.applicationContext.contentResolver
                        val output = resolver.openOutputStream(uri, "wt") ?: error("Cannot open backup destination")
                        output.use { dest ->
                            java.io.FileInputStream(temp).use { it.copyTo(dest) }
                        }
                    } finally {
                        temp.delete()
                    }
                }
                context.toast(R.string.backup_saved)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("K4 Music", "Backup failed", error)
                context.toast(R.string.backup_failed)
            } finally { transferInProgress = false }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || transferInProgress) return@rememberLauncherForActivityResult
        transferInProgress = true
        scope.launch {
            var databaseClosed = false
            try {
                withContext(Dispatchers.IO) {
                    val current = java.io.File(requireNotNull(Database.internal.path) { "Library path unavailable" })
                    val staged = java.io.File.createTempFile("restore-", ".db", current.parentFile)
                    try {
                        val input = context.applicationContext.contentResolver.openInputStream(uri)
                            ?: error("Cannot read backup")
                        input.use {
                            FileOutputStream(staged).use { output ->
                                it.copyTo(output)
                                output.fd.sync()
                            }
                        }
                        check(staged.length() > 0) { "Empty backup" }
                        DatabaseDependency.validateBackup(staged)
                        // Once validated, finish the atomic swap even if navigation cancels.
                        withContext(kotlinx.coroutines.NonCancellable) {
                            val recovery = java.io.File(current.parentFile, "data-before-restore.db")
                            val recoveryStage = java.io.File.createTempFile("recovery-", ".db", current.parentFile)
                            try {
                                snapshotDatabase(Database.internal.openHelper.writableDatabase, recoveryStage)
                                DatabaseDependency.validateBackup(recoveryStage)
                                android.system.Os.rename(recoveryStage.absolutePath, recovery.absolutePath)
                            } finally { recoveryStage.delete() }
                            withContext(Dispatchers.Main) { context.stopService(android.content.Intent(context, PlayerService::class.java)) }
                            Database.internal.close()
                            databaseClosed = true
                            // POSIX rename is atomic and leaves current intact on failure.
                            android.system.Os.rename(staged.absolutePath, current.absolutePath)
                            java.io.File(current.path + "-wal").delete()
                            java.io.File(current.path + "-shm").delete()
                            exitProcess(0)
                        }
                    } finally {
                        staged.delete()
                        java.io.File(staged.path + "-wal").delete()
                        java.io.File(staged.path + "-shm").delete()
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("K4 Music", "Restore failed; original library preserved", error)
                context.toast(R.string.restore_failed)
                // A failed atomic rename preserves the original, but Room needs a fresh process.
                if (databaseClosed) exitProcess(0)
            } finally { transferInProgress = false }
        }
    }

    SettingsCategoryScreen(title = stringResource(R.string.settings_backup_title)) {
        SettingsGroup(
            title = stringResource(R.string.backup),
            description = stringResource(R.string.backup_description)
        ) {
            val errorMsg = stringResource(R.string.no_file_chooser_installed)

            SettingsEntry(
                title = stringResource(R.string.backup),
                text = stringResource(R.string.backup_action_description),
                isEnabled = !transferInProgress,
                onClick = {
                    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

                    try {
                        backupLauncher.launch("K4_Music_backup_${dateFormat.format(Date())}.db")
                    } catch (_: ActivityNotFoundException) {
                        context.toast(errorMsg)
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.restore),
            description = stringResource(R.string.restore_warning),
            important = true
        ) {
            val errorMsg = stringResource(R.string.no_file_chooser_installed)

            SettingsEntry(
                title = stringResource(R.string.restore),
                text = stringResource(R.string.restore_description),
                isEnabled = !transferInProgress,
                onClick = {
                    try {
                        restoreLauncher.launch(
                            arrayOf(
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                                "application/octet-stream"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        context.toast(errorMsg)
                    }
                }
            )
        }
        SettingsGroup(title = stringResource(R.string.cleanup)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_history),
                text = stringResource(R.string.pause_playback_history_description),
                isChecked = pauseHistory,
                onCheckedChange = { pauseHistory = !pauseHistory }
            )

            AnimatedVisibility(visible = pauseHistory) {
                SettingsDescription(
                    text = stringResource(R.string.pause_playback_history_warning),
                    important = true
                )
            }

            AnimatedVisibility(visible = !(pauseHistory && eventsCount == 0)) {
                SettingsEntry(
                    title = stringResource(R.string.reset_quick_picks),
                    text = if (eventsCount > 0) pluralStringResource(
                        R.plurals.format_reset_quick_picks_amount,
                        eventsCount,
                        eventsCount
                    )
                    else stringResource(R.string.quick_picks_empty),
                    onClick = { query(Database::clearEvents) },
                    isEnabled = eventsCount > 0
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_time),
                text = stringResource(
                    R.string.format_pause_playback_time_description,
                    topListLength
                ),
                isChecked = pausePlaytime,
                onCheckedChange = { pausePlaytime = !pausePlaytime }
            )

            SettingsEntry(
                title = stringResource(R.string.reset_blacklist),
                text = if (blacklistLength > 0) pluralStringResource(
                    R.plurals.format_reset_blacklist_description,
                    blacklistLength,
                    blacklistLength
                ) else stringResource(R.string.blacklist_empty),
                isEnabled = blacklistLength > 0,
                onClick = {
                    transaction {
                        Database.resetBlacklist()
                    }
                }
            )
        }
    }
}
