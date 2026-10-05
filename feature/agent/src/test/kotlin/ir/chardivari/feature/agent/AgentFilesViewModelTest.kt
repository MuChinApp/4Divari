package ir.chardivari.feature.agent

import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.SellerListingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AgentFilesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeAgentRepository
    private lateinit var tracker: RecordingTracker

    private fun item(id: String): SellerListingItem = SellerListingItem(
        id = id,
        status = "ACTIVE",
        dealType = DealType.SALE,
        priceRial = 4_000_000_000,
        depositRial = null,
        rentRial = null,
        publishedAt = "2026-01-01T00:00:00+00:00",
        city = "تهران",
        areaSqm = 80,
        propertyType = PropertyType.APARTMENT,
        coverPath = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeAgentRepository()
        tracker = RecordingTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AgentFilesViewModel(
        agentRepository = repo,
        analytics = tracker,
    )

    @Test
    fun `success maps files into content`() = runTest {
        repo.filesResult = AppResult.Success(listOf(item("l1"), item("l2")))

        val vm = viewModel()
        val state = vm.uiState.value

        assertTrue(state is UiState.Content)
        assertEquals(2, (state as UiState.Content<List<SellerListingItem>>).data.size)
    }

    @Test
    fun `empty files show empty state`() = runTest {
        repo.filesResult = AppResult.Empty

        val vm = viewModel()

        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun `failure surfaces error state`() = runTest {
        repo.filesResult = AppResult.Failure(AppError.Server)

        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals(AppError.Server, (state as UiState.Error).error)
    }

    @Test
    fun `retry after failure recovers`() = runTest {
        repo.filesResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()
        assertTrue(vm.uiState.value is UiState.Error)

        repo.filesResult = AppResult.Success(listOf(item("l1")))
        vm.load()

        assertTrue(vm.uiState.value is UiState.Content)
    }

    @Test
    fun `screen view tracked once on init`() = runTest {
        repo.filesResult = AppResult.Success(listOf(item("l1")))

        viewModel()

        val screens = tracker.recorded.filterIsInstance<AnalyticsEvent.ScreenView>()
        assertEquals("agent_files", screens.first().screen)
    }
}
