package com.ostomate.app.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JVM host run (gates every PR) via Robolectric + AndroidSQLiteDriver. Assertions are
// shared with the iOS run via MigrationScenarios.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun migrate2To3_addsColorIndexColumnWithNullDefault() =
        MigrationScenarios.migrate2To3AddsColorIndexColumnWithNullDefault(AndroidSQLiteDriver())
}
