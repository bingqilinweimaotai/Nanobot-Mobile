package dev.nanobot.tunnel

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for relay data channels.
 *
 * The relay forwards opaque frames. The phone and the PC agent share a 32-byte key delivered by the
 * pairing QR, so the relay never sees the gateway secret, prompts, or replies.
 *
 * The 12-byte nonce is prefixed to every packet. The pairing key is static for the life of a pairing;
 * rotating it means generating a new pairing link.
 */
object TunnelCrypto {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun generateKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun encodeKey(key: ByteArray): String {
        require(key.size == KEY_BYTES) { "pair key must be $KEY_BYTES bytes" }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key)
    }

    fun decodeKey(text: String): ByteArray {
        val key = runCatching { Base64.getUrlDecoder().decode(text.trim()) }
            .getOrElse { throw TunnelCryptoException("invalid pair key encoding") }
        if (key.size != KEY_BYTES) throw TunnelCryptoException("pair key must be $KEY_BYTES bytes")
        return key
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        if (key.size != KEY_BYTES) throw TunnelCryptoException("invalid pair key")
        return try {
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            nonce + cipher.doFinal(plaintext)
        } catch (error: GeneralSecurityException) {
            throw TunnelCryptoException("could not encrypt relay frame", error)
        }
    }

    fun decrypt(key: ByteArray, packet: ByteArray): ByteArray {
        if (key.size != KEY_BYTES) throw TunnelCryptoException("invalid pair key")
        if (packet.size <= NONCE_BYTES) throw TunnelCryptoException("relay frame is too short")
        return try {
            val nonce = packet.copyOfRange(0, NONCE_BYTES)
            val ciphertext = packet.copyOfRange(NONCE_BYTES, packet.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.doFinal(ciphertext)
        } catch (error: GeneralSecurityException) {
            throw TunnelCryptoException("could not decrypt relay frame", error)
        }
    }
}

class TunnelCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
