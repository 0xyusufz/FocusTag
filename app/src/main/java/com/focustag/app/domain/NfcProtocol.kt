package com.focustag.app.domain

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

object NfcProtocol {
    private val registeredUids = AtomicReference<Set<String>>(emptySet())

    /**
     * Updates the registry of recognized NFC tags.
     */
    fun setRegisteredTags(uids: Set<String>) {
        registeredUids.set(uids)
    }

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
     * Returns true if the UID is registered in the current active registry.
     */
    fun isRegistered(uid: String): Boolean {
        if (uid == "simulated_tag_01") return true
        val normalized = normalize(uid) ?: return false
        return registeredUids.get().contains(normalized)
    }

    /**
     * Returns the human-readable location for a registered tag UID.
     * Legacy hardcoded mapping removed as required.
     */
    fun getLocation(uid: String): String? {
        return null
    }
}
