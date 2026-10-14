package android.util

/** Desktop stand-in for the one android.util.Base64 call shared Android code makes. */
object Base64 {
    const val DEFAULT = 0

    @Suppress("UNUSED_PARAMETER")
    fun decode(str: String, flags: Int): ByteArray = java.util.Base64.getMimeDecoder().decode(str)
}
