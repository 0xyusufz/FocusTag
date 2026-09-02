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
import android.util.Log

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: FocusViewModel
    private lateinit var fakeRepo: FakeFocusRepository
    private lateinit var fakeCoordinator: FakeEnforcementCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Initialize fakeRepo only if not already done, to preserve state across setup() calls in one test
        if (!::fakeRepo.isInitialized) {
            fakeRepo = FakeFocusRepository()
        }
        
        // Manual fakes for coordinator dependencies to avoid crashes in super constructor
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

        // SYNC: Ensure coordinator matches repo before ViewModel.init
        if (fakeRepo.savedState.focusState == FocusState.FOCUS_ACTIVE) {
            fakeCoordinator.setStatus(EnforcementStatus.ENFORCEMENT_ACTIVE)
        } else {
            fakeCoordinator.setStatus(EnforcementStatus.IDLE)
        }
        
        viewModel = object : FocusViewModel(
            context = null as android.content.Context?, 
            focusRepository = fakeRepo,
            enforcementCoordinator = fakeCoordinator
        ) {
            override fun refreshAccessibilityCapability() {
                _accessibilityCapability.update { AccessibilityCapability.ACCESSIBILITY_READY }
            }
            override fun refreshNfcCapability() {
                _nfcCapability.update { NfcCapability.NFC_READY }
            }
            override fun refreshEnforcementStatus() {}
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Fakes ---

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

    // --- Test Cases A-J ---

    @Test
    fun `Case A - START success`() = runTest {
        val libTag = "1D:FF:7C:1C:1A:10:80"
        
        viewModel.onTagEvent(libTag)
        advanceUntilIdle()

        assertTrue(fakeCoordinator.startCalled)
        assertEquals(libTag, fakeCoordinator.startTagId)
        assertEquals(FocusState.FOCUS_ACTIVE, viewModel.focusState.value.focusState)
        assertEquals(libTag, viewModel.focusState.value.activeTagId)
        assertEquals(libTag, fakeRepo.savedState.activeTagId)
    }

    @Test
    fun `Case B - STOP success`() = runTest {
        val libTag = "1D:FF:7C:1C:1A:10:80"
        fakeRepo.savedState = FocusSessionState(FocusState.FOCUS_ACTIVE, libTag)
        
        // Re-init to pick up state
        setup()

        viewModel.onTagEvent(libTag)
        advanceUntilIdle()

        assertTrue(fakeCoordinator.stopCalled)
        assertEquals(FocusState.NORMAL, viewModel.focusState.value.focusState)
        assertEquals(null, viewModel.focusState.value.activeTagId)
        assertEquals(null, fakeRepo.savedState.activeTagId)
    }

    @Test
    fun `Case C - DIFFERENT registered tag while ACTIVE results in IGNORE`() = runTest {
        val libTag = "1D:FF:7C:1C:1A:10:80"
        val class1Tag = "1D:5B:70:1C:1A:10:80"
        fakeRepo.savedState = FocusSessionState(FocusState.FOCUS_ACTIVE, libTag)
        
        setup()

        viewModel.onTagEvent(class1Tag)
        advanceUntilIdle()

        assertTrue(!fakeCoordinator.stopCalled)
        assertTrue(!fakeCoordinator.startCalled)
        assertEquals(FocusState.FOCUS_ACTIVE, viewModel.focusState.value.focusState)
        assertEquals(libTag, viewModel.focusState.value.activeTagId)
        assertEquals(libTag, fakeRepo.savedState.activeTagId)
    }

    @Test
    fun `Case D - UNKNOWN NORMAL ignore`() = runTest {
        val unknownTag = "AA:BB:CC:DD:EE:FF:00"
        viewModel.onTagEvent(unknownTag)
        advanceUntilIdle()

        assertTrue(!fakeCoordinator.startCalled)
        assertEquals(FocusState.NORMAL, viewModel.focusState.value.focusState)
    }
}
