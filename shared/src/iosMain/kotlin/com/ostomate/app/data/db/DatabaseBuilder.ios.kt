package com.ostomate.app.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun databaseBuilder(): RoomDatabase.Builder<OstomateDatabase> {
    val documentsUrl =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val path = requireNotNull(documentsUrl?.path) { "Could not resolve documents directory" }
    return Room.databaseBuilder<OstomateDatabase>(
        name = "$path/${OstomateDatabase.FILE_NAME}",
    )
}
