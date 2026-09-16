package com.interlinedlist.android.core.materialize.data.mapper

import com.interlinedlist.android.core.materialize.data.remote.dto.DocConfigDto
import com.interlinedlist.android.core.materialize.data.remote.dto.ListConfigDto
import com.interlinedlist.android.core.materialize.data.remote.dto.ListFieldDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeRequestDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeResponseDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeSourceDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializedDocumentDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializedListDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MessageConfigDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MessageDraftDto
import com.interlinedlist.android.core.materialize.domain.DocConfig
import com.interlinedlist.android.core.materialize.domain.ListConfig
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializedDocument
import com.interlinedlist.android.core.materialize.domain.MaterializedList
import com.interlinedlist.android.core.materialize.domain.MessageDraft
import com.interlinedlist.android.core.materialize.domain.MessageDraftConfig
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the request body. Each destination contributes only its own config, so
 * the body never carries a section the target ignores.
 */
internal fun MaterializeRequest.toDto(): MaterializeRequestDto = MaterializeRequestDto(
    target = target.apiValue,
    source = source.toDto(),
    listConfig = when (this) {
        is MaterializeRequest.ToList -> listConfig.toDto()
        is MaterializeRequest.ToListAndDocument -> listConfig.toDto()
        else -> null
    },
    docConfig = when (this) {
        is MaterializeRequest.ToDocument -> docConfig?.toDto()
        is MaterializeRequest.ToListAndDocument -> docConfig?.toDto()
        else -> null
    },
    messageConfig = (this as? MaterializeRequest.ToMessageDraft)?.messageConfig?.toDto(),
)

/** Ids only — see [MaterializeSource]. */
internal fun MaterializeSource.toDto(): MaterializeSourceDto = when (this) {
    is MaterializeSource.Messages -> MaterializeSourceDto(kind = kind, messageIds = messageIds)
    is MaterializeSource.Lists -> MaterializeSourceDto(kind = kind, listIds = listIds)
    is MaterializeSource.Rows -> MaterializeSourceDto(kind = kind, listId = listId, rowIds = rowIds)
    is MaterializeSource.Document -> MaterializeSourceDto(kind = kind, documentId = documentId)
    is MaterializeSource.DocumentSelection ->
        MaterializeSourceDto(kind = kind, documentId = documentId, markdown = markdown)
}

internal fun ListConfig.toDto(): ListConfigDto = ListConfigDto(
    title = title,
    description = description,
    isPublic = isPublic,
    fields = fields?.map { it.toDto() },
    includeData = includeData,
)

internal fun MaterializeColumn.toDto(): ListFieldDto = ListFieldDto(
    propertyKey = propertyKey,
    propertyName = propertyName,
    propertyType = propertyType.apiValue,
    // Explicit `null` — a user-added empty column, not an omitted key.
    sourceKey = sourceKey?.let { JsonPrimitive(it) } ?: JsonNull,
    isRequired = isRequired,
    options = options,
)

internal fun DocConfig.toDto(): DocConfigDto = DocConfigDto(
    title = title,
    relativePath = relativePath,
    isPublic = isPublic,
    listStyle = listStyle?.apiValue,
    rowDataStyle = rowDataStyle?.apiValue,
)

internal fun MessageDraftConfig.toDto(): MessageConfigDto = MessageConfigDto(
    content = content,
    crossPostTargets = crossPostTargets?.map { it.apiValue },
    allowThread = allowThread,
    publiclyVisible = publiclyVisible,
    tags = tags,
    scheduledAt = scheduledAt,
)

/**
 * Reads the result against the target that was asked for.
 *
 * Returns null when the 201 did not carry what the target promised — a contract
 * violation the caller has to see as a failure rather than as an empty success.
 */
internal fun MaterializeResponseDto.toOutcomeOrNull(
    request: MaterializeRequest,
): MaterializeOutcome? = when (request) {
    is MaterializeRequest.ToList ->
        list?.toDomain()?.let { MaterializeOutcome.ListCreated(it) }

    is MaterializeRequest.ToDocument ->
        document?.toDomain()?.let { MaterializeOutcome.DocumentCreated(it) }

    is MaterializeRequest.ToListAndDocument -> {
        val createdList = list?.toDomain()
        val createdDocument = document?.toDomain()
        if (createdList != null && createdDocument != null) {
            MaterializeOutcome.ListAndDocumentCreated(createdList, createdDocument)
        } else {
            null
        }
    }

    is MaterializeRequest.ToMessageDraft ->
        message?.toDomain()?.let { MaterializeOutcome.DraftReady(it) }
}

/** The id is the part that matters — a titleless response still opens. */
internal fun MaterializedListDto.toDomain(): MaterializedList = MaterializedList(
    id = id,
    title = title.orEmpty(),
    description = description,
    isPublic = isPublic,
)

internal fun MaterializedDocumentDto.toDomain(): MaterializedDocument = MaterializedDocument(
    id = id,
    title = title.orEmpty(),
    relativePath = relativePath,
    isPublic = isPublic,
)

internal fun MessageDraftDto.toDomain(): MessageDraft = MessageDraft(
    content = content,
    // A server that only sent `content` still yields a one-part draft.
    thread = thread.ifEmpty { listOf(content) },
    isThread = isThread,
    charLimit = charLimit,
)
