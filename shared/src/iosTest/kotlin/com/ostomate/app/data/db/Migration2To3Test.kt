package com.ostomate.app.data.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test

// iOS run: bundled native driver. Assertions shared with the JVM host via MigrationScenarios.
class Migration2To3Test {
    @Test
    fun migrate2To3_addsColorIndexColumnWithNullDefault() =
        MigrationScenarios.migrate2To3AddsColorIndexColumnWithNullDefault(BundledSQLiteDriver())
}
