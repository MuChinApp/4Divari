package ir.chardivari.feature.agent

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.AgentDashboard
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
class AgentDashboardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeAgentRepository
    private lateinit var tracker: RecordingTracker

    private fun viewModel() = AgentDashboardViewModel(
        agentRepository = repo,
        analytics = tracker,
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

    @Test
    fun `dashboard success maps server counts`() = runTest {
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        val data = (state as UiState.Content).data
        assertEquals(3, data.filesActive)
        assertEquals(2, data.leadsNew)
        assertEquals(1, data.visitsPending)
        assertTrue(
            tracker.recorded.any {
                it.name == "screen_view" && it.params["screen"] == "agent_dashboard"
            },
        )
    }

    @Test
    fun `dashboard failure is retryable error`() = runTest {
        repo.dashboardResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()

        assertTrue(vm.uiState.value is UiState.Error)
    }

    @Test
    fun `reload after failure recovers`() = runTest {
        repo.dashboardResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()
        assertTrue(vm.uiState.value is UiState.Error)

        repo.dashboardResult = AppResult.Success(
            AgentDashboard(1, 0, 0, 0, 0, 0, 0),
        )
        vm.load()

        assertTrue(vm.uiState.value is UiState.Content)
    }
}
