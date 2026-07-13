package com.ostomate.app.data

/**
 * How a change event was logged. Stored as a namespaced token in the event's `tags` column
 * (e.g. "source:qr") so a duplicate self-identifies its origin — letting a BUG-09 double-log
 * be traced from the data itself, without a schema change (FEAT-02). The token lives in the
 * comma-separated `tags` field, so it coexists with future user tags in the same column.
 */
enum class ChangeSource {
    QR,
    MANUAL,
    WIDGET,
    ;

    /** The token written into a change event's `tags` column, e.g. "source:qr". */
    val tag: String get() = "$TAG_PREFIX${name.lowercase()}"

    companion object {
        private const val TAG_PREFIX = "source:"

        /** Reads the source back out of a (possibly multi-value) `tags` string, or null if absent. */
        fun fromTags(tags: String?): ChangeSource? =
            tags
                ?.splitToSequence(',')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith(TAG_PREFIX) }
                ?.removePrefix(TAG_PREFIX)
                ?.let { value -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } }
    }
}
