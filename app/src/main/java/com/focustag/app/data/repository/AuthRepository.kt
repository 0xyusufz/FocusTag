package com.focustag.app.data.repository

import android.util.Log
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "AuthRepo"

interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun signUp(email: String, password: String, name: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}

class SupabaseAuthRepository : AuthRepository {
    override val sessionStatus: StateFlow<SessionStatus> = SupabaseModule.client.auth.sessionStatus

    override suspend fun signUp(email: String, password: String, name: String): Result<Unit> {
        return try {
            Log.d(TAG, "Signing up user...")
            SupabaseModule.client.auth.signUpWith(Email, redirectUrl = "focustag://auth/callback") {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("name", name)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Signup failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            SupabaseModule.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            SupabaseModule.client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
