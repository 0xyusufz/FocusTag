package com.focustag.app.data.repository

import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun signUp(email: String, password: String): Result<Unit>
}

class SupabaseAuthRepository : AuthRepository {
    override val sessionStatus: StateFlow<SessionStatus> = SupabaseModule.client.auth.sessionStatus

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            SupabaseModule.client.auth.signUpWith(Email, redirectUrl = "focustag://auth/callback") {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
