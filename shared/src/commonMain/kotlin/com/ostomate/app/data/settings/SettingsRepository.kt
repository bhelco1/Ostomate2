package com.ostomate.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ostomate.app.domain.ApplianceType
import com.ostomate.app.domain.drive.DriveBackupFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * app_settings from the v2 data model (02-architecture.md). Per-supply values
 * (thresholds, box sizes) live in the supply_types table, not here.
 */
data class AppSettings(
    val devMode: Boolean = false,
    val onboardingDone: Boolean = false,
    val lockSettings: Boolean = false,
    /** BCP-47 tag, or null to follow the system locale (N7). */
    val localeOverride: String? = null,
    /** Opt-in Sentry crash reporting (default off, per privacy posture). */
    val crashReportingEnabled: Boolean = false,
    val applianceType: ApplianceType = ApplianceType.TWO_PIECE,
)

/**
 * The daily Google Drive backup's switch and its last run. Kept out of [AppSettings] on
 * purpose: AppSettings is exactly what a backup file carries and a restore writes back, and
 * this is per-device state (a Google authorization is granted to one install), so adding it
 * there would change the backup format and what a restore does.
 */
data class DriveBackupState(
    val enabled: Boolean = false,
    val lastRunAtMillis: Long? = null,
    /** Null after a successful run (or before any run). */
    val lastFailure: DriveBackupFailure? = null,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                devMode = prefs[DEV_MODE] ?: false,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                lockSettings = prefs[LOCK_SETTINGS] ?: false,
                localeOverride = prefs[LOCALE_OVERRIDE],
                crashReportingEnabled = prefs[CRASH_REPORTING] ?: false,
                applianceType =
                    prefs[APPLIANCE_TYPE]?.let {
                        runCatching { ApplianceType.valueOf(it) }.getOrNull()
                    } ?: ApplianceType.TWO_PIECE,
            )
        }

    val driveBackup: Flow<DriveBackupState> =
        dataStore.data.map { prefs ->
            DriveBackupState(
                enabled = prefs[DRIVE_BACKUP_ENABLED] ?: false,
                lastRunAtMillis = prefs[DRIVE_BACKUP_LAST_RUN_AT],
                lastFailure =
                    prefs[DRIVE_BACKUP_LAST_FAILURE]?.let { name ->
                        DriveBackupFailure.entries.firstOrNull { it.name == name } ?: DriveBackupFailure.OTHER
                    },
            )
        }

    suspend fun setDriveBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[DRIVE_BACKUP_ENABLED] = enabled }
    }

    /** Records a finished run: [failure] null means it uploaded. */
    suspend fun recordDriveBackupRun(
        atMillis: Long,
        failure: DriveBackupFailure?,
    ) {
        dataStore.edit { prefs ->
            prefs[DRIVE_BACKUP_LAST_RUN_AT] = atMillis
            if (failure == null) {
                prefs.remove(
                    DRIVE_BACKUP_LAST_FAILURE,
                )
            } else {
                prefs[DRIVE_BACKUP_LAST_FAILURE] = failure.name
            }
        }
    }

    suspend fun setDevMode(enabled: Boolean) {
        dataStore.edit { it[DEV_MODE] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    suspend fun setLockSettings(enabled: Boolean) {
        dataStore.edit { it[LOCK_SETTINGS] = enabled }
    }

    suspend fun setLocaleOverride(languageTag: String?) {
        dataStore.edit { prefs ->
            if (languageTag == null) prefs.remove(LOCALE_OVERRIDE) else prefs[LOCALE_OVERRIDE] = languageTag
        }
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        dataStore.edit { it[CRASH_REPORTING] = enabled }
    }

    suspend fun setApplianceType(type: ApplianceType) {
        dataStore.edit { it[APPLIANCE_TYPE] = type.name }
    }

    /** Overwrites every setting in one edit, for a full-state backup restore. */
    suspend fun restore(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[DEV_MODE] = settings.devMode
            prefs[ONBOARDING_DONE] = settings.onboardingDone
            prefs[LOCK_SETTINGS] = settings.lockSettings
            prefs[CRASH_REPORTING] = settings.crashReportingEnabled
            prefs[APPLIANCE_TYPE] = settings.applianceType.name
            val locale = settings.localeOverride
            if (locale == null) prefs.remove(LOCALE_OVERRIDE) else prefs[LOCALE_OVERRIDE] = locale
        }
    }

    private companion object {
        val DEV_MODE = booleanPreferencesKey("devMode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboardingDone")
        val LOCK_SETTINGS = booleanPreferencesKey("lockSettings")
        val LOCALE_OVERRIDE = stringPreferencesKey("localeOverride")
        val CRASH_REPORTING = booleanPreferencesKey("crashReportingEnabled")
        val APPLIANCE_TYPE = stringPreferencesKey("applianceType")
        val DRIVE_BACKUP_ENABLED = booleanPreferencesKey("driveBackupEnabled")
        val DRIVE_BACKUP_LAST_RUN_AT = longPreferencesKey("driveBackupLastRunAt")
        val DRIVE_BACKUP_LAST_FAILURE = stringPreferencesKey("driveBackupLastFailure")
    }
}
