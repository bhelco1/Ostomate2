package com.ostomate.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun settingsDataStore(): DataStore<Preferences> =
    createSettingsDataStore {
        val documentsUrl =
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )
        val path = requireNotNull(documentsUrl?.path) { "Could not resolve documents directory" }
        "$path/$SETTINGS_FILE_NAME"
    }
