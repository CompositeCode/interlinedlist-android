package com.interlinedlist.android.core.materialize.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.materialize.data.MaterializeRepository
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest

/**
 * Records what the window asked to be created.
 *
 * The window's job ends at the request it hands the repository; that request's
 * serialisation is covered by `MaterializeRequestBodyTest` against a real
 * Retrofit/MockWebServer stack, so these tests assert at the seam instead of
 * racing a socket.
 */
internal class FakeMaterializeRepository(
    var result: ApiResult<MaterializeOutcome> = ApiResult.Success(
        MaterializeOutcome.ListCreated(
            com.interlinedlist.android.core.materialize.domain.MaterializedList("lst_new", "Books to Read"),
        ),
    ),
) : MaterializeRepository {

    val requests = mutableListOf<MaterializeRequest>()

    override suspend fun materialize(request: MaterializeRequest): ApiResult<MaterializeOutcome> {
        requests += request
        return result
    }
}
