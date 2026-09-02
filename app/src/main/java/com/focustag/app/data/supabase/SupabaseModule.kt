package com.focustag.app.data.supabase

import android.content.Context
import com.focustag.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.user.UserSession
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Persists Supabase session to SharedPreferences to ensure background sync
 * works after app process restart.
 */
class SharedPreferencesSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    
    override suspend fun saveSession(session: UserSession) {
        Log.d("SupabaseModule", "Saving session to prefs")
        prefs.edit().putString("session", Json.encodeToString(session)).apply()
    }
    
    override suspend fun loadSession(): UserSession? {
        Log.d("SupabaseModule", "Loading session from prefs...")
        val json = prefs.getString("session", null)
        if (json == null) {
            Log.d("SupabaseModule", "No session found in prefs")
            return null
        }
        Log.d("SupabaseModule", "Session JSON found, length: ${json.length}")
        return try {
            val session = Json.decodeFromString<UserSession>(json)
            Log.d("SupabaseModule", "Session decoded successfully. User ID: ${session.user?.id}")
            session
        } catch (e: Throwable) {
            Log.e("SupabaseModule", "Error decoding session JSON: ${e.message}", e)
            null
        }
    }
    
    override suspend fun deleteSession() {
        Log.d("SupabaseModule", "Deleting session from prefs")
        prefs.edit().remove("session").apply()
    }
}

object SupabaseModule {
    private var _client: SupabaseClient? = null
    
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("SupabaseModule not initialized. Call initialize(context) first.")

    @Synchronized
    fun initialize(context: Context) {
        if (_client != null) {
            Log.d("SupabaseModule", "Already initialized")
            return
        }
        
        Log.d("SupabaseModule", "Initializing Supabase client...")
        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                scheme = "focustag"
                host = "auth"
                sessionManager = SharedPreferencesSessionManager(context.applicationContext)
            }
            install(Postgrest)
        }
        Log.d("SupabaseModule", "Supabase client created")
    }
}
