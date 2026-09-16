package com.interlinedlist.android.feature.messages.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Membership of one cached message in one feed, with its position in that feed.
 *
 * Feeds are *lists over* the shared [MessageEntity] rows, not copies of them: the
 * main feed and a tag-filtered feed routinely contain the same message, and both
 * must see the same dig count, edits and deletions. Keeping membership in its own
 * table is what lets a tag feed be cached at all without evicting the main feed —
 * a message row can only hold one position, so storing the position on the message
 * would mean the second feed to load silently stole rows from the first.
 *
 * [feedKey] identifies the feed: [MAIN_FEED_KEY] (the empty string) for the main
 * feed, and the tag itself for a tag feed. Tags are never blank, so the two spaces
 * cannot collide.
 *
 * The foreign key cascades, so deleting a message (own-message delete, block, mute)
 * drops it from every feed it appeared in without extra bookkeeping. That cascade
 * is also why message rows are written with an **upsert** rather than
 * `@Insert(REPLACE)` — see [MessageDao.insertAll].
 */
@Entity(
    tableName = "feed_entry",
    primaryKeys = ["feedKey", "messageId"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class FeedEntryEntity(
    val feedKey: String,
    val messageId: String,
    /** Server-relative position within this feed, captured at fetch time. */
    val position: Long,
)

/** Feed key of the account's main (unfiltered) feed. */
const val MAIN_FEED_KEY: String = ""

/**
 * The [FeedEntryEntity.feedKey] a feed scoped to [tag] stores its rows under; a
 * null or blank tag is the main feed.
 */
fun feedKeyFor(tag: String?): String = tag?.takeIf { it.isNotBlank() } ?: MAIN_FEED_KEY
