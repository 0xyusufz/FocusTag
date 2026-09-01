package com.focustag.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focustag.app.data.repository.FocusSessionDto
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.time.Instant
import java.util.UUID

private const val TAG = "S2_LIFECYCLE_STRICT"

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class S2LifecycleSecurityTest {

    private val client = SupabaseModule.client
    private val args = InstrumentationRegistry.getArguments()

    private val userAEmail = args.getString("user_a_email") ?: throw IllegalArgumentException("Missing user_a_email")
    private val userAPassword = args.getString("user_a_password") ?: throw IllegalArgumentException("Missing user_a_password")

    companion object {
        private lateinit var userIdA: String

        // 8 sessions for 8 test cases
        private val SESSION_S2_1 = UUID.randomUUID().toString()
        private val SESSION_S2_2 = UUID.randomUUID().toString()
        private val SESSION_S2_3 = UUID.randomUUID().toString()
        private val SESSION_S2_4 = UUID.randomUUID().toString()
        private val SESSION_S2_5 = UUID.randomUUID().toString()
        private val SESSION_S2_6 = UUID.randomUUID().toString()
        private val SESSION_S2_7 = UUID.randomUUID().toString()
        private val SESSION_S2_8 = UUID.randomUUID().toString()

        private val ALL_S2_SESSIONS = listOf(
            SESSION_S2_1, SESSION_S2_2, SESSION_S2_3, SESSION_S2_4,
            SESSION_S2_5, SESSION_S2_6, SESSION_S2_7, SESSION_S2_8
        )
    }

    private suspend fun loginAs(email: String, pass: String): String {
        client.auth.signOut()
        client.auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
        return client.auth.currentSessionOrNull()?.user?.id ?: throw IllegalStateException("Auth Failure for $email")
    }

    private fun handleLifecycleRejection(e: Throwable, testNum: Int): Boolean {
        if (e is AssertionError) throw e

        if (e is RestException) {
            val status = e.response.status.value
            val message = e.message?.lowercase() ?: ""

            Log.i(
                TAG,
                "S2-$testNum: RestException status=$status message=$message"
            )

            if (
                status in 400..499 &&
                (
                        message.contains("enforce_monotonic_status") ||
                                message.contains("status_transition") ||
                                message.contains("check_status_transition") ||
                                message.contains("lifecycle regression blocked") ||
                                message.contains("terminal status pivot blocked")
                        )
            ) {
                Log.i(TAG, "S2-$testNum: Correctly rejected by lifecycle trigger.")
                return true
            }

            throw RuntimeException(
                "S2-$testNum FAIL: Unexpected RestException " +
                        "(status=$status, message=$message)",
                e
            )
        }

        val msg = e.message?.lowercase() ?: ""
        // Check for common trigger/monotonic message patterns
        if (msg.contains("enforce_monotonic_status") || msg.contains("status_transition") || msg.contains("check_status_transition")) {
            Log.i(TAG, "S2-$testNum: Correctly rejected by database trigger.")
            return true
        }

        throw RuntimeException("S2-$testNum FAIL: Unexpected technical error (not a lifecycle rejection): ${e.message}", e)
    }

    @Test
    fun step0_SetupLifecycleFixtures() {
        runBlocking {
            Log.i(TAG, "--- STEP 0: SETUP Lifecycle Fixtures (User A) ---")
            userIdA = loginAs(userAEmail, userAPassword)

            val now = Instant.now().toString()

            // S2-1, S2-2: start IN_PROGRESS
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_1, userIdA, "tag_s2_1", now, null, "IN_PROGRESS"))
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_2, userIdA, "tag_s2_2", now, null, "IN_PROGRESS"))

            // S2-3, S2-5, S2-7: start COMPLETED
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_3, userIdA, "tag_s2_3", now, now, "COMPLETED"))
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_5, userIdA, "tag_s2_5", now, now, "COMPLETED"))
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_7, userIdA, "tag_s2_7", now, now, "COMPLETED"))

            // S2-4, S2-6, S2-8: start INTERRUPTED
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_4, userIdA, "tag_s2_4", now, null, "INTERRUPTED"))
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_6, userIdA, "tag_s2_6", now, null, "INTERRUPTED"))
            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_S2_8, userIdA, "tag_s2_8", now, null, "INTERRUPTED"))

            Log.i(TAG, "Lifecycle fixtures established.")
        }
    }

    @Test
    fun step1_AllowedTransitions() {
        runBlocking {
            Log.i(TAG, "--- STEP 1: Testing Allowed Transitions ---")
            userIdA = loginAs(userAEmail, userAPassword)

            // S2-1: IN_PROGRESS → COMPLETED
            client.postgrest["focus_sessions"].update({
                set("status", "COMPLETED")
                set("end_at", Instant.now().toString())
            }) { filter { eq("id", SESSION_S2_1) } }
            
            val verify1 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_1) } }.decodeSingleOrNull<FocusSessionDto>()
            assertNotNull("S2-1: Row not found", verify1)
            assertEquals("S2-1 FAIL: Transition IN_PROGRESS -> COMPLETED rejected", "COMPLETED", verify1?.status)
            assertEquals("S2-1 FAIL: Ownership corrupted", userIdA, verify1?.userId)

            // S2-2: IN_PROGRESS → INTERRUPTED
            client.postgrest["focus_sessions"].update({
                set("status", "INTERRUPTED")
            }) { filter { eq("id", SESSION_S2_2) } }
            
            val verify2 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_2) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-2 FAIL: Transition IN_PROGRESS -> INTERRUPTED rejected", "INTERRUPTED", verify2?.status)

            // S2-3: COMPLETED → COMPLETED
            client.postgrest["focus_sessions"].update({
                set("status", "COMPLETED")
            }) { filter { eq("id", SESSION_S2_3) } }
            
            val verify3 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_3) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-3 FAIL: Transition COMPLETED -> COMPLETED rejected", "COMPLETED", verify3?.status)

            // S2-4: INTERRUPTED → INTERRUPTED
            client.postgrest["focus_sessions"].update({
                set("status", "INTERRUPTED")
            }) { filter { eq("id", SESSION_S2_4) } }
            
            val verify4 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_4) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-4 FAIL: Transition INTERRUPTED -> INTERRUPTED rejected", "INTERRUPTED", verify4?.status)

            Log.i(TAG, "Allowed transitions PASS.")
        }
    }

    @Test
    fun step2_BlockedTransitions() {
        runBlocking {
            Log.i(TAG, "--- STEP 2: Testing Blocked Transitions ---")
            userIdA = loginAs(userAEmail, userAPassword)

            // S2-5: COMPLETED → IN_PROGRESS
            try {
                client.postgrest["focus_sessions"].update({ set("status", "IN_PROGRESS") }) { filter { eq("id", SESSION_S2_5) } }
                fail("S2-5 FAIL: COMPLETED -> IN_PROGRESS was not blocked")
            } catch (e: Throwable) {
                handleLifecycleRejection(e, 5)
            }
            val verify5 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_5) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-5 Physical Verification: Status must remain COMPLETED", "COMPLETED", verify5?.status)
            Log.i(TAG, "S2-5 BLOCK PASS")

            // S2-6: INTERRUPTED → IN_PROGRESS
            try {
                client.postgrest["focus_sessions"].update({ set("status", "IN_PROGRESS") }) { filter { eq("id", SESSION_S2_6) } }
                fail("S2-6 FAIL: INTERRUPTED -> IN_PROGRESS was not blocked")
            } catch (e: Throwable) {
                handleLifecycleRejection(e, 6)
            }
            val verify6 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_6) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-6 Physical Verification: Status must remain INTERRUPTED", "INTERRUPTED", verify6?.status)
            Log.i(TAG, "S2-6 BLOCK PASS")

            // S2-7: COMPLETED → INTERRUPTED
            try {
                client.postgrest["focus_sessions"].update({ set("status", "INTERRUPTED") }) { filter { eq("id", SESSION_S2_7) } }
                fail("S2-7 FAIL: COMPLETED -> INTERRUPTED was not blocked")
            } catch (e: Throwable) {
                handleLifecycleRejection(e, 7)
            }
            val verify7 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_7) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-7 Physical Verification: Status must remain COMPLETED", "COMPLETED", verify7?.status)
            Log.i(TAG, "S2-7 BLOCK PASS")

            // S2-8: INTERRUPTED → COMPLETED
            try {
                client.postgrest["focus_sessions"].update({ set("status", "COMPLETED") }) { filter { eq("id", SESSION_S2_8) } }
                fail("S2-8 FAIL: INTERRUPTED -> COMPLETED was not blocked")
            } catch (e: Throwable) {
                handleLifecycleRejection(e, 8)
            }
            val verify8 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_S2_8) } }.decodeSingleOrNull<FocusSessionDto>()
            assertEquals("S2-8 Physical Verification: Status must remain INTERRUPTED", "INTERRUPTED", verify8?.status)
            Log.i(TAG, "S2-8 BLOCK PASS")

            Log.i(TAG, "Blocked transition attempts completed.")
        }
    }

    @Test
    fun step3_FinalDatabaseVerification() {
        runBlocking {
            Log.i(TAG, "--- STEP 3: PHYSICAL DATABASE VERIFICATION ---")
            userIdA = loginAs(userAEmail, userAPassword)

            val sessionMap = mutableMapOf<String, FocusSessionDto>()
            for (id in ALL_S2_SESSIONS) {
                val sess = client.postgrest["focus_sessions"].select {
                    filter { eq("id", id) }
                }.decodeSingleOrNull<FocusSessionDto>()
                if (sess != null) {
                    sessionMap[id] = sess
                }
            }

            assertEquals("Verification: Expected 8 sessions in DB", 8, sessionMap.size)

            // S2-1: IN_PROGRESS -> COMPLETED (Allowed)
            assertEquals("S2-1 Final State", "COMPLETED", sessionMap[SESSION_S2_1]?.status)
            // S2-2: IN_PROGRESS -> INTERRUPTED (Allowed)
            assertEquals("S2-2 Final State", "INTERRUPTED", sessionMap[SESSION_S2_2]?.status)
            // S2-3: COMPLETED -> COMPLETED (Allowed)
            assertEquals("S2-3 Final State", "COMPLETED", sessionMap[SESSION_S2_3]?.status)
            // S2-4: INTERRUPTED -> INTERRUPTED (Allowed)
            assertEquals("S2-4 Final State", "INTERRUPTED", sessionMap[SESSION_S2_4]?.status)

            // S2-5: COMPLETED -> IN_PROGRESS (Blocked)
            assertEquals("S2-5 Final State", "COMPLETED", sessionMap[SESSION_S2_5]?.status)
            // S2-6: INTERRUPTED -> IN_PROGRESS (Blocked)
            assertEquals("S2-6 Final State", "INTERRUPTED", sessionMap[SESSION_S2_6]?.status)
            // S2-7: COMPLETED -> INTERRUPTED (Blocked)
            assertEquals("S2-7 Final State", "COMPLETED", sessionMap[SESSION_S2_7]?.status)
            // S2-8: INTERRUPTED -> COMPLETED (Blocked)
            assertEquals("S2-8 Final State", "INTERRUPTED", sessionMap[SESSION_S2_8]?.status)

            // Ownership check for all
            sessionMap.values.forEach {
                assertEquals("Ownership verification for ${it.id}", userIdA, it.userId)
            }

            Log.i(TAG, "--- S2 LIFECYCLE SECURITY TESTS PASS ---")
        }
    }

    @Test
    fun step9_PrintCleanupIds() {
        Log.i(TAG, "--- S2_LIFECYCLE_STRICT CLEANUP IDS ---")
        ALL_S2_SESSIONS.forEachIndexed { index, id ->
            Log.i(TAG, "SESSION_S2_${index + 1}: $id")
        }
    }
}
