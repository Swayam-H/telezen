package com.telezen.app.core

import java.security.MessageDigest
import java.security.SecureRandom

object Pin {
    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String): String {
        var digest = (salt + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(10_000) { digest = md.digest(digest) }
        return digest.toHex()
    }

    fun verify(pin: String, salt: String?, hash: String?): Boolean =
        salt != null && hash != null && MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
