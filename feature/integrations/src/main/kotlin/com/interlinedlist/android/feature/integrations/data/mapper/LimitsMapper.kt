package com.interlinedlist.android.feature.integrations.data.mapper

import com.interlinedlist.android.feature.integrations.data.remote.dto.LimitsDto
import com.interlinedlist.android.feature.integrations.domain.PlanLimits

/**
 * Maps the loosely-typed `/api/limits` payload into [PlanLimits]. Each entry in
 * the `limits` map becomes a [PlanLimits.Limit] with a humanised label derived
 * from its snake/camel-case key; entries are sorted by key for a stable order.
 */
fun LimitsDto.toDomain(): PlanLimits = PlanLimits(
    planName = planName ?: plan,
    limits = (limits ?: emptyMap())
        .toSortedMap()
        .map { (key, entry) ->
            PlanLimits.Limit(
                key = key,
                label = key.toDisplayLabel(),
                used = entry.used,
                max = entry.ceiling,
            )
        },
)

/** "listDataRows" / "list_data_rows" -> "List data rows". */
private fun String.toDisplayLabel(): String {
    val spaced = replace("_", " ")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
    return spaced.replaceFirstChar { it.uppercaseChar() }
        .lowercase()
        .replaceFirstChar { it.uppercaseChar() }
}
