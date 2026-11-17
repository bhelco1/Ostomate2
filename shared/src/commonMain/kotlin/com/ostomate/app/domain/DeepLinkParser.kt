package com.ostomate.app.domain

/**
 * Parses QR-sticker deep links of the form `ostomate://log?item=<supply>`.
 *
 * Pure Kotlin (no platform Uri) so it runs and tests in commonMain.
 * Security posture (06-security-privacy.md): strict scheme + host match and a
 * supply allowlist — never act on anything else in the URI.
 */
object DeepLinkParser {
    private const val SCHEME_PREFIX = "ostomate://"
    private const val HOST = "log"
    private val namedSupplies = setOf("bag", "flange")
    private val idItemRegex = Regex("""^id:\d+$""")

    fun parse(uri: String): String? {
        val item = queryParams(uri).firstOrNull { it.first == "item" }?.second ?: return null
        return item.takeIf { it in namedSupplies || idItemRegex.matches(it) }
    }

    /**
     * True when the link marks itself as an NFC sticker tap (`&src=nfc`, FEAT-04). iOS
     * Shortcuts opens the link with no other sign of NFC, so the sticker's link says so.
     */
    fun isNfc(uri: String): Boolean = queryParams(uri).any { it.first == "src" && it.second == "nfc" }

    /** The link's query parameters, or none unless it is exactly `ostomate://log`. */
    private fun queryParams(uri: String): List<Pair<String, String>> {
        val normalized = uri.trim().lowercase()
        val afterScheme = normalized.removePrefix(SCHEME_PREFIX)
        val host = afterScheme.substringBefore('?').trimEnd('/')
        val query = if (afterScheme != normalized && host == HOST) afterScheme.substringAfter('?', "") else ""
        return query
            .split('&')
            .map { it.split('=', limit = 2) }
            .filter { it.size == 2 }
            .map { it[0] to it[1] }
    }

    /** Extracts the numeric supply ID from an `id:<number>` item token, or null. */
    fun parseCustomId(item: String): Long? =
        if (item.startsWith("id:")) item.removePrefix("id:").toLongOrNull() else null
}
