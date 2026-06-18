package com.ostomate.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ostomate.app.domain.ApplianceType
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

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                devMode = prefs[DEV_MODE] ?: false,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                lockSettings = prefs[LOCK_SETTINGS] ?: false,
                localeOverride = prefs[LOCALE_OVERRIDE],
                crashReportingEnabled = prefs[CRASH_REPORTING] ?: false,
                applianceType = prefs[APPLIANCE_TYPE]?.let {
                    runCatching { ApplianceType.valueOf(it) }.getOrNull()
                } ?: ApplianceType.TWO_PIECE,
            )
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

    private companion object {
        val DEV_MODE = booleanPreferencesKey("devMode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboardingDone")
        val LOCK_SETTINGS = booleanPreferencesKey("lockSettings")
        val LOCALE_OVERRIDE = stringPreferencesKey("localeOverride")
        val CRASH_REPORTING = booleanPreferencesKey("crashReportingEnabled")
        val APPLIANCE_TYPE = stringPreferencesKey("applianceType")
    }
}
