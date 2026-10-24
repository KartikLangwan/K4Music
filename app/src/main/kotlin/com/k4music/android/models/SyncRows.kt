package com.k4music.android.models

/** Light query results for library sync (see providers/sync); shared by Android and the desktop app. */
data class LikedRow(val id: String, val likedAt: Long)

data class PlaylistRow(val id: Long, val name: String)
