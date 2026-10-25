package com.mardous.booming.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ListenBrainzQueueDao {

    @Query("SELECT * FROM ListenBrainzQueueEntity ORDER BY listened_at ASC LIMIT :limit")
    suspend fun pendingListens(limit: Int): List<ListenBrainzQueueEntity>

    @Query("SELECT COUNT(*) FROM ListenBrainzQueueEntity")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: ListenBrainzQueueEntity)

    @Query("DELETE FROM ListenBrainzQueueEntity WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM ListenBrainzQueueEntity WHERE id IN (SELECT id FROM ListenBrainzQueueEntity ORDER BY listened_at ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("UPDATE ListenBrainzQueueEntity SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun incrementAttempts(ids: List<Long>)

    @Query("DELETE FROM ListenBrainzQueueEntity")
    suspend fun clear()
}
