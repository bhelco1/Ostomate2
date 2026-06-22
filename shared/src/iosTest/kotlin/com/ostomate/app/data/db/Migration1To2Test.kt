package com.ostomate.app.data.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test

// iOS run: bundled native driver. Assertions shared with the JVM host via MigrationScenarios.
class Migration1To2Test {
    @Test
    fun migrate1To2_seedsCatalogAndRemapsSpikeRows() =
        MigrationScenarios.migrate1To2SeedsCatalogAndRemapsSpikeRows(BundledSQLiteDriver())
}
