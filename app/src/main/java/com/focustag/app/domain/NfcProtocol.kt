package com.focustag.app.domain

import java.util.Locale

object NfcProtocol {
    private val physicalRegistry = mapOf(
        "1D:FF:7C:1C:1A:10:80" to "Library 1",
        "1D:5B:70:1C:1A:10:80" to "Classroom 1",
        "1D:3D:70:1C:1A:10:80" to "Classroom 2"
    )

    /**
     * Normalizes a tag UID to the canonical uppercase colon-separated format.
     * Example: 1dff7c1c1a1080 -> 1D:FF:7C:1C:1A:10:80
     *
     * Handles simulated_tag_01 as a special case by returning it as-is.
     * Returns null for malformed or empty input.
     */
    fun normalize(uid: String): String? {
        if (uid.isBlank()) return null
        if (uid == "simulated_tag_01") return uid

        // Remove existing colons and check if it's valid hex
        val clean = uid.replace(":", "").uppercase(Locale.US)
        if (!clean.matches(Regex("^[0-9A-F]+$"))) {
            return null
        }
        
        // Protocol v1 tags are expected to be 7-byte (14 hex chars) or similar even-length hex.
        if (clean.length % 2 != 0) return null
        
        return clean.chunked(2).joinToString(":")
    }

    /**
     * Returns true if the UID is one of the three real prototype NFC tags.
     */
    fun isRegistered(uid: String): Boolean {
        return physicalRegistry.containsKey(uid)
    }

    /**
     * Returns the human-readable location for a registered tag UID.
     */
    fun getLocation(uid: String): String? {
        return physicalRegistry[uid]
    }
}
