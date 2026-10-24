package com.k4music.android.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ktor.http.Url

/**
 * Piped was removed from the app. The table stays (empty) so the Room schema stays at version 30,
 * which Android and Windows backups share.
 */
@Immutable
@Entity(
    indices = [
        Index(
            value = ["apiBaseUrl", "username"],
            unique = true
        )
    ]
)
data class PipedSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiBaseUrl: Url,
    val token: String,
    // the username should never change on piped
    val username: String
)
