package ir.chardivari.feature.search

import app.cash.turbine.test
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.PropertyDto
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.SavedSearchItem
import ir.chardivari.core.marketplace.SavedSearchRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow
        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private fun listing(id: String) = Listing(
        id = id,
        propertyId = "p-$id",
        dealType = DealType.SALE,
        priceRial = 1_000_000_000L,
        depositRial = null,
        rentRial = null,
        status = "ACTIVE",
        publishedAt = null,
        verificationStatus = "unverified",
        freshness = "fresh",
        dataSource = "REAL",
        property = PropertyDto(
            id = "p-$id",
            propertyType = PropertyType.APARTMENT,
            areaSqm = 70,
            city = "تهران",
            province = "تهران",
        ),
    )

    private class FakeListings(
        var result: AppResult<List<Listing>> = AppResult.Success(listOf(listing("a"))),
    ) : ListingRepository {
        var lastFilters: SearchFilters? = null
            private set

        override suspend fun feed(limit: Int) = result

        override suspend fun search(filters: SearchFilters, limit: Int): AppResult<List<Listing>> {
            lastFilters = filters
            return result
        }

        override suspend fun byId(listingId: String): AppResult<Listing> = AppResult.Success(listing("a"))

        private companion object {
            fun listing(id: String) = Listing(
                id = id,
                propertyId = "p-$id",
                dealType = DealType.SALE,
                priceRial = 1L,
                depositRial = null,
                rentRial = null,
                status = "ACTIVE",
                publishedAt = null,
                verificationStatus = "unverified",
                freshness = "fresh",
                dataSource = "REAL",
                property = PropertyDto(
                    id = "p-$id",
                    propertyType = PropertyType.APARTMENT,
                    areaSqm = 1,
                    city = "x",
                    province = "y",
                ),
            )
        }
    }

    private class FakeSaved(
        var saveResult: AppResult<SavedSearchItem> = AppResult.Success(
            SavedSearchItem("s1", null, SearchFilters.EMPTY, null),
        ),
    ) : SavedSearchRepository {
        var savedName: String? = null
            private set

        override suspend fun list(): AppResult<List<SavedSearchItem>> = AppResult.Empty

        override suspend fun save(name: String?, filters: SearchFilters): AppResult<SavedSearchItem> {
            savedName = name
            return saveResult
        }

        override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
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
    fun initialLoad_fetchesFeedAndEmitsContent() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val repo = FakeListings()
        val vm = SearchViewModel(repo, FakeSaved(), StorageBaseUrl(null), tracker)

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Content)
            assertEquals(1, (state as UiState.Content).data.results.size)
            cancel()
        }
        assertTrue(tracker.recorded.any { it.name == "search_created" })
    }

    @Test
    fun emptyResult_stillContentWithEmptyList() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val repo = FakeListings(AppResult.Empty)
        val vm = SearchViewModel(repo, FakeSaved(), StorageBaseUrl(null), tracker)

        vm.uiState.test {
            val state = expectMostRecentItem() as UiState.Content
            assertTrue(state.data.results.isEmpty())
            cancel()
        }
    }

    @Test
    fun dealTypeFilter_tracksAnalyticsAndQueries() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val repo = FakeListings()
        val vm = SearchViewModel(repo, FakeSaved(), StorageBaseUrl(null), tracker)

        vm.onDealTypeSelected(DealType.RENT)
        assertEquals(DealType.RENT, repo.lastFilters?.dealType)
        assertTrue(tracker.recorded.any { it.name == "filter_used" })
    }

    @Test
    fun saveActiveSearch_emitsSearchSaved() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val repo = FakeListings()
        val saved = FakeSaved()
        val vm = SearchViewModel(repo, saved, StorageBaseUrl(null), tracker)

        vm.onCityChange("تهران")
        vm.saveCurrentSearch("خانه تهران")

        assertEquals("خانه تهران", saved.savedName)
        assertTrue(tracker.recorded.any { it.name == "search_saved" })
    }

    @Test
    fun unauthorizedSave_setsLoginMessage() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val saved = FakeSaved(AppResult.Failure(AppError.Unauthorized))
        val vm = SearchViewModel(FakeListings(), saved, StorageBaseUrl(null), tracker)

        vm.onCityChange("تهران")
        vm.saveCurrentSearch(null)

        val content = vm.uiState.value as UiState.Content
        assertTrue(content.data.saveMessage?.contains("وارد") == true)
    }
}
