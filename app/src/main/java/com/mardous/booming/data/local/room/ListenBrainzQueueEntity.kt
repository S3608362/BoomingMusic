package com.mardous.booming.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ListenBrainzQueueEntity")
class ListenBrainzQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "listened_at")
    val listenedAt: Long,
    @ColumnInfo(name = "listen_json")
    val listenJson: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
