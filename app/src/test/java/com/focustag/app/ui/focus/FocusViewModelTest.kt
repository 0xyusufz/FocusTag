package com.focustag.app.ui.focus

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.focustag.app.data.model.*
import com.focustag.app.data.repository.*
import com.focustag.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeRepo: FakeFocusRepository
    private lateinit var fakeCoordinator: FakeEnforcementCoordinator
    private lateinit var fakeNfcRepo: FakeNfcRepository
    private lateinit var fakeContext: FakeContext

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Reset static state
        NfcProtocol.setRegisteredTags(emptySet())

        fakeRepo = FakeFocusRepository()
        fakeNfcRepo = FakeNfcRepository()
        fakeContext = FakeContext()
        
        // Pre-verify institution for the default test user to allow cache loading by default
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        val fakeInv = object : AppInventoryRepository(null as android.content.Context?) {
            override fun getInstalledApps(): List<AppInfo> = emptyList()
        }
        val fakePol = object : AppPolicyRepository(null as android.content.Context?, "user1") {
            override fun getBlockedApps(): Set<String> = emptySet()
        }
        val fakeEnfRepo = object : EnforcementRepository(null as android.content.Context?, "user1") {
            override fun getStatus() = EnforcementStatus.IDLE
            override fun getSnapshot(): EnforcementSnapshot? = null
            override fun getLedger(): EnforcementLedger = EnforcementLedger()
            override fun saveStatus(status: EnforcementStatus) {}
            override fun saveSnapshot(snapshot: EnforcementSnapshot?) {}
            override fun saveLedger(ledger: EnforcementLedger) {}
            override fun isDeviceOwner(): Boolean = true
            override fun getDeviceEnforcementOwnerId(): String? = null
        }
        val fakeHist = object : SessionHistoryRepository(null as android.content.Context?, "user1") {
            override fun getSessions(): List<FocusSessionRecord> = emptyList()
            override fun getEvents(): List<InterceptionEvent> = emptyList()
        }
        
        fakeCoordinator = FakeEnforcementCoordinator(fakeInv, fakePol, fakeEnfRepo, fakeHist)

        if (fakeRepo.savedState.focusState == FocusState.FOCUS_ACTIVE) {
            fakeCoordinator.setStatus(EnforcementStatus.ENFORCEMENT_ACTIVE)
        } else {
            fakeCoordinator.setStatus(EnforcementStatus.IDLE)
        }
    }

    private fun createViewModel(userId: String = "user1", nfcRepo: NfcRepository? = null) = object : FocusViewModel(
        context = fakeContext, 
        focusRepository = fakeRepo,
        enforcementCoordinator = fakeCoordinator,
        nfcRepository = nfcRepo ?: fakeNfcRepo,
        currentUserIdProvider = { userId }
    ) {
        override fun refreshAccessibilityCapability() {
            _accessibilityCapability.update { AccessibilityCapability.ACCESSIBILITY_READY }
        }
        override fun refreshNfcCapability() {
            _nfcCapability.update { NfcCapability.NFC_READY }
        }
        override fun refreshEnforcementStatus() {}
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        NfcProtocol.setRegisteredTags(emptySet())
    }

    // --- Fakes ---

    open class FakeNfcRepository(val uId: String = "user1") : NfcRepository(null as android.content.Context?, uId) {
        var tags = setOf("1D:FF:7C:1C:1A:10:80", "1D:5B:70:1C:1A:10:80")
        var tagMap = mapOf(
            "1D:FF:7C:1C:1A:10:80" to "Library 1",
            "1D:5B:70:1C:1A:10:80" to "Classroom 1"
        )
        var currentCache: NfcRegistryCache? = NfcRegistryCache(tags, tagMap, "inst1", System.currentTimeMillis())
        var profile: Profile? = Profile(uId, "User 1", "student", "inst1")
        
        var fetchTagsCount = 0
        var fetchProfileCount = 0
        
        var shouldFailProfile = false
        var shouldFailTags = false

        override fun getCache(): NfcRegistryCache? = currentCache
        override fun saveCache(cache: NfcRegistryCache) { this.currentCache = cache }
        
        override suspend fun fetchActiveTagsWithNames(): Result<Map<String, String>> {
            fetchTagsCount++
            return if (shouldFailTags) Result.failure(Exception("Tags fetch failed")) else Result.success(tagMap)
        }
        
        override suspend fun fetchActiveTags(): Result<Set<String>> {
            return fetchActiveTagsWithNames().map { it.keys }
        }
        
        override suspend fun fetchProfile(): Result<Profile?> {
            fetchProfileCount++
            return if (shouldFailProfile) Result.failure(Exception("Profile fetch failed")) else Result.success(profile)
        }
    }

    class FakeContext : android.content.ContextWrapper(null) {
        private val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            return prefsMap.getOrPut(name) { FakeSharedPreferences() }
        }
    }

    class FakeSharedPreferences : android.content.SharedPreferences {
        private val data = mutableMapOf<String, String>()
        override fun getAll(): Map<String, *> = data
        override fun getString(key: String, defValue: String?): String? = data[key] ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit() = FakeEditor(data)
        
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = null
        override fun getInt(key: String, defValue: Int): Int = 0
        override fun getLong(key: String, defValue: Long): Long = 0L
        override fun getFloat(key: String, defValue: Float): Float = 0f
        override fun getBoolean(key: String, defValue: Boolean): Boolean = false
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class FakeEditor(private val data: MutableMap<String, String>) : android.content.SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { if (value != null) data[key] = value else data.remove(key) }
        override fun remove(key: String) = apply { data.remove(key) }
        override fun apply() {}
        override fun commit(): Boolean = true
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun clear() = this
    }

    class FakeFocusRepository : FocusRepository(null as android.content.Context?, "user1") {
        var savedState = FocusSessionState()
        override fun getFocusSessionState() = savedState
        override fun saveFocusSessionState(state: FocusSessionState) {
            savedState = state
        }
    }

    class FakeEnforcementCoordinator(
        inv: AppInventoryRepository,
        pol: AppPolicyRepository,
        enf: EnforcementRepository,
        hist: SessionHistoryRepository
    ) : EnforcementCoordinator("user1", inv, pol, enf, hist, object : EnforcementStrategy {
        override suspend fun apply(snapshot: EnforcementSnapshot) = EnforcementResult.Success(EnforcementLedger())
        override suspend fun release(ledger: EnforcementLedger) = EnforcementResult.Success(EnforcementLedger())
        override suspend fun reconcile(snapshot: EnforcementSnapshot, ledger: EnforcementLedger) = EnforcementResult.Success(EnforcementLedger())
    }) {
        
        private val _status = MutableStateFlow(EnforcementStatus.IDLE)
        override val status = _status.asStateFlow()

        var startCalled = false
        var stopCalled = false
        var startTagId: String? = null

        var startResultStatus = EnforcementStatus.ENFORCEMENT_ACTIVE
        var stopResultStatus = EnforcementStatus.IDLE

        fun setStatus(s: EnforcementStatus) {
            _status.value = s
        }

        override suspend fun startEnforcement(tagId: String?) {
            startCalled = true
            startTagId = tagId
            _status.value = startResultStatus
        }

        override suspend fun stopEnforcement() {
            stopCalled = true
            _status.value = stopResultStatus
        }
        
        override fun refreshStatus(isAccessibilityReady: Boolean) {}
        override suspend fun reconcile() {}
        override suspend fun checkAndHandleOrphans() {}
    }

    // --- Test Cases ---

    @Test
    fun testCaseA_STARTsuccess() = runTest {
        val libTag = "1D:FF:7C:1C:1A:10:80"
        NfcProtocol.setRegisteredTags(setOf(libTag))
        
        val vm = createViewModel()
        advanceUntilIdle()
        
        vm.onTagEvent(rawToTag(libTag))
        advanceUntilIdle()

        assertTrue(fakeCoordinator.startCalled)
    }

    @Test
    fun testCaseB_STOPsuccess() = runTest {
        val libTag = "1D:FF:7C:1C:1A:10:80"
        NfcProtocol.setRegisteredTags(setOf(libTag))
        
        fakeRepo.savedState = FocusSessionState(FocusState.FOCUS_ACTIVE, libTag)
        fakeCoordinator.setStatus(EnforcementStatus.ENFORCEMENT_ACTIVE)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onTagEvent(rawToTag(libTag))
        advanceUntilIdle()

        assertTrue(fakeCoordinator.stopCalled)
    }

    @Test
    fun testCaseC_IGNORE_different_tag() = runTest {
        val libTag = "1D:FF:7C:1C:1A:10:80"
        val otherTag = "1D:5B:70:1C:1A:10:80"
        NfcProtocol.setRegisteredTags(setOf(libTag, otherTag))
        
        fakeRepo.savedState = FocusSessionState(FocusState.FOCUS_ACTIVE, libTag)
        fakeCoordinator.setStatus(EnforcementStatus.ENFORCEMENT_ACTIVE)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onTagEvent(rawToTag(otherTag))
        advanceUntilIdle()

        assertTrue(!fakeCoordinator.stopCalled)
        assertTrue(!fakeCoordinator.startCalled)
        assertEquals(FocusState.FOCUS_ACTIVE, vm.focusState.value.focusState)
    }

    @Test
    fun testCaseD_UNKNOWNNORMALignore() = runTest {
        val unknownTag = "AA:BB:CC:DD:EE:FF:00"
        val vm = createViewModel()
        advanceUntilIdle()
        
        vm.onTagEvent(unknownTag)
        advanceUntilIdle()

        assertTrue(!fakeCoordinator.startCalled)
    }

    @Test
    fun testRegistry_FreshCacheApplied() = runTest {
        val tags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()
        
        createViewModel()
        advanceUntilIdle()
        
        assertTrue(NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_ExpiredCacheNotApplied() = runTest {
        val tags = setOf("1D:FF:7C:1C:1A:10:80")
        val expiredTime = System.currentTimeMillis() - (49 * 60 * 60 * 1000L)
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, emptyMap(), "inst1", expiredTime)
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()
        
        // Prevent refresh from restoring them
        fakeNfcRepo.tagMap = emptyMap()

        createViewModel()
        advanceUntilIdle()
        
        assertTrue("Expired cache should not be applied", !NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_InstitutionMismatchNotApplied() = runTest {
        val tags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst2").apply()
        
        fakeNfcRepo.tagMap = emptyMap()

        createViewModel()
        advanceUntilIdle()
        
        assertTrue("Mismatched institution cache should not be applied", !NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_NullInstitutionClears() = runTest {
        // Start with registered tags
        NfcProtocol.setRegisteredTags(setOf("1D:FF:7C:1C:1A:10:80"))
        
        // Setup repo with NO institution
        val nullInstRepo = FakeNfcRepository()
        nullInstRepo.profile = Profile("user1", "User 1", "student", null)
        nullInstRepo.currentCache = null // Ensure no valid cache
        
        // Force refresh to be old enough
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().remove("institution_id").apply()
        
        createViewModel(nfcRepo = nullInstRepo)
        advanceUntilIdle()
        
        assertTrue("Registry should be empty for null institution", !NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_FailedRefreshKeepsValidCache() = runTest {
        val tags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.tags = tags
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
        
        fakeNfcRepo.shouldFailProfile = true
        vm.refreshRegistry(force = true)
        advanceUntilIdle()
        
        assertTrue(NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_SuccessfulRefreshReplaces() = runTest {
        val initialTags = setOf("1D:FF:7C:1C:1A:10:80")
        val initialMap = mapOf("1D:FF:7C:1C:1A:10:80" to "Library 1")
        val newTags = setOf("1D:5B:70:1C:1A:10:80")
        val newMap = mapOf("1D:5B:70:1C:1A:10:80" to "Classroom 1")
        
        // Cache has old tags and names
        fakeNfcRepo.currentCache = NfcRegistryCache(initialTags, initialMap, "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()
        
        // Supabase has OLD state initially
        fakeNfcRepo.tags = initialTags
        fakeNfcRepo.tagMap = initialMap
        
        val vm = createViewModel()
        advanceUntilIdle() // Ensure init refresh completes
        
        // Should have old tags
        assertTrue(NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
        assertEquals("Library 1", fakeNfcRepo.getCache()?.tagDisplayNames?.get("1D:FF:7C:1C:1A:10:80"))
        
        // Now update Supabase and force refresh
        fakeNfcRepo.tags = newTags
        fakeNfcRepo.tagMap = newMap
        
        vm.refreshRegistry(force = true)
        advanceUntilIdle()
        
        // Should have new tags and names now
        assertTrue("New tag not registered after refresh", NfcProtocol.isRegistered("1D:5B:70:1C:1A:10:80"))
        assertTrue("Deactivated tag still registered", !NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
        assertEquals("Classroom 1", fakeNfcRepo.getCache()?.tagDisplayNames?.get("1D:5B:70:1C:1A:10:80"))
        assertEquals(null, fakeNfcRepo.getCache()?.tagDisplayNames?.get("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_ProcessRestartBypassesThrottle() = runTest {
        val tags = setOf("1D:FF:7C:1C:1A:10:80")
        // Cache is fresh (1 min ago)
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, emptyMap(), "inst1", System.currentTimeMillis() - 60000)
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        // DB is updated (deactivated)
        fakeNfcRepo.tags = emptySet()
        fakeNfcRepo.tagMap = emptyMap()

        // Construct VM (Simulating Scenario B - Restart)
        createViewModel()
        // lastRefreshTime should be 0, so init call to refreshRegistry should NOT throttle
        advanceUntilIdle()
        
        // Registry should be empty now
        assertTrue("Registry should have been refreshed on restart despite fresh cache", !NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_RefreshThrottled() = runTest {
        val initialTags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.tags = initialTags
        fakeNfcRepo.currentCache = NfcRegistryCache(initialTags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        val vm = createViewModel()
        advanceUntilIdle()
        
        // Successfully fetched once
        val countAfterInit = fakeNfcRepo.fetchProfileCount
        
        // Change tags but don't force or advance time
        fakeNfcRepo.tags = setOf("1D:5B:70:1C:1A:10:80")
        vm.refreshRegistry()
        advanceUntilIdle()
        
        // Should be throttled
        assertEquals(countAfterInit, fakeNfcRepo.fetchProfileCount)
        assertTrue(NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_EmptyRegistryBypassesThrottle() = runTest {
        // Initial setup with matching cache so it loads something
        val tags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.tags = tags
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        val vm = createViewModel()
        advanceUntilIdle()
        
        // Explicitly clear registry (simulating logout/unauth transition)
        NfcProtocol.setRegisteredTags(emptySet())
        assertTrue(NfcProtocol.isRegistryEmpty())
        
        val countAfterInit = fakeNfcRepo.fetchProfileCount
        
        // Try refresh again immediately - should bypass throttle because registry is empty
        vm.refreshRegistry()
        advanceUntilIdle()
        
        assertEquals("Fetch should have occurred due to empty registry", countAfterInit + 1, fakeNfcRepo.fetchProfileCount)
        assertTrue("Registry should be repopulated", NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_Deactivation_NonEmpty_Throttled() = runTest {
        val initialTags = setOf("1D:FF:7C:1C:1A:10:80", "1D:5B:70:1C:1A:10:80")
        fakeNfcRepo.tags = initialTags
        fakeNfcRepo.currentCache = NfcRegistryCache(initialTags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        val vm = createViewModel()
        advanceUntilIdle()
        
        // Supabase deactivates one
        fakeNfcRepo.tags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.tagMap = mapOf("1D:FF:7C:1C:1A:10:80" to "Library 1")
        
        // Normal refresh (force=false) within 5 mins
        vm.refreshRegistry(force = false)
        advanceUntilIdle()
        
        // Should STILL have both tags due to throttle
        assertTrue("Tag should NOT have been removed due to throttle", NfcProtocol.isRegistered("1D:5B:70:1C:1A:10:80"))
    }

    @Test
    fun testRegistry_AccountSwitchingIsolatesCache() = runTest {
        // User A setup
        val userATags = setOf("1D:FF:7C:1C:1A:10:80")
        fakeNfcRepo.tags = userATags
        fakeNfcRepo.currentCache = NfcRegistryCache(userATags, emptyMap(), "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()
        
        createViewModel(userId = "user1")
        advanceUntilIdle()
        assertTrue(NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
        
        // User B setup - NO Reset of NfcProtocol singleton
        val userBRepo = FakeNfcRepository(uId = "user2")
        userBRepo.currentCache = null // No cache for B
        userBRepo.profile = Profile("user2", "User 2", "student", null) // No inst for B
        userBRepo.tags = emptySet()
        
        createViewModel(userId = "user2", nfcRepo = userBRepo)
        advanceUntilIdle()
        
        // User A's tag must BE GONE now that B initialized
        assertTrue("User A's registry leaked into User B", !NfcProtocol.isRegistered("1D:FF:7C:1C:1A:10:80"))
    }

    @Test
    fun testDisplayNames_Resolution() = runTest {
        val tags = setOf("1D:FF:7C:1C:1A:10:80", "1D:C5:7C:1C:1A:10:80")
        val tagMap = mapOf(
            "1D:FF:7C:1C:1A:10:80" to "Library 1",
            "1D:C5:7C:1C:1A:10:80" to "Classroom 3"
        )
        fakeNfcRepo.tagMap = tagMap
        fakeNfcRepo.currentCache = NfcRegistryCache(tags, tagMap, "inst1", System.currentTimeMillis())
        fakeContext.getSharedPreferences("verified_profile_user1", 0).edit().putString("institution_id", "inst1").apply()

        val vm = createViewModel()
        advanceUntilIdle()
        
        val cache = fakeNfcRepo.getCache()!!
        assertEquals("Library 1", cache.tagDisplayNames["1D:FF:7C:1C:1A:10:80"])
        assertEquals("Classroom 3", cache.tagDisplayNames["1D:C5:7C:1C:1A:10:80"])
        assertEquals(null, cache.tagDisplayNames["AA:BB:CC"])
    }
    
    private fun rawToTag(uid: String) = uid.replace(":", "")
}
