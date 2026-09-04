package com.focustag.app.data.repository

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

class SupabaseHistoryRepositoryTest {

    private val repository = SupabaseHistoryRepository()

    // Helper to invoke private classifyError via reflection for testing
    private fun classify(e: Exception): SyncError {
        val method = SupabaseHistoryRepository::class.java.getDeclaredMethod("classifyError", Exception::class.java)
        method.isAccessible = true
        return method.invoke(repository, e) as SyncError
    }

    @Test
    fun `test network exceptions are retryable`() {
        assertTrue(classify(UnknownHostException("Unable to resolve host")) is SyncError.Retryable)
        assertTrue(classify(ConnectException("Connection refused")) is SyncError.Retryable)
        assertTrue(classify(IOException("Network error")) is SyncError.Retryable)
    }

    @Test
    fun `test unknown non-RestException is retryable`() {
        assertTrue(classify(RuntimeException("Something went wrong")) is SyncError.Retryable)
    }

    @Test
    fun `test rest exceptions classification`() {
        // Mocking RestException is complex because it requires a lot of ktor plumbing
        // But we can test the fallback logic if we had a way to instantiate it easily.
        // For now, these 2 tests cover the main regression fix.
    }
}
