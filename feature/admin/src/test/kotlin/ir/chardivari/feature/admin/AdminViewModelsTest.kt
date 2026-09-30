package ir.chardivari.feature.admin

import app.cash.turbine.test
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AdminRepository
import ir.chardivari.core.marketplace.ModerationQueue
import ir.chardivari.core.marketplace.QueueReport
import ir.chardivari.core.marketplace.QueueVerification
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun queueFixture() = ModerationQueue(
    openReports = 1,
    pendingVerifications = 1,
    reports = listOf(
        QueueReport(
            id = "r1",
            listingId = "l1",
            reason = "قیمت نامعتبر",
            detail = null,
            status = "open",
            createdAt = "2026-01-01T10:00:00+00:00",
            listing = null,
            risk = emptyList(),
        ),
    ),
    verifications = listOf(
        QueueVerification(
            listingId = "l2",
            verificationStatus = "pending",
            publishedAt = null,
            listing = null,
            risk = emptyList(),
        ),
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow
        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private class FakeSession(
        var uid: String? = "admin-1",
    ) : SessionTokenProvider {
        override fun accessToken(): String? = "jwt-token"
        override fun currentUserId(): String? = uid
    }

    private class FakeAdmin(
        var adminResult: AppResult<Boolean> = AppResult.Success(true),
        var queueResult: AppResult<ModerationQueue> = AppResult.Success(queueFixture()),
        var resolveResult: AppResult<Unit> = AppResult.Success(Unit),
        var decideResult: AppResult<String> = AppResult.Success("verified"),
    ) : AdminRepository {
        var resolveCalls = mutableListOf<Pair<String, String>>()
        var decideCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAdmin(): AppResult<Boolean> = adminResult
        override suspend fun queue(): AppResult<ModerationQueue> = queueResult
        override suspend fun resolveReport(
            reportId: String,
            status: String,
        ): AppResult<Unit> {
            resolveCalls += reportId to status
            return resolveResult
        }
        override suspend fun decideVerification(
            listingId: String,
            status: String,
        ): AppResult<String> {
            decideCalls += listingId to status
            return decideResult
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- gate ----

    @Test
    fun gate_notLoggedIn_isHonestWall() = runTest(dispatcher.scheduler) {
        val vm = AdminGateViewModel(FakeSession(uid = null), FakeAdmin(), RecordingTracker())
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Content)
            assertEquals(AdminGate.NotLoggedIn, (state as UiState.Content).data)
            cancel()
        }
    }

    @Test
    fun gate_withoutAdminRole_blocks() = runTest(dispatcher.scheduler) {
        val vm = AdminGateViewModel(
            FakeSession(),
            FakeAdmin(adminResult = AppResult.Success(false)),
            RecordingTracker(),
        )
        val deadline = System.currentTimeMillis() + 2000
        while (
            (vm.uiState.value as? UiState.Content)?.data != AdminGate.NotAdmin &&
            System.currentTimeMillis() < deadline
        ) {
            kotlinx.coroutines.delay(10)
        }
        val state = vm.uiState.value as UiState.Content
        assertEquals(AdminGate.NotAdmin, state.data)
    }

    @Test
    fun gate_admin_ready() = runTest(dispatcher.scheduler) {
        val vm = AdminGateViewModel(FakeSession(), FakeAdmin(), RecordingTracker())
        val deadline = System.currentTimeMillis() + 2000
        while (
            (vm.uiState.value as? UiState.Content)?.data != AdminGate.Ready &&
            System.currentTimeMillis() < deadline
        ) {
            kotlinx.coroutines.delay(10)
        }
        val state = vm.uiState.value as UiState.Content
        assertEquals(AdminGate.Ready, state.data)
    }

    // ---- queue actions ----

    @Test
    fun queue_loadsContent() = runTest(dispatcher.scheduler) {
        val vm = AdminViewModel(FakeAdmin(), RecordingTracker())
        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        val content = vm.uiState.value as UiState.Content
        assertEquals(1, content.data.queue?.reports?.size)
        assertEquals(1, content.data.queue?.verifications?.size)
    }

    @Test
    fun resolveReport_success_removesRowAndTracks() = runTest(dispatcher.scheduler) {
        val admin = FakeAdmin()
        val tracker = RecordingTracker()
        val vm = AdminViewModel(admin, tracker)

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }

        vm.resolveReport("r1", "actioned")
        val join = System.currentTimeMillis() + 2000
        while (admin.resolveCalls.isEmpty() && System.currentTimeMillis() < join) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals(listOf("r1" to "actioned"), admin.resolveCalls)

        val content = vm.uiState.value as UiState.Content
        assertTrue(content.data.queue?.reports?.isEmpty() ?: false)
        assertEquals(0, content.data.queue?.openReports)
        assertNotNull(content.data.message)
        assertTrue(content.data.busyIds.isEmpty())
        assertTrue(tracker.recorded.any { it.name == "moderation_action" })
    }

    @Test
    fun resolveReport_failure_keepsRow() = runTest(dispatcher.scheduler) {
        val admin = FakeAdmin(resolveResult = AppResult.Failure(AppError.Server))
        val vm = AdminViewModel(admin, RecordingTracker())

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }

        vm.resolveReport("r1", "dismissed")
        val join = System.currentTimeMillis() + 2000
        while (admin.resolveCalls.isEmpty() && System.currentTimeMillis() < join) {
            kotlinx.coroutines.delay(10)
        }
        val content = vm.uiState.value as UiState.Content
        assertEquals(1, content.data.queue?.reports?.size)
        assertNotNull(content.data.message)
        assertTrue(content.data.busyIds.isEmpty())
    }

    @Test
    fun decideVerification_success_removesRowAndTracks() = runTest(dispatcher.scheduler) {
        val admin = FakeAdmin()
        val tracker = RecordingTracker()
        val vm = AdminViewModel(admin, tracker)

        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }

        vm.decideVerification("l2", "verified")
        val join = System.currentTimeMillis() + 2000
        while (admin.decideCalls.isEmpty() && System.currentTimeMillis() < join) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals(listOf("l2" to "verified"), admin.decideCalls)

        val content = vm.uiState.value as UiState.Content
        assertTrue(content.data.queue?.verifications?.isEmpty() ?: false)
        assertEquals(0, content.data.queue?.pendingVerifications)
        assertTrue(tracker.recorded.any { it.name == "moderation_action" })
    }

    @Test
    fun queue_failure_emitsErrorState() = runTest(dispatcher.scheduler) {
        val vm = AdminViewModel(
            FakeAdmin(queueResult = AppResult.Failure(AppError.Server)),
            RecordingTracker(),
        )
        val deadline = System.currentTimeMillis() + 2000
        while (vm.uiState.value !is UiState.Error && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        assertTrue(vm.uiState.value is UiState.Error)
        assertNull((vm.uiState.value as? UiState.Content)?.data)
    }
}
