package com.k4music.android.utils

/** Only this device's MediaStore numbers can be converted to Android content URIs. */
internal fun localMediaStoreId(id: String): Long? =
    id.takeIf { it.startsWith("local:") }?.removePrefix("local:")?.toLongOrNull()?.takeIf { it >= 0 }
