package com.interlinedlist.android.feature.lists.domain

/** A folder used to organise lists. */
data class ListFolder(
    val id: String,
    val name: String,
    val parentId: String?,
)

/**
 * A page of results plus whether more remain, so the UI can offer load-more
 * without knowing the wire pagination shape.
 */
data class Paged<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val total: Int,
    val offset: Int,
)
