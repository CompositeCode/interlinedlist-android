package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.interlinedlist.android.feature.documents.domain.DocumentFolder

/** Locally cached document folder. */
@Entity(tableName = "folder")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val sortOrder: Int,
)

fun FolderEntity.toDomain(): DocumentFolder = DocumentFolder(
    id = id,
    name = name,
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DocumentFolder.toEntity(sortOrder: Int): FolderEntity = FolderEntity(
    id = id,
    name = name,
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)
