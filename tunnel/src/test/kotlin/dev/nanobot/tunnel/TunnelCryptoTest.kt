package dev.nanobot.tunnel

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TunnelCryptoTest {
    @Test
    fun `encrypts and decrypts a frame`() {
        val key = TunnelCrypto.generateKey()
        val plaintext = "hello nanobot".toByteArray()
        val packet = TunnelCrypto.encrypt(key, plaintext)

        assertNotEquals(plaintext.toList(), packet.toList())
        assertContentEquals(plaintext, TunnelCrypto.decrypt(key, packet))
    }

    @Test
    fun `tampered frames fail authentication`() {
        val key = TunnelCrypto.generateKey()
        val packet = TunnelCrypto.encrypt(key, "secret".toByteArray())
        packet[packet.lastIndex] = (packet.last().toInt() xor 0x01).toByte()

        assertFailsWith<TunnelCryptoException> {
            TunnelCrypto.decrypt(key, packet)
        }
    }

    @Test
    fun `key encoding is url safe and round trips`() {
        val key = TunnelCrypto.generateKey()
        val encoded = TunnelCrypto.encodeKey(key)
        assertContentEquals(key, TunnelCrypto.decodeKey(encoded))
        assertEquals(null, encoded.find { it == '+' || it == '/' || it == '=' })
    }
}
