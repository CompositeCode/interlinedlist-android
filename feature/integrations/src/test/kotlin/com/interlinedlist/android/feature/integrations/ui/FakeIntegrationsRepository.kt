package com.interlinedlist.android.feature.integrations.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import java.io.File

/** In-memory [IntegrationsRepository] for ViewModel tests; each result is settable. */
class FakeIntegrationsRepository : IntegrationsRepository {

    var exportResult: ApiResult<File> = ApiResult.Failure(AppError.Unknown("not set"))
    val exportedTypes = mutableListOf<ExportType>()

    var accounts: List<ConnectedAccount> = emptyList()
    var accountsCalls = 0

    var limitsResult: ApiResult<PlanLimits> = ApiResult.Failure(AppError.Unknown("not set"))

    override suspend fun downloadExport(type: ExportType): ApiResult<File> {
        exportedTypes.add(type)
        return exportResult
    }

    override suspend fun getConnectedAccounts(): List<ConnectedAccount> {
        accountsCalls++
        return accounts
    }

    override suspend fun getLimits(): ApiResult<PlanLimits> = limitsResult
}
