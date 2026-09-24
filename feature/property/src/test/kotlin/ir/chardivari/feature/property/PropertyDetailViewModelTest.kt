package ir.chardivari.feature.property

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.FavoritesRepository
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingContact
import ir.chardivari.core.marketplace.ListingContactRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow
        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private val sample = Listing(
        id = "l1",
        propertyId = "p1",
        dealType = DealType.SALE,
        priceRial = 5_000_000_000L,
        depositRial = null,
        rentRial = null,
        status = "ACTIVE",
        publishedAt = "2026-09-01T00:00:00+00:00",
        verificationStatus = "verified",
        freshness = "fresh",
        dataSource = "REAL",
        property = PropertyDto(
            id = "p1",
            propertyType = PropertyType.APARTMENT,
            areaSqm = 100,
            bedrooms = 3,
            city = "تهران",
            province = "تهران",
            description = "توضیح آزمایشی",
        ),
    )

    private class FakeListings(
        var result: AppResult<Listing> = AppResult.Success(sample),
    ) : ListingRepository {
        override suspend fun feed(limit: Int) = AppResult.Empty
        override suspend fun search(filters: SearchFilters, limit: Int) = AppResult.Empty
        override suspend fun byId(listingId: String): AppResult<Listing> = result

        private companion object {
            val sample = Listing(
                id = "l1",
                propertyId = "p1",
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
                    id = "p1",
                    propertyType = PropertyType.APARTMENT,
                    areaSqm = 1,
                    city = "x",
                    province = "y",
                ),
            )
        }
    }

    private class FakeFavorites(
        var result: AppResult<Unit> = AppResult.Success(Unit),
    ) : FavoritesRepository {
        var last: Pair<String, Boolean>? = null
        override suspend fun favoriteIds(): AppResult<Set<String>> = AppResult.Success(emptySet())
        override suspend fun setFavorite(listingId: String, favorite: Boolean): AppResult<Unit> {
            last = listingId to favorite
            return result
        }
    }

    private class FakeContacts(
        var result: AppResult<ListingContact> = AppResult.Success(
            ListingContact("+989121111111", "مشاور", "agent"),
        ),
    ) : ListingContactRepository {
        override suspend fun contact(listingId: String) = result
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(
        listings: FakeListings = FakeListings(),
        favorites: FakeFavorites = FakeFavorites(),
        contacts: FakeContacts = FakeContacts(),
        tracker: RecordingTracker = RecordingTracker(),
    ) = PropertyDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("propertyId" to "l1")),
        listings = listings,
        favorites = favorites,
        contacts = contacts,
        storage = StorageBaseUrl("https://x.supabase.co"),
        analytics = tracker,
    )

    @Test
    fun load_success_emitsDetailAndTracksView() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val viewModel = vm(tracker = tracker)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Content)
            assertEquals("l1", (state as UiState.Content).data.detail.listingId)
            cancel()
        }
        assertTrue(tracker.recorded.any { it.name == "property_view" })
    }

    @Test
    fun load_empty_emitsEmpty() = runTest(dispatcher.scheduler) {
        val viewModel = vm(listings = FakeListings(AppResult.Empty))
        viewModel.uiState.test {
            assertEquals(UiState.Empty, expectMostRecentItem())
            cancel()
        }
    }

    @Test
    fun toggleFavorite_success_updatesStateAndTracks() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val favorites = FakeFavorites()
        val viewModel = vm(favorites = favorites, tracker = tracker)

        viewModel.uiState.test {
            expectMostRecentItem() // initial content (may be loading then content)
            // wait until content
        }
        // Ensure content
        val deadline = System.currentTimeMillis() + 2000
        while (viewModel.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        assertTrue(viewModel.uiState.value is UiState.Content)

        viewModel.toggleFavorite()
        val join = System.currentTimeMillis() + 2000
        while (favorites.last == null && System.currentTimeMillis() < join) {
            kotlinx.coroutines.delay(10)
        }
        assertEquals("l1" to true, favorites.last)
        val content = viewModel.uiState.value as UiState.Content
        assertTrue(content.data.detail.isFavorited)
        assertTrue(tracker.recorded.any { it.name == "property_favorite" })
    }

    @Test
    fun toggleFavorite_unauthorized_setsMessage() = runTest(dispatcher.scheduler) {
        val favorites = FakeFavorites(AppResult.Failure(AppError.Unauthorized))
        val viewModel = vm(favorites = favorites)

        val deadline = System.currentTimeMillis() + 2000
        while (viewModel.uiState.value !is UiState.Content && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        viewModel.toggleFavorite()
        val join = System.currentTimeMillis() + 2000
        while ((viewModel.uiState.value as? UiState.Content)?.data?.favoriteError == null &&
            System.currentTimeMillis() < join
        ) {
            kotlinx.coroutines.delay(10)
        }
        val content = viewModel.uiState.value as UiState.Content
        assertNotNull(content.data.favoriteError)
        assertFalse(content.data.detail.isFavorited)
    }
}
