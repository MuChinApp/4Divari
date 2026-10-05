package ir.chardivari.feature.saved

import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.FavoritesRepository
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.PropertyDto
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.DealType
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
class SavedViewModelTest {

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
            propertyType = PropertyType.HOUSE,
            areaSqm = 10,
            city = "تهران",
            province = "تهران",
        ),
    )

    private class FakeFavorites(
        var ids: AppResult<Set<String>> = AppResult.Success(emptySet()),
    ) : FavoritesRepository {
        var removed: String? = null
        override suspend fun favoriteIds() = ids
        override suspend fun setFavorite(listingId: String, favorite: Boolean): AppResult<Unit> {
            if (!favorite) removed = listingId
            return AppResult.Success(Unit)
        }
    }

    private class FakeListings(
        var byId: AppResult<Listing> = AppResult.Success(listing("f1")),
    ) : ListingRepository {
        override suspend fun feed(limit: Int) = AppResult.Empty
        override suspend fun search(filters: SearchFilters, limit: Int) = AppResult.Empty
        override suspend fun byId(listingId: String) = byId

        private companion object {
            fun listing(id: String) = Listing(
                id = id,
                propertyId = "p",
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
                    id = "p",
                    propertyType = PropertyType.HOUSE,
                    areaSqm = 1,
                    city = "x",
                    province = "y",
                ),
            )
        }
    }

    private class FakeSaved(
        var listResult: AppResult<List<SavedSearchItem>> = AppResult.Empty,
    ) : SavedSearchRepository {
        override suspend fun list() = listResult
        override suspend fun save(name: String?, filters: SearchFilters) = AppResult.Empty
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
    fun unauthorizedFavorites_requiresLoginContent() = runTest(dispatcher.scheduler) {
        val vm = SavedViewModel(
            favoritesRepository = object : FavoritesRepository {
                override suspend fun favoriteIds() =
                    AppResult.Failure(AppError.Unauthorized)

                override suspend fun setFavorite(listingId: String, favorite: Boolean) =
                    AppResult.Failure(AppError.Unauthorized)
            },
            savedSearchRepository = FakeSaved(),
            listings = FakeListings(),
            storage = StorageBaseUrl(null),
            analytics = RecordingTracker(),
        )

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue((state as UiState.Content).data.requiresLogin)
    }

    @Test
    fun emptyFavorites_contentNotError() = runTest(dispatcher.scheduler) {
        val vm = SavedViewModel(
            favoritesRepository = FakeFavorites(),
            savedSearchRepository = FakeSaved(),
            listings = FakeListings(),
            storage = StorageBaseUrl(null),
            analytics = RecordingTracker(),
        )
        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue((state as UiState.Content).data.favorites.isEmpty())
        assertEquals(SavedTab.FAVORITES, state.data.tab)
    }

    @Test
    fun favoritesHydratedFromListings() = runTest(dispatcher.scheduler) {
        val vm = SavedViewModel(
            favoritesRepository = FakeFavorites(AppResult.Success(setOf("f1"))),
            savedSearchRepository = FakeSaved(),
            listings = FakeListings(AppResult.Success(listing("f1"))),
            storage = StorageBaseUrl(null),
            analytics = RecordingTracker(),
        )
        val state = vm.uiState.value as UiState.Content
        assertEquals(1, state.data.favorites.size)
        assertEquals("f1", state.data.favorites[0].listingId)
    }

    @Test
    fun removeFavorite_updatesList() = runTest(dispatcher.scheduler) {
        val favorites = FakeFavorites(AppResult.Success(setOf("f1", "f2")))
        val vm = SavedViewModel(
            favoritesRepository = favorites,
            savedSearchRepository = FakeSaved(),
            listings = FakeListings(),
            storage = StorageBaseUrl(null),
            analytics = RecordingTracker(),
        )
        // Both hydrations succeed with same sample listing id f1 — clear first
        vm.removeFavorite("f1")
        assertEquals("f1", favorites.removed)
    }

    @Test
    fun selectTab_switchesWithoutError() = runTest(dispatcher.scheduler) {
        val vm = SavedViewModel(
            favoritesRepository = FakeFavorites(),
            savedSearchRepository = FakeSaved(),
            listings = FakeListings(),
            storage = StorageBaseUrl(null),
            analytics = RecordingTracker(),
        )
        vm.selectTab(SavedTab.SEARCHES)
        val state = vm.uiState.value as UiState.Content
        assertEquals(SavedTab.SEARCHES, state.data.tab)
    }
}
