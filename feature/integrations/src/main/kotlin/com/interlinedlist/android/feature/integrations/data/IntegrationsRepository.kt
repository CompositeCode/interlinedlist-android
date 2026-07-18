package com.interlinedlist.android.feature.integrations.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import java.io.File

/**
 * Data operations for the integrations hub: downloading CSV exports to disk,
 * reading connected-account status, and reading plan limits. Everything is a
 * live read — there is no offline cache — so results come back as [ApiResult].
 */
interface IntegrationsRepository {

    /**
     * Downloads the CSV for [type] and writes it into the app cache, returning
     * the file so the caller can hand it to the share sheet.
     */
    suspend fun downloadExport(type: ExportType): ApiResult<File>

    /** Fetches connection status for every supported provider. */
    suspend fun getConnectedAccounts(): List<ConnectedAccount>

    /** Reads plan limits/usage, or a failure the UI can render inline. */
    suspend fun getLimits(): ApiResult<PlanLimits>
}
