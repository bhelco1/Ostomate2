package com.ostimate.app.platform

expect fun currentTimeMillis(): Long

/** Formats an epoch-millis timestamp as a short, locale-aware date+time string. */
expect fun formatTimestamp(millis: Long): String
