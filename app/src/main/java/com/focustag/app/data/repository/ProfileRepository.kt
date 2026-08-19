package com.focustag.app.data.repository

import android.util.Log
import com.focustag.app.data.model.Profile
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

private const val TAG = "ProfileRepo"

interface ProfileRepository {
    suspend fun getProfile(userId: String): Result<Profile?>
    suspend fun updateName(userId: String, name: String): Result<Unit>
}

class SupabaseProfileRepository : ProfileRepository {
    override suspend fun getProfile(userId: String): Result<Profile?> {
        return try {
            Log.d(TAG, "Fetching profile for $userId")
            val result = SupabaseModule.client.from("profiles")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }
            val profile = result.decodeSingleOrNull<Profile>()
            Log.d(TAG, "Fetch result: $profile")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateName(userId: String, name: String): Result<Unit> {
        return try {
            Log.d(TAG, "Updating name for $userId to $name")
            SupabaseModule.client.from("profiles").update({
                set("name", name)
            }) {
                filter {
                    eq("id", userId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Update failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
