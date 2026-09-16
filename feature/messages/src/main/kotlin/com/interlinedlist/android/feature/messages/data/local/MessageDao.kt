package com.interlinedlist.android.feature.messages.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /**
     * Top-level messages of the feed [feedKey] identifies, in server order;
     * re-emits on every change. Scheduled (not-yet-published) messages are
     * excluded — they live in their own view, not a feed.
     *
     * Membership comes from `feed_entry`, so the main feed and a tag feed share
     * the underlying message rows without either owning them.
     */
    @Query(
        """
        SELECT m.* FROM message AS m
        INNER JOIN feed_entry AS f ON f.messageId = m.id
        WHERE f.feedKey = :feedKey AND m.parentId IS NULL AND m.scheduledAt IS NULL
        ORDER BY f.position ASC
        """,
    )
    fun observeFeed(feedKey: String): Flow<List<MessageEntity>>

    /** Direct replies to a message in server order. */
    @Query("SELECT * FROM message WHERE parentId = :parentId ORDER BY feedOrder ASC")
    fun observeReplies(parentId: String): Flow<List<MessageEntity>>

    /** A single cached message (or null), re-emitting on change. */
    @Query("SELECT * FROM message WHERE id = :id")
    fun observeMessage(id: String): Flow<MessageEntity?>

    /** Cached scheduled messages, soonest first; re-emits on every change. */
    @Query("SELECT * FROM message WHERE scheduledAt IS NOT NULL ORDER BY scheduledAt ASC")
    fun observeScheduled(): Flow<List<MessageEntity>>

    /**
     * Writes message rows, updating the ones already cached.
     *
     * Deliberately an upsert and **not** `@Insert(REPLACE)`: REPLACE deletes the
     * conflicting row before re-inserting it, which would fire `feed_entry`'s
     * ON DELETE CASCADE and silently drop that message out of every feed it was
     * already in — so re-fetching a message in a tag feed would evict it from the
     * main feed. An upsert updates in place and leaves memberships alone.
     */
    @Upsert
    suspend fun insertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    /** Places (or moves) messages within a feed. Message rows must exist first. */
    @Upsert
    suspend fun upsertFeedEntries(entries: List<FeedEntryEntity>)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Removes every cached message authored by [username] (used to hide a blocked
     * or muted author's messages from the local feed/replies immediately). Feed
     * memberships cascade away with the rows.
     */
    @Query("DELETE FROM message WHERE authorUsername = :username")
    suspend fun deleteByAuthorUsername(username: String)

    /**
     * Empties the feed [feedKey] identifies, before writing a fresh refresh page.
     * Only that feed's membership is dropped: the message rows stay, so another
     * feed holding the same messages is untouched.
     */
    @Query("DELETE FROM feed_entry WHERE feedKey = :feedKey")
    suspend fun clearFeed(feedKey: String)

    /** Clears the cached scheduled messages (used before a fresh refresh). */
    @Query("DELETE FROM message WHERE scheduledAt IS NOT NULL")
    suspend fun clearScheduled()

    /** Last position currently stored in a feed (for append/load-more). */
    @Query("SELECT MAX(position) FROM feed_entry WHERE feedKey = :feedKey")
    suspend fun maxFeedPosition(feedKey: String): Long?

    /** First position currently stored in a feed (for inserting at the head). */
    @Query("SELECT MIN(position) FROM feed_entry WHERE feedKey = :feedKey")
    suspend fun minFeedPosition(feedKey: String): Long?

    /**
     * Largest sort position stored on any cached message row. Used to park a
     * message the app fetched outside a feed (a reply, a detail-screen fetch)
     * after everything already cached, rather than in front of it.
     */
    @Query("SELECT MAX(feedOrder) FROM message")
    suspend fun maxMessageOrder(): Long?
}
