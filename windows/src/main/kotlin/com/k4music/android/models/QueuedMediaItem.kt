package com.k4music.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Desktop twin of the Android entity (same table and columns, so library backups move between
 * phone and PC). Android stores a parcelled MediaItem here; the desktop keeps its queue in
 * queue.json and leaves this table empty.
 */
@Immutable
@Entity
class QueuedMediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val mediaItem: ByteArray,
    var position: Long?
)
