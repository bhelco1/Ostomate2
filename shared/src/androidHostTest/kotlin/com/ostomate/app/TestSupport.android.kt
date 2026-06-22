package com.ostomate.app

actual fun testTempDir(): String = System.getProperty("java.io.tmpdir") ?: "/tmp"
