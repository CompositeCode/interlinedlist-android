package com.interlinedlist.android.feature.documents.domain

/** A document that lives in the `_templates` folder and can seed a new document. */
data class DocumentTemplate(
    val id: String,
    val title: String,
    val snippet: String,
)
