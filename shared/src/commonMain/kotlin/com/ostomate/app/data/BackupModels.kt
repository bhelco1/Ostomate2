package com.ostomate.app.data

import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.data.settings.AppSettings
import com.ostomate.app.domain.ApplianceType
import com.ostomate.app.domain.SupplyKind
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of all persisted state, kept separate from the Room entities so the
 * DB schema and the on-disk backup format can evolve independently (the entities stay free
 * of serialization annotations). Enums travel as their `name` string. See FEAT-00.
 */
@Serializable
data class BackupEnvelope(
    val formatVersion: Int,
    val schemaVersion: Int,
    val exportedAt: Long,
    val settings: SettingsDto,
    val supplyTypes: List<SupplyTypeDto>,
    val events: List<ChangeEventDto>,
)

@Serializable
data class SettingsDto(
    val onboardingDone: Boolean,
    val lockSettings: Boolean,
    val localeOverride: String?,
    val crashReportingEnabled: Boolean,
    val applianceType: String,
    val devMode: Boolean,
)

@Serializable
data class SupplyTypeDto(
    val id: Long,
    val name: String,
    val kind: String,
    val boxSize: Int,
    val warnThresholdDays: Int,
    val onHand: Int,
    val sortOrder: Int,
    val archived: Boolean,
    val colorIndex: Int?,
)

@Serializable
data class ChangeEventDto(
    val id: Long,
    val supplyTypeId: Long,
    val timestampMillis: Long,
    val note: String?,
    val tags: String?,
    val createdAtMillis: Long,
    val editedAtMillis: Long?,
)

internal fun AppSettings.toDto(): SettingsDto =
    SettingsDto(
        onboardingDone = onboardingDone,
        lockSettings = lockSettings,
        localeOverride = localeOverride,
        crashReportingEnabled = crashReportingEnabled,
        applianceType = applianceType.name,
        devMode = devMode,
    )

internal fun SettingsDto.toAppSettings(): AppSettings =
    AppSettings(
        devMode = devMode,
        onboardingDone = onboardingDone,
        lockSettings = lockSettings,
        localeOverride = localeOverride,
        crashReportingEnabled = crashReportingEnabled,
        applianceType = ApplianceType.valueOf(applianceType),
    )

internal fun SupplyTypeEntity.toDto(): SupplyTypeDto =
    SupplyTypeDto(
        id = id,
        name = name,
        kind = kind.name,
        boxSize = boxSize,
        warnThresholdDays = warnThresholdDays,
        onHand = onHand,
        sortOrder = sortOrder,
        archived = archived,
        colorIndex = colorIndex,
    )

internal fun SupplyTypeDto.toEntity(): SupplyTypeEntity =
    SupplyTypeEntity(
        id = id,
        name = name,
        kind = SupplyKind.valueOf(kind),
        boxSize = boxSize,
        warnThresholdDays = warnThresholdDays,
        onHand = onHand,
        sortOrder = sortOrder,
        archived = archived,
        colorIndex = colorIndex,
    )

internal fun ChangeEventEntity.toDto(): ChangeEventDto =
    ChangeEventDto(
        id = id,
        supplyTypeId = supplyTypeId,
        timestampMillis = timestampMillis,
        note = note,
        tags = tags,
        createdAtMillis = createdAtMillis,
        editedAtMillis = editedAtMillis,
    )

internal fun ChangeEventDto.toEntity(): ChangeEventEntity =
    ChangeEventEntity(
        id = id,
        supplyTypeId = supplyTypeId,
        timestampMillis = timestampMillis,
        note = note,
        tags = tags,
        createdAtMillis = createdAtMillis,
        editedAtMillis = editedAtMillis,
    )
