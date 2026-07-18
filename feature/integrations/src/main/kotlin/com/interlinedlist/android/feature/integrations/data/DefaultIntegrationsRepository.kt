package com.interlinedlist.android.feature.integrations.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.integrations.data.mapper.toDomain
import com.interlinedlist.android.feature.integrations.data.remote.IntegrationsApi
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class DefaultIntegrationsRepository @Inject constructor(
    private val api: IntegrationsApi,
    private val fileStore: ExportFileStore,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : IntegrationsRepository {

    override suspend fun downloadExport(type: ExportType): ApiResult<File> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.downloadExport(type.pathSegment) }
                .map { body ->
                    // Stream the response straight to a cache file so a large CSV
                    // never has to sit fully in memory.
                    val dir = fileStore.exportsDir().apply { mkdirs() }
                    val file = File(dir, "${type.fileBaseName}.csv")
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file
                }
        }

    override suspend fun getConnectedAccounts(): List<ConnectedAccount> =
        withContext(dispatchers.io) {
            // Statuses are independent; one provider failing shouldn't hide the
            // rest, so a failed lookup is treated as "not connected".
            ConnectedAccount.Provider.entries.map { provider ->
                when (val result = safeApiCall(json) { api.getConnectionStatus(provider.statusPath) }) {
                    is ApiResult.Success -> ConnectedAccount(
                        provider = provider,
                        isConnected = result.data.isConnected,
                        handle = result.data.bestHandle,
                    )
                    is ApiResult.Failure -> ConnectedAccount(
                        provider = provider,
                        isConnected = false,
                    )
                }
            }
        }

    override suspend fun getLimits(): ApiResult<PlanLimits> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getLimits() }.map { it.toDomain() }
        }
}
