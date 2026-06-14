package com.ostomate.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<OstomateDatabase> {
    val dbFile = context.getDatabasePath(OstomateDatabase.FILE_NAME)
    return Room.databaseBuilder<OstomateDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
}
