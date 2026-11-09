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

        /** True for a source token ("source:qr"): an audit record, never shown as a user tag. */
        fun isSourceTag(tag: String): Boolean = tag.trim().startsWith(TAG_PREFIX)

        /** The user's own tags in a `tags` string, without source tokens or blanks. */
        fun userTags(tags: String?): List<String> =
            tags
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !isSourceTag(it) }
                .orEmpty()

        /**
         * Rebuilds a `tags` string after the user edited their tags, keeping [original]'s
         * source token: the user never sees it, so an edit must not be able to drop it.
         */
        fun withUserTags(
            original: String?,
            userTags: Collection<String>,
        ): String? {
            val sourceTokens = original?.split(',')?.map { it.trim() }?.filter { isSourceTag(it) }.orEmpty()
            return (sourceTokens + userTags).joinToString(",").ifEmpty { null }
        }
    }
}
