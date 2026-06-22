package com.ostomate.app

/**
 * Platform hook for tests that need a real filesystem path (DataStore files).
 * Implemented per test target so the same data tests run on the JVM host
 * (`testAndroidHostTest`, gated on every PR) and on the iOS simulator
 * (`iosSimulatorArm64Test`).
 */
expect fun testTempDir(): String
