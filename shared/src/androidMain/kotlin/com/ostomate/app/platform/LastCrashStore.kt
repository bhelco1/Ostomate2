package com.ostimate.app.platform

import android.content.Context

object LastCrashStore {
    private const val PREFS_NAME = "ostimate_crash"
    private const val KEY_CRASH = "last_crash"
    private const val MAX_TRACE_CHARS = 4_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = throwable.stackTraceToString().take(MAX_TRACE_CHARS)
                appContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CRASH, trace)
                    .apply()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CRASH, null)

    fun clear(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CRASH)
            .apply()
    }
}
