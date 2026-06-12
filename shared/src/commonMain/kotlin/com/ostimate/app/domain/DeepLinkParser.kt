package com.ostimate.app.domain

/**
 * Parses QR-sticker deep links of the form `ostimate://log?item=<supply>`.
 *
 * Pure Kotlin (no platform Uri) so it runs and tests in commonMain.
 * Security posture (06-security-privacy.md): strict scheme + host match and a
 * supply allowlist — never act on anything else in the URI.
 */
object DeepLinkParser {

    private const val SCHEME_PREFIX = "ostimate://"
    private const val HOST = "log"
    private val allowedSupplies = setOf("bag", "flange")

    fun parse(uri: String): String? {
        val normalized = uri.trim().lowercase()
        if (!normalized.startsWith(SCHEME_PREFIX)) return null

        val afterScheme = normalized.removePrefix(SCHEME_PREFIX)
        val host = afterScheme.substringBefore('?').trimEnd('/')
        if (host != HOST) return null

        val query = afterScheme.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null

        val item = query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "item" }
            ?.get(1)
            ?: return null

        return item.takeIf { it in allowedSupplies }
    }
}
