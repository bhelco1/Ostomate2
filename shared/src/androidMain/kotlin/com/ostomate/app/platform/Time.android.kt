package com.ostomate.app.platform

import java.text.DateFormat
import java.util.Date

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatTimestamp(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
