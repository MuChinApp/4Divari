package ir.chardivari.feature.seller

import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.AgentRepository
import ir.chardivari.core.marketplace.SellerAction
import ir.chardivari.core.marketplace.SellerDraftCreated
import ir.chardivari.core.marketplace.SellerListingItem
import ir.chardivari.core.marketplace.SellerRepository
import ir.chardivari.core.marketplace.StorageBaseUrl
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

private fun item(id: String, status: String) = SellerListingItem(
    id = id,
    status = status,
    dealType = DealType.SALE,
    priceRial = 5_000_000_000L,
    depositRial = null,
    rentRial = null,
    publishedAt = null,
    city = "تهران",
    areaSqm = 80,
    propertyType = null,
    coverPath = null,
)

private class FakeAgent(
    var assignResult: AppResult<String?> = AppResult.Success("agent-1"),
) : AgentRepository {
    val assignCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun isAgent(): AppResult<Boolean> = AppResult.Success(true)

    override suspend fun dashboard(): AppResult<ir.chardivari.core.marketplace.AgentDashboard> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun myFiles(): AppResult<List<SellerListingItem>> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun leads(): AppResult<List<ir.chardivari.core.marketplace.AgentLead>> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun leadMatches(
        leadId: String,
    ): AppResult<List<ir.chardivari.core.marketplace.LeadMatch>> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun requirementForLead(
        leadId: String,
    ): AppResult<ir.chardivari.core.marketplace.BuyerRequirement> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun upsertRequirement(
        input: ir.chardivari.core.marketplace.RequirementInput,
    ): AppResult<Unit> = AppResult.Failure(AppError.Unexpected)

    override suspend fun refreshMatches(leadId: String): AppResult<Int> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun updateLead(
        leadId: String,
        stage: String?,
        priority: Int?,
        notes: String?,
    ): AppResult<Unit> = AppResult.Failure(AppError.Unexpected)

    override suspend fun visits(): AppResult<List<ir.chardivari.core.marketplace.AgentVisit>> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun setVisitStatus(visitId: String, status: String): AppResult<Unit> =
        AppResult.Failure(AppError.Unexpected)

    override suspend fun assignAgent(
        listingId: String,
        agentPhone: String?,
    ): AppResult<String?> {
        assignCalls += listingId to agentPhone
        return assignResult
    }

    override suspend fun profile(): AppResult<ir.chardivari.core.marketplace.AgentProfile> =
        AppResult.Failure(AppError.Unexpected)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SellerManageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow
        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private class FakeSeller(
        var listResult: AppResult<List<SellerListingItem>> = AppResult.Success(
            listOf(item("L1", "ACTIVE")),
        ),
        var statusResult: AppResult<Unit> = AppResult.Success(Unit),
    ) : SellerRepository {
        var listCalls = 0
        val statusCalls = mutableListOf<Pair<String, SellerAction>>()

        override suspend fun createDraft(
            property: kotlinx.serialization.json.JsonObject,
            listing: kotlinx.serialization.json.JsonObject,
        ): AppResult<SellerDraftCreated> = AppResult.Failure(AppError.Unexpected)

        override suspend fun setStatus(
            listingId: String,
            action: SellerAction,
        ): AppResult<Unit> {
            statusCalls += listingId to action
            return statusResult
        }

        override suspend fun myListings(): AppResult<List<SellerListingItem>> {
            listCalls++
            return listResult
        }

        override suspend fun uploadMedia(
            propertyId: String,
            mimeType: String,
            bytes: ByteArray,
            mediaType: String,
            sortOrder: Int,
            isCover: Boolean,
        ): AppResult<Unit> = AppResult.Failure(AppError.Unexpected)
    }

    private lateinit var seller: FakeSeller
    private lateinit var agent: FakeAgent
    private lateinit var tracker: RecordingTracker

    private fun viewModel() = SellerManageViewModel(
        sellerRepository = seller,
        agentRepository = agent,
        storage = StorageBaseUrl(null),
        analytics = tracker,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        seller = FakeSeller()
        agent = FakeAgent()
        tracker = RecordingTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load success renders content with items`() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertEquals(listOf("L1"), (state as UiState.Content).data.items.map { it.id })
        assertEquals(
            AnalyticsEvent.ScreenView("seller_manage"),
            tracker.recorded.first(),
        )
    }

    @Test
    fun `load empty renders content with empty list`() = runTest {
        seller.listResult = AppResult.Empty
        val vm = viewModel()
        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue((state as UiState.Content).data.items.isEmpty())
        assertTrue(!state.data.requiresLogin)
    }

    @Test
    fun `load unauthorized shows login gate`() = runTest {
        seller.listResult = AppResult.Failure(AppError.Unauthorized)
        val vm = viewModel()
        val state = vm.uiState.value as UiState.Content
        assertTrue(state.data.requiresLogin)
        assertTrue(state.data.items.isEmpty())
    }

    @Test
    fun `load failure surfaces error state with retry`() = runTest {
        seller.listResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()
        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals(AppError.Server, (state as UiState.Error).error)
        assertTrue(state.canRetry)
    }

    @Test
    fun `pause action updates row only after server success`() = runTest {
        val vm = viewModel()
        vm.performAction("L1", SellerAction.PAUSE)
        assertEquals(listOf("L1" to SellerAction.PAUSE), seller.statusCalls)
        val state = vm.uiState.value as UiState.Content
        assertEquals("PAUSED", state.data.items.first().status)
        assertEquals("وضعیت آگهی به‌روزرسانی شد", state.data.message)
        assertTrue(state.data.busyIds.isEmpty())
    }

    @Test
    fun `failed action keeps original status and explains`() = runTest {
        seller.statusResult = AppResult.Failure(AppError.Client(400, "illegal transition"))
        val vm = viewModel()
        vm.performAction("L1", SellerAction.MARK_SOLD)
        val state = vm.uiState.value as UiState.Content
        assertEquals("ACTIVE", state.data.items.first().status)
        assertTrue(state.data.message != null && state.data.message!!.startsWith("تغییر وضعیت انجام نشد"))
        assertTrue(state.data.busyIds.isEmpty())
    }

    @Test
    fun `clearMessage wipes snackbar text`() = runTest {
        val vm = viewModel()
        vm.performAction("L1", SellerAction.PAUSE)
        vm.clearMessage()
        val state = vm.uiState.value as UiState.Content
        assertNull(state.data.message)
    }

    @Test
    fun `assign success closes dialog and confirms`() = runTest {
        val vm = viewModel()
        vm.load()
        vm.openAssign("L1")
        vm.setAssignPhone("09121234567")
        vm.submitAssign()

        val state = (vm.uiState.value as UiState.Content).data
        assertNull(state.assign)
        assertEquals("فایل به مشاور واگذار شد", state.message)
        assertEquals(listOf("L1" to "09121234567"), agent.assignCalls)
    }

    @Test
    fun `assign blank phone sends null to unassign`() = runTest {
        val vm = viewModel()
        vm.load()
        vm.openAssign("L1")
        vm.submitAssign()

        assertEquals(listOf("L1" to null), agent.assignCalls)
        val state = (vm.uiState.value as UiState.Content).data
        assertEquals("واگذاری مشاور برداشته شد", state.message)
    }

    @Test
    fun `assign failure keeps dialog with error`() = runTest {
        agent.assignResult = AppResult.Failure(
            AppError.Client(code = 400, serverMessage = "no active agent with this phone"),
        )
        val vm = viewModel()
        vm.load()
        vm.openAssign("L1")
        vm.setAssignPhone("09120000000")
        vm.submitAssign()

        val state = (vm.uiState.value as UiState.Content).data
        assertNotNull(state.assign)
        assertTrue(state.assign?.error?.contains("مشاور فعال") == true)
        assertNull(state.message)
    }

    @Test
    fun `assign short phone is rejected client-side`() = runTest {
        val vm = viewModel()
        vm.load()
        vm.openAssign("L1")
        vm.setAssignPhone("123")
        vm.submitAssign()

        assertEquals(emptyList<Pair<String, String?>>(), agent.assignCalls)
        val state = (vm.uiState.value as UiState.Content).data
        assertTrue(state.assign?.error != null)
    }
}
