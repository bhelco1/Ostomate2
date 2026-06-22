package com.ostomate.app.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object QrCodeEncoder {
    fun encodeToPng(
        url: String,
        size: Int,
    ): ByteArray
}
