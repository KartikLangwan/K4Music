package com.k4music.providers.sync

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A one-time random 128-bit secret, carried in the QR code the PC shows. Only a device that scanned it
 * can make data the PC accepts, and nobody else on the network can read it.
 */
class SyncCrypto private constructor(private val secret: ByteArray) {
    /** The secret as text (Base32 in groups of four), as the QR code carries it. */
    val code: String get() = Base32.encode(secret).chunked(4).joinToString("-")

    /** Binds the encrypted data to this secret (used as associated data). */
    val room: String by lazy { MessageDigest.getInstance("SHA-256").digest("k4:room:".toByteArray() + secret).hex() }

    private val key by lazy {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        SecretKeySpec(mac.doFinal("k4:enc".toByteArray()), "AES")
    }

    fun encrypt(plain: ByteArray): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(room.toByteArray())
        }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain))
    }

    /** Throws when the data was changed or belongs to another code. */
    fun decrypt(text: String): ByteArray {
        val raw = Base64.getDecoder().decode(text)
        require(raw.size > 12 + 16) { "Sync data is too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            updateAAD(room.toByteArray())
        }
        return cipher.doFinal(raw, 12, raw.size - 12)
    }

    companion object {
        private val random = SecureRandom()

        fun create() = SyncCrypto(ByteArray(16).also { random.nextBytes(it) })

        /** Null when [code] is not a valid pairing code. */
        fun fromCode(code: String): SyncCrypto? {
            // 0, 1 and 8 are not in the alphabet: read them as the letters they are mistaken for.
            val clean = code.uppercase().filter { it.isLetterOrDigit() }.replace('0', 'O').replace('1', 'I').replace('8', 'B')
            if (clean.length != 26) return null
            return runCatching { Base32.decode(clean) }.getOrNull()?.takeIf { it.size == 16 }?.let(::SyncCrypto)
        }

    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = ((buffer shl 8) or (b.toInt() and 0xff)) and 0xffff
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Not a pairing code" }
            buffer = ((buffer shl 5) or v) and 0xffff
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
