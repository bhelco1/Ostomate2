package com.ostomate.app

import platform.Foundation.NSTemporaryDirectory

actual fun testTempDir(): String = NSTemporaryDirectory()
