package com.ostimate.app.domain

/**
 * Built-in supply categories. BAG and FLANGE are the two seeded defaults that
 * mirror v1's hardcoded supplies; CUSTOM covers user-created types (N2).
 * Stored in the DB by name — renaming a constant requires a migration.
 */
enum class SupplyKind {
    BAG,
    FLANGE,
    CUSTOM,
}
