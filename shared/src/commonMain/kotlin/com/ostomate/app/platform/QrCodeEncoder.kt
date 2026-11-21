package com.ostomate.app.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object QrCodeEncoder {
    /** [url]'s QR code as modules with a 1-module quiet zone, for printing; null if it can't be encoded. */
    fun encodeModules(url: String): QrModules?
}
