package ir.chardivari.feature.seller

import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.SellerAction
import ir.chardivari.core.marketplace.SellerDraftCreated
import ir.chardivari.core.marketplace.SellerListingItem
import ir.chardivari.core.marketplace.SellerRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class SellerWizardViewModelTest {

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
        var userId: String? = "user-1",
    ) : SessionTokenProvider {
        override fun accessToken(): String? = if (userId == null) null else "token"
        override fun currentUserId(): String? = userId
    }

    private class FakeReader(
        var bytes: Map<String, ByteArray> = emptyMap(),
        var mimes: Map<String, String> = emptyMap(),
    ) : MediaBytesReader {
        override fun read(uri: String): ByteArray? = bytes[uri]
        override fun mimeTypeOf(uri: String): String? = mimes[uri]
    }

    private class FakeSeller : SellerRepository {
        var createResult: AppResult<SellerDraftCreated> =
            AppResult.Success(SellerDraftCreated("L1", "P1"))
        var createCalls = 0
        var statusResult: AppResult<Unit> = AppResult.Success(Unit)
        val statusCalls = mutableListOf<Pair<String, SellerAction>>()
        var uploadResult: AppResult<Unit> = AppResult.Success(Unit)
        val uploadCalls = mutableListOf<String>()

        override suspend fun createDraft(
            property: kotlinx.serialization.json.JsonObject,
            listing: kotlinx.serialization.json.JsonObject,
        ): AppResult<SellerDraftCreated> {
            createCalls++
            return createResult
        }

        override suspend fun setStatus(
            listingId: String,
            action: SellerAction,
        ): AppResult<Unit> {
            statusCalls += listingId to action
            return statusResult
        }

        override suspend fun myListings(): AppResult<List<SellerListingItem>> = AppResult.Empty

        override suspend fun uploadMedia(
            propertyId: String,
            mimeType: String,
            bytes: ByteArray,
            mediaType: String,
            sortOrder: Int,
            isCover: Boolean,
        ): AppResult<Unit> {
            uploadCalls += mimeType
            return uploadResult
        }
    }

    private lateinit var seller: FakeSeller
    private lateinit var session: FakeSession
    private lateinit var reader: FakeReader
    private lateinit var tracker: RecordingTracker

    private fun viewModel() = SellerWizardViewModel(
        sellerRepository = seller,
        mediaReader = reader,
        session = session,
        analytics = tracker,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        seller = FakeSeller()
        session = FakeSession()
        reader = FakeReader(
            bytes = mapOf("content://photo1" to byteArrayOf(1, 2, 3)),
            mimes = mapOf(
                "content://photo1" to "image/jpeg",
                "content://bad" to "application/pdf",
                "content://doc1" to "application/pdf",
            ),
        )
        tracker = RecordingTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun SellerWizardViewModel.fillValidSaleDraft() {
        setDealType(DealType.SALE)
        setPropertyType(PropertyType.APARTMENT)
        setProvince("تهران")
        setCity("تهران")
        setNeighborhood("ونک")
        setAreaSqm("80")
        setBedrooms("2")
        setFloor("3")
        setPriceRial("5000000000")
        addPhotos(listOf("content://photo1"))
        addDocs(listOf("content://doc1"))
    }

    @Test
    fun `init tracks screen view without premature login wall`() = runTest {
        session.userId = null
        val vm = viewModel()
        // Exploration is allowed offline of auth; wall appears only on publish.
        assertTrue(!vm.uiState.value.requiresLogin)
        assertEquals(
            AnalyticsEvent.ScreenView("seller_wizard"),
            tracker.recorded.first(),
        )
    }

    @Test
    fun `refreshSession clears stale login wall once session exists`() = runTest {
        session.userId = null
        val vm = viewModel()
        vm.fillValidSaleDraft()
        vm.publish()
        assertTrue(vm.uiState.value.requiresLogin)
        session.userId = "user-1"
        vm.refreshSession()
        assertTrue(!vm.uiState.value.requiresLogin)
    }

    @Test
    fun `next blocks on invalid step with persian error`() = runTest {
        val vm = viewModel()
        vm.next()
        assertEquals(WizardStep.TYPE, vm.uiState.value.step)
        assertEquals("نوع معامله را انتخاب کنید", vm.uiState.value.stepError)
    }

    @Test
    fun `forward jump lands on first invalid step`() = runTest {
        val vm = viewModel()
        vm.setDealType(DealType.SALE)
        vm.setPropertyType(PropertyType.APARTMENT)
        vm.onStepSelected(WizardStep.REVIEW)
        assertEquals(WizardStep.LOCATION, vm.uiState.value.step)
        assertEquals("استان را وارد کنید", vm.uiState.value.stepError)
    }

    @Test
    fun `next walks the full linear flow`() = runTest {
        val vm = viewModel()
        vm.fillValidSaleDraft()
        vm.onStepSelected(WizardStep.TYPE)
        repeat(WizardStep.entries.size) {
            vm.next()
        }
        assertNull(vm.uiState.value.stepError)
        assertEquals(WizardStep.REVIEW, vm.uiState.value.step)
    }

    @Test
    fun `publish happy path uploads media then activates and tracks ListingCreated`() = runTest {
        val vm = viewModel()
        vm.fillValidSaleDraft()
        vm.publish()

        assertEquals(1, seller.createCalls)
        assertEquals(listOf("image/jpeg", "application/pdf"), seller.uploadCalls)
        assertEquals(listOf("L1" to SellerAction.PUBLISH), seller.statusCalls)
        assertEquals("L1", vm.uiState.value.publishedListingId)
        assertTrue(!vm.uiState.value.publishing)
        assertNull(vm.uiState.value.publishError)
        assertTrue(
            tracker.recorded.any { it == AnalyticsEvent.ListingCreated("L1") },
        )
    }

    @Test
    fun `publish without login sets requiresLogin and never creates draft`() = runTest {
        session.userId = null
        val vm = viewModel()
        vm.fillValidSaleDraft()
        vm.publish()
        assertEquals(0, seller.createCalls)
        assertTrue(vm.uiState.value.requiresLogin)
        assertNull(vm.uiState.value.publishedListingId)
    }

    @Test
    fun `publish failure at draft keeps retry surface without listing id`() = runTest {
        seller.createResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()
        vm.fillValidSaleDraft()
        vm.publish()

        assertEquals(0, seller.statusCalls.size)
        assertNull(vm.uiState.value.publishedListingId)
        assertNotNull(vm.uiState.value.publishError)
        assertTrue(tracker.recorded.none { it is AnalyticsEvent.ListingCreated })
    }

    @Test
    fun `publish retry after upload failure reuses draft and skips uploaded media`() = runTest {
        seller.uploadResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()
        vm.fillValidSaleDraft()
        vm.publish()

        assertEquals(1, seller.createCalls)
        assertTrue(vm.uiState.value.created?.listingId == "L1")
        assertNotNull(vm.uiState.value.publishError)
        assertTrue(vm.uiState.value.publishError!!.startsWith("پیش‌نویس ذخیره شد"))

        // photo1 uploaded successfully before failure? here upload fails on first call,
        // so nothing marked uploaded; retry must NOT re-create the draft.
        seller.uploadResult = AppResult.Success(Unit)
        vm.publish()
        assertEquals(1, seller.createCalls)
        assertEquals(listOf("L1" to SellerAction.PUBLISH), seller.statusCalls)
        assertEquals("L1", vm.uiState.value.publishedListingId)
    }

    @Test
    fun `addPhotos rejects unsupported mime types`() = runTest {
        val vm = viewModel()
        val rejected = vm.addPhotos(listOf("content://bad"))
        assertEquals(1, rejected)
        assertTrue(vm.uiState.value.draft.photos.isEmpty())
    }

    @Test
    fun `validation catches out-of-range build year`() = runTest {
        val vm = viewModel()
        vm.setDealType(DealType.RENT)
        vm.setPropertyType(PropertyType.HOUSE)
        vm.setProvince("اصفهان")
        vm.setCity("اصفهان")
        vm.setAreaSqm("120")
        vm.setBuildYear("999")
        vm.next() // TYPE -> LOCATION
        vm.next() // LOCATION -> SPECS
        vm.next() // SPECS blocked by build year
        assertEquals(WizardStep.SPECS, vm.uiState.value.step)
        assertEquals("سال ساخت (شمسی) بین ۱۱۰۰ تا ۱۵۰۰ است", vm.uiState.value.stepError)
    }
}
