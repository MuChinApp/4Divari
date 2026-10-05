package ir.chardivari.feature.home

import app.cash.turbine.test
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.PropertyDto
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.SearchFilters
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow

        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private fun sampleListing(id: String) = Listing(
        id = id,
        propertyId = "p-$id",
        dealType = DealType.SALE,
        priceRial = 12_800_000_000L,
        depositRial = null,
        rentRial = null,
        status = "ACTIVE",
        publishedAt = "2026-09-01T10:00:00+00:00",
        verificationStatus = "verified",
        freshness = "fresh",
        dataSource = "REAL",
        property = PropertyDto(
            id = "p-$id",
            propertyType = PropertyType.APARTMENT,
            areaSqm = 90,
            bedrooms = 2,
            city = "تهران",
            province = "تهران",
            neighborhood = "پونک",
        ),
    )

    private class FakeListings(
        private val result: AppResult<List<Listing>>,
    ) : ListingRepository {
        var feedCalls = 0
            private set

        override suspend fun feed(limit: Int): AppResult<List<Listing>> {
            feedCalls++
            return result
        }

        override suspend fun search(
            filters: SearchFilters,
            limit: Int,
        ): AppResult<List<Listing>> = result

        override suspend fun byId(listingId: String): AppResult<Listing> =
            AppResult.Failure(ir.chardivari.core.common.AppError.Unexpected)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadSuccess_emitsContentWithRealListings() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val repo = FakeListings(
            AppResult.Success(listOf(sampleListing("l1"), sampleListing("l2"))),
        )
        val vm = HomeViewModel(tracker, repo, StorageBaseUrl("https://example.supabase.co"))

        vm.uiState.test {
            val loaded = expectMostRecentItem()
            val model = (loaded as UiState.Content).data
            assertEquals(2, model.newListings.size)
            assertEquals("l1", model.newListings[0].listingId)
            assertTrue(model.sections.none { it.isImplemented })
            cancel()
        }
        assertTrue(tracker.recorded.any { it.name == "screen_view" })
        assertEquals(1, repo.feedCalls)
    }

    @Test
    fun loadEmpty_emitsEmptyNotFakeContent() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(
            tracker,
            FakeListings(AppResult.Empty),
            StorageBaseUrl(null),
        )

        vm.uiState.test {
            assertEquals(UiState.Empty, expectMostRecentItem())
            cancel()
        }
    }

    @Test
    fun loadFailure_propagatesUiError() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(
            tracker,
            FakeListings(AppResult.Failure(ir.chardivari.core.common.AppError.Offline)),
            StorageBaseUrl(null),
        )

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Error)
            cancel()
        }
    }

    @Test
    fun blankSearchDoesNotEmitAnalytics() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(
            tracker,
            FakeListings(AppResult.Success(emptyList())),
            StorageBaseUrl(null),
        )
        tracker.recorded.clear()

        vm.onSearchSubmitted("   ")
        assertFalse(tracker.recorded.any { it.name == "search_created" })
    }

    @Test
    fun nonBlankSearchEmitsSearchCreated() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(
            tracker,
            FakeListings(AppResult.Success(emptyList())),
            StorageBaseUrl(null),
        )
        tracker.recorded.clear()

        vm.onSearchSubmitted("آپارتمان دوخوابه")
        assertTrue(tracker.recorded.any { it.name == "search_created" })
    }
}
