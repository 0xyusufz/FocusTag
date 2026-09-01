package com.focustag.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focustag.app.data.repository.FocusSessionDto
import com.focustag.app.data.repository.InterceptionEventDto
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

private const val TAG = "S1_SECURITY_STRICT"

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class S1RlsSecurityTest {

    private val client = SupabaseModule.client
    private val args = InstrumentationRegistry.getArguments()

    private val userAEmail = args.getString("user_a_email") ?: throw IllegalArgumentException("Missing user_a_email")
    private val userAPassword = args.getString("user_a_password") ?: throw IllegalArgumentException("Missing user_a_password")
    private val userBEmail = args.getString("user_b_email") ?: throw IllegalArgumentException("Missing user_b_email")
    private val userBPassword = args.getString("user_b_password") ?: throw IllegalArgumentException("Missing user_b_password")

    companion object {
        private lateinit var userIdA: String
        private lateinit var userIdB: String

        // Stable UUIDs for setup
        private val SESSION_A = UUID.randomUUID().toString()
        private val SESSION_B = UUID.randomUUID().toString()
        private val EVENT_A = UUID.randomUUID().toString()
        private val EVENT_B = UUID.randomUUID().toString()

        // Attack UUIDs
        private val MALICIOUS_SESSION_ID = UUID.randomUUID().toString()
        private val MALICIOUS_EVENT_ID = UUID.randomUUID().toString()

        private const val B_ORIGINAL_TAG = "tag_b_stable"
        private const val B_ORIGINAL_PKG = "com.victim.app"

        // Rejection flags (to be asserted in verification phase)
        private var t2_rejected = false
        private var t3_rejected = false
        private var t4_rejected = false
        private var t8_rejected = false
        private var t9_rejected = false
        private var t10_rejected = false
    }

    private suspend fun loginAs(email: String, pass: String): String {
        client.auth.signOut()
        client.auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
        return client.auth.currentSessionOrNull()?.user?.id ?: throw IllegalStateException("Auth Failure for $email")
    }

    /**
     * Strictly classifies exceptions. Technical failures (network, timeout, auth) 
     * will fail the test. Only PostgREST/RLS/Permission errors are acceptable rejections.
     */
    private fun classifyAttackResult(e: Throwable?, testNum: Int): Boolean {
        if (e == null) {
            Log.w(TAG, "TEST $testNum: No exception thrown. Verifying RLS no-op via database state.")
            return true
        }
        if (e is AssertionError) throw e

        if (e is RestException) {
            val status = e.response.status.value
            // 403: Forbidden, 401: Unauthorized, 404: Hidden by RLS
            if (status == 403 || status == 401 || status == 404) {
                Log.i(TAG, "TEST $testNum: Correctly rejected by server (HTTP $status)")
                return true
            }
        }

        val msg = e.message?.lowercase() ?: ""
        if (msg.contains("permission denied") || msg.contains("rls policy") || msg.contains("42501")) {
            Log.i(TAG, "TEST $testNum: Correctly rejected by database driver.")
            return true
        }

        throw RuntimeException("TEST $testNum FAIL: Unexpected technical error: ${e.message}", e)
    }

    @Test
    fun step0_SetupVictimData() {
        runBlocking {
            Log.i(TAG, "--- STEP 0: SETUP Baseline (User B) ---")
            userIdB = loginAs(userBEmail, userBPassword)

            client.postgrest["focus_sessions"].insert(FocusSessionDto(SESSION_B, userIdB, B_ORIGINAL_TAG, Instant.now().toString(), null, "IN_PROGRESS"))
            client.postgrest["interception_events"].insert(InterceptionEventDto(EVENT_B, userIdB, SESSION_B, B_ORIGINAL_PKG, Instant.now().toString()))
            Log.i(TAG, "Baseline established.")
        }

    }

    @Test
    fun step1_UserA_OwnPermissionTests() = runBlocking {
        Log.i(TAG, "--- STEP 1: USER A Legitimate Permissions ---")
        userIdA = loginAs(userAEmail, userAPassword)

        // TEST 5: Create own session
        val sessionA = FocusSessionDto(SESSION_A, userIdA, "tag_a", Instant.now().toString(), null, "IN_PROGRESS")
        client.postgrest["focus_sessions"].insert(sessionA)

        // TEST 1: Physical verification of read + ownership
        val verifyA = client.postgrest["focus_sessions"].select {
            filter { eq("id", SESSION_A) }
        }.decodeSingleOrNull<FocusSessionDto>()
        assertNotNull("TEST 1/5 FAIL: Session A not found or not readable by owner", verifyA)
        assertEquals("TEST 1 FAIL: Owner identity mismatch", userIdA, verifyA?.userId)
        assertEquals("TEST 1 FAIL: Data corruption", "tag_a", verifyA?.tagId)

        // TEST 6: Update own session
        client.postgrest["focus_sessions"].update({ set("status", "COMPLETED") }) { filter { eq("id", SESSION_A) } }
        val verify6 = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_A) } }.decodeSingleOrNull<FocusSessionDto>()
        assertEquals("TEST 6 FAIL: Update not applied to own row", "COMPLETED", verify6?.status)

        // TEST 7: Create own event
        val eventA = InterceptionEventDto(EVENT_A, userIdA, SESSION_A, "com.a.app", Instant.now().toString())
        client.postgrest["interception_events"].insert(eventA)
        val verify7 = client.postgrest["interception_events"].select { filter { eq("id", EVENT_A) } }.decodeSingleOrNull<InterceptionEventDto>()
        assertNotNull("TEST 7 FAIL: Event not found after owner insert", verify7)
        assertEquals("TEST 7 FAIL: Event owner mismatch", userIdA, verify7?.userId)
    }

    @Test
    fun step2_UserA_AttackPhase() {
        runBlocking {
            Log.i(TAG, "--- STEP 2: USER A RLS Attacks ---")
            userIdA = loginAs(userAEmail, userAPassword)

            // TEST 2: Attempt Update User B row
            try { client.postgrest["focus_sessions"].update({ set("tag_id", "HACK") }) { filter { eq("id", SESSION_B) } }; t2_rejected = classifyAttackResult(null, 2) }
            catch (e: Throwable) { t2_rejected = classifyAttackResult(e, 2) }

            // TEST 3: Attempt Identity Spoofing (user_id = userIdB)
            try { client.postgrest["focus_sessions"].insert(FocusSessionDto(MALICIOUS_SESSION_ID, userIdB, "spoof", Instant.now().toString(), null, "IN_PROGRESS")); t3_rejected = classifyAttackResult(null, 3) }
            catch (e: Throwable) { t3_rejected = classifyAttackResult(e, 3) }

            // TEST 4: Attempt DELETE User B session (Expect explicit rejection)
            try {
                client.postgrest["focus_sessions"].delete {
                    filter { eq("id", SESSION_B) }
                }
                fail("TEST 4 FAIL: DELETE was not rejected by permission")
            } catch (e: Throwable) {
                if (e is AssertionError) throw e

                if (e is RestException) {
                    val status = e.response.status.value

                    if (status == 403) {
                        t4_rejected = true
                    } else {
                        throw RuntimeException(
                            "TEST 4 FAIL: DELETE returned unexpected HTTP status $status",
                            e
                        )
                    }
                } else {
                    val msg = e.message?.lowercase() ?: ""

                    if (msg.contains("permission denied") || msg.contains("42501")) {
                        t4_rejected = true
                    } else {
                        throw RuntimeException(
                            "TEST 4 FAIL: Unexpected technical error: ${e.message}",
                            e
                        )
                    }
                }
            }

            // TEST 8: Attempt Event Hijacking (User A creates event for User B session)
            try { client.postgrest["interception_events"].insert(InterceptionEventDto(MALICIOUS_EVENT_ID, userIdA, SESSION_B, "com.hack", Instant.now().toString())); t8_rejected = classifyAttackResult(null, 8) }
            catch (e: Throwable) { t8_rejected = classifyAttackResult(e, 8) }

            // TEST 9: Attempt Update User B event
            try { client.postgrest["interception_events"].update({ set("package_name", "HACK") }) { filter { eq("id", EVENT_B) } }; t9_rejected = classifyAttackResult(null, 9) }
            catch (e: Throwable) { t9_rejected = classifyAttackResult(e, 9) }

            // TEST 10: Attempt DELETE User B event
            try {
                client.postgrest["interception_events"].delete {
                    filter { eq("id", EVENT_B) }
                }
                fail("TEST 10 FAIL: DELETE was not rejected")
            } catch (e: Throwable) {
                if (e is AssertionError) throw e

                if (e is RestException) {
                    val status = e.response.status.value

                    if (status == 403) {
                        t10_rejected = true
                    } else {
                        throw RuntimeException(
                            "TEST 10 FAIL: DELETE returned unexpected HTTP status $status",
                            e
                        )
                    }
                } else {
                    val msg = e.message?.lowercase() ?: ""

                    if (msg.contains("permission denied") || msg.contains("42501")) {
                        t10_rejected = true
                    } else {
                        throw RuntimeException(
                            "TEST 10 FAIL: Unexpected technical error: ${e.message}",
                            e
                        )
                    }
                }
            }

            Log.i(TAG, "Attack attempts completed.")
        }
    }

    @Test
    fun step3_Victim_FinalVerification() {
        runBlocking {
            Log.i(TAG, "--- STEP 3: PHYSICAL State Verification (User B) ---")
            loginAs(userBEmail, userBPassword)

            // Verify TEST 2 & 4
            val verifyB = client.postgrest["focus_sessions"].select { filter { eq("id", SESSION_B) } }.decodeSingleOrNull<FocusSessionDto>()
            assertNotNull("TEST 4 FAIL: Session B was deleted by User A!", verifyB)
            assertEquals("TEST 2 FAIL: Session B data corrupted!", B_ORIGINAL_TAG, verifyB?.tagId)
            assertTrue("TEST 4 REJECTION: DELETE permission must be rejected", t4_rejected)

            // Verify TEST 3
            val verifyMaliciousSess = client.postgrest["focus_sessions"].select { filter { eq("id", MALICIOUS_SESSION_ID) } }.decodeSingleOrNull<FocusSessionDto>()
            assertNull("TEST 3 FAIL: User A successfully spoofed User B user_id!", verifyMaliciousSess)

            // Verify TEST 9 & 10
            val verifyEvB = client.postgrest["interception_events"].select { filter { eq("id", EVENT_B) } }.decodeSingleOrNull<InterceptionEventDto>()
            assertNotNull("TEST 10 FAIL: Event B was deleted by User A!", verifyEvB)
            assertEquals("TEST 9 FAIL: Event B data corrupted!", B_ORIGINAL_PKG, verifyEvB?.packageName)
            assertTrue("TEST 10 REJECTION: DELETE permission must be rejected", t10_rejected)

            // Verify TEST 8
            // Verify TEST 8 from User A's perspective.
// The malicious event uses User A as owner, so User A must be able
// to see it if the INSERT actually succeeded.
            loginAs(userAEmail, userAPassword)

            val verifyMaliciousEv = client.postgrest["interception_events"].select {
                filter { eq("id", MALICIOUS_EVENT_ID) }
            }.decodeSingleOrNull<InterceptionEventDto>()


            assertNull(
                "TEST 8 FAIL: Malicious event was successfully created and linked to User B session!",
                verifyMaliciousEv
            )

            // Final assertions on rejection flags to distinguish no-op from tech-fail
            assertTrue("TEST 2 rejection logic failure", t2_rejected)
            assertTrue("TEST 3 rejection logic failure", t3_rejected)
            assertTrue("TEST 8 rejection logic failure", t8_rejected)
            assertTrue("TEST 9 rejection logic failure", t9_rejected)

            Log.i(TAG, "--- S1 SECURITY VERIFICATION PASS ---")
        }

    }

    @Test
    fun step9_PrintCleanupIds() {
        Log.i(TAG, "--- CLEANUP IDS ---")
        Log.i(TAG, "SESSION_A: $SESSION_A")
        Log.i(TAG, "SESSION_B: $SESSION_B")
        Log.i(TAG, "EVENT_A: $EVENT_A")
        Log.i(TAG, "EVENT_B: $EVENT_B")
        Log.i(TAG, "MAL_SESSION: $MALICIOUS_SESSION_ID")
        Log.i(TAG, "MAL_EVENT: $MALICIOUS_EVENT_ID")
    }
}
