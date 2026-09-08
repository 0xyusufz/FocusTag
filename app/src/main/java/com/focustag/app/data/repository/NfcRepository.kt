package com.focustag.app.data.repository

import android.content.Context
import android.util.Log
import com.focustag.app.data.model.Profile
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class NfcTagWithLocationDto(
    val uid: String,
    val locations: LocationNameDto
)

@Serializable
data class LocationNameDto(val name: String)

@Serializable
data class NfcRegistryCache(
    val activeUids: Set<String>,
    val tagDisplayNames: Map<String, String>, // New field for UID -> Location Name mapping
    val fetchedForInstitutionId: String?,
    val fetchedAtMillis: Long,
    val schemaVersion: Int = 1
)

open class NfcRepository(private val context: Context?, private val userId: String) {
    private val prefs by lazy {
        context?.getSharedPreferences("focus_nfc_registry_$userId", Context.MODE_PRIVATE)
    }

    private companion object {
        const val TAG = "NfcRepo"
        const val KEY_CACHE = "registry_cache"
    }

    open suspend fun fetchActiveTagsWithNames(): Result<Map<String, String>> {
        return try {
            Log.d(TAG, "Fetching active tags with location names from Supabase...")
            // Dynamic resolution: nfc_tags.uid -> locations.name
            val response = SupabaseModule.client.from("nfc_tags")
                .select(columns = Columns.raw("uid, locations(name)")) {
                    filter {
                        eq("is_active", true)
                    }
                }
                .decodeList<NfcTagWithLocationDto>()
            
            val tagMap = response.associate { it.uid to it.locations.name }
            Result.success(tagMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch NFC tags with names: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun fetchActiveTags(): Result<Set<String>> {
        return fetchActiveTagsWithNames().map { it.keys }
    }

    open suspend fun fetchProfile(): Result<Profile?> {
        return try {
            val profile = SupabaseModule.client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<Profile>()
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch profile: ${e.message}")
            Result.failure(e)
        }
    }

    open fun getCache(): NfcRegistryCache? {
        val json = prefs?.getString(KEY_CACHE, null) ?: return null
        return try {
            Json.decodeFromString<NfcRegistryCache>(json)
        } catch (e: Exception) {
            null
        }
    }

    open fun saveCache(cache: NfcRegistryCache) {
        prefs?.edit()?.putString(KEY_CACHE, Json.encodeToString(cache))?.apply()
    }
}
