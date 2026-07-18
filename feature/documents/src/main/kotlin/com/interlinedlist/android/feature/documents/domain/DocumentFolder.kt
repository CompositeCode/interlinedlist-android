package com.interlinedlist.android.feature.documents.domain

/** A folder used to organise documents. Root folders have a null [parentId]. */
data class DocumentFolder(
    val id: String,
    val name: String,
    val parentId: String?,
)
