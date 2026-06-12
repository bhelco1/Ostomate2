package com.ostimate.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<OstimateDatabase> {
    val dbFile = context.getDatabasePath(OstimateDatabase.FILE_NAME)
    return Room.databaseBuilder<OstimateDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
}
