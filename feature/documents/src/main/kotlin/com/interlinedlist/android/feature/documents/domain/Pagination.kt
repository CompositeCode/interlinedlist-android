package com.interlinedlist.android.feature.documents.domain

/**
 * Offset/limit paging metadata returned alongside list responses. [hasMore]
 * drives the index's load-more affordance.
 */
data class Pagination(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
) {
    /** Offset to request for the next page. */
    val nextOffset: Int get() = offset + limit

    companion object {
        const val DEFAULT_LIMIT = 20

        /** A single-page result covering [count] items (used for local-only reads). */
        fun single(count: Int) = Pagination(
            total = count,
            limit = if (count == 0) DEFAULT_LIMIT else count,
            offset = 0,
            hasMore = false,
        )
    }
}

/** A page of items plus its paging metadata. */
data class Page<T>(
    val items: List<T>,
    val pagination: Pagination,
)
