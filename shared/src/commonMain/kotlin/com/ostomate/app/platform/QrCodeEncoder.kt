package com.ostomate.app.platform

/** Opaque black, the default module colour (ARGB). */
const val QR_BLACK: Int = 0xFF000000.toInt()

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object QrCodeEncoder {
    /** A PNG of [url]'s QR code, modules in [color] (ARGB) on white; empty if it can't be encoded. */
    fun encodeToPng(
        url: String,
        size: Int,
        color: Int = QR_BLACK,
    ): ByteArray

    /** [url]'s QR code as modules with a 1-module quiet zone, for printing; null if it can't be encoded. */
    fun encodeModules(url: String): QrModules?
}
