package ir.chardivari.feature.map

import com.google.common.truth.Truth.assertThat
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
import ir.chardivari.core.marketplace.SearchFilters
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
class MapViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeListingRepository(
        var searchResult: AppResult<List<Listing>> = AppResult.Empty,
    ) : ListingRepository {
        var lastLimit: Int? = null

        override suspend fun feed(limit: Int): AppResult<List<Listing>> =
            search(SearchFilters.EMPTY, limit)

        override suspend fun search(
            filters: SearchFilters,
            limit: Int,
        ): AppResult<List<Listing>> {
            lastLimit = limit
            return searchResult
        }

        override suspend fun byId(listingId: String): AppResult<Listing> = AppResult.Empty
    }

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        override val events: SharedFlow<AnalyticsEvent> =
            MutableSharedFlow(extraBufferCapacity = 16)

        override fun track(event: AnalyticsEvent) {
            recorded += event
        }
    }

    private fun listing(
        id: String,
        latitude: Double? = 35.68,
        longitude: Double? = 51.38,
        dataSource: String = "REAL",
    ): Listing = Listing(
        id = id,
        propertyId = "p-$id",
        dealType = DealType.SALE,
        priceRial = 5_000_000_000,
        depositRial = null,
        rentRial = null,
        status = "ACTIVE",
        publishedAt = "2026-01-01T00:00:00+00:00",
        verificationStatus = "unverified",
        freshness = "fresh",
        dataSource = dataSource,
        property = PropertyDto(
            id = "p-$id",
            propertyType = PropertyType.APARTMENT,
            areaSqm = 90,
            city = "تهران",
            province = "تهران",
            latitude = latitude,
            longitude = longitude,
        ),
    )

    private lateinit var repo: FakeListingRepository
    private lateinit var tracker: RecordingTracker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeListingRepository()
        tracker = RecordingTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MapViewModel(
        listings = repo,
        analytics = tracker,
    )

    @Test
    fun `success maps listings with coordinates into pins`() = runTest {
        repo.searchResult = AppResult.Success(
            listOf(
                listing("l1"),
                listing("l2", latitude = 35.72, longitude = 51.41),
            ),
        )

        val vm = viewModel()
        val state = vm.uiState.value

        assertTrue(state is UiState.Content)
        val pins = (state as UiState.Content<MapUiModel>).data.pins
        assertEquals(2, pins.size)
        assertEquals("l1", pins[0].listingId)
        assertEquals(35.68, pins[0].latitude, 0.0001)
        assertTrue(pins[0].label.contains("تهران"))
        assertEquals(100, repo.lastLimit)
    }

    @Test
    fun `fixture pins carry the dev label — anti-prototype`() = runTest {
        repo.searchResult = AppResult.Success(
            listOf(
                listing("f1", dataSource = "DEV_FIXTURE"),
                listing("r1", dataSource = "REAL"),
            ),
        )

        val vm = viewModel()
        val state = vm.uiState.value as UiState.Content<MapUiModel>

        assertTrue(state.data.pins[0].label.contains("دادهٔ نمونه"))
        assertTrue(state.data.pins[0].isFixture)
        assertTrue(!state.data.pins[1].label.contains("دادهٔ نمونه"))
    }

    @Test
    fun `listings without coordinates are excluded honestly`() = runTest {
        repo.searchResult = AppResult.Success(
            listOf(
                listing("n1", latitude = null, longitude = null),
                listing("n2", latitude = 35.0, longitude = null),
            ),
        )

        val vm = viewModel()

        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun `empty repository result shows empty state`() = runTest {
        repo.searchResult = AppResult.Empty

        val vm = viewModel()

        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun `failure surfaces error state with the app error`() = runTest {
        repo.searchResult = AppResult.Failure(AppError.Server)

        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals(AppError.Server, (state as UiState.Error).error)
    }

    @Test
    fun `screen view and pin open are tracked`() = runTest {
        repo.searchResult = AppResult.Success(listOf(listing("l1")))
        val vm = viewModel()

        vm.onPinOpen()

        val names = tracker.recorded.map { it.name }
        assertTrue(names.contains("screen_view"))
        val interactions = tracker.recorded
            .filterIsInstance<AnalyticsEvent.MapInteraction>()
        assertEquals(1, interactions.size)
        assertEquals("pin_open", interactions.single().kind)
        val screen = tracker.recorded.filterIsInstance<AnalyticsEvent.ScreenView>()
        assertThat(screen.first().screen).isEqualTo("map")
    }
}
