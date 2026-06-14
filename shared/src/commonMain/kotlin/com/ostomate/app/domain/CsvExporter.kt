@file:Suppress("DEPRECATION") // kotlinx-datetime 0.6.x

package com.ostimate.app.domain

import com.ostimate.app.data.db.ChangeEventWithSupply
import kotlinx.datetime.Instant

/**
 * Generates the Ostimate v2 CSV format.
 * Header: supply_id,supply_name,supply_kind,timestamp_millis,edited_at_millis,note
 */
object CsvExporter {
    fun buildCsv(events: List<ChangeEventWithSupply>): String =
        buildString {
            appendLine("supply_id,supply_name,supply_kind,timestamp_millis,edited_at_millis,note")
            events
                .sortedBy { it.event.timestampMillis }
                .forEach { row ->
                    append(row.event.supplyTypeId)
                    append(',')
                    append(escapeCsv(row.supplyName))
                    append(',')
                    append(row.supplyKind.name)
                    append(',')
                    append(row.event.timestampMillis)
                    append(',')
                    append(row.event.editedAtMillis ?: "")
                    append(',')
                    appendLine(escapeCsv(row.event.note ?: ""))
                }
        }

    private fun escapeCsv(value: String): String {
        if (!value.contains(',') && !value.contains('"') && !value.contains('\n')) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}

/**
 * Parses the Ostimate v1 CSV format:
 * Header: id,type,timestamp_iso8601
 * type = BAG | FLANGE
 */
object CsvV1Importer {
    data class V1Row(
        val originalId: Long,
        val kind: String,
        val timestampMillis: Long,
    )

    data class ParseResult(
        val rows: List<V1Row>,
        val parseErrors: Int,
    )

    fun parse(csv: String): ParseResult {
        val lines = csv.lines()
        if (lines.isEmpty()) return ParseResult(emptyList(), 0)
        val dataLines = lines.drop(1) // skip header
        var errors = 0
        val rows =
            dataLines.mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split(",", limit = 3)
                if (parts.size < 3) {
                    errors++
                    return@mapNotNull null
                }
                val id =
                    parts[0].toLongOrNull() ?: run {
                        errors++
                        return@mapNotNull null
                    }
                val kind = parts[1].uppercase().trim()
                val isoString = parts[2].trim()
                val millis =
                    runCatching {
                        Instant.parse(isoString).toEpochMilliseconds()
                    }.getOrElse {
                        errors++
                        return@mapNotNull null
                    }
                V1Row(originalId = id, kind = kind, timestampMillis = millis)
            }
        return ParseResult(rows = rows, parseErrors = errors)
    }
}
