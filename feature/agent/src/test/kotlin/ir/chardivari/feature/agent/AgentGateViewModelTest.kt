package ir.chardivari.feature.agent

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
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
class AgentGateViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeAgentRepository
    private lateinit var tracker: RecordingTracker

    private fun viewModel() = AgentGateViewModel(
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
    fun `agent with role reaches ready with profile name`() = runTest {
        repo.isAgentResult = AppResult.Success(true)
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertEquals(
            AgentGate.Ready(displayName = "سارا رضایی"),
            (state as UiState.Content).data,
        )
        assertTrue(tracker.recorded.any { it.name == "agent" })
    }

    @Test
    fun `non-agent is gated with NotAgent`() = runTest {
        repo.isAgentResult = AppResult.Success(false)
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertEquals(AgentGate.NotAgent, (state as UiState.Content).data)
    }

    @Test
    fun `missing session shows NotLoggedIn`() = runTest {
        repo.isAgentResult = AppResult.Failure(AppError.Unauthorized)
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertEquals(AgentGate.NotLoggedIn, (state as UiState.Content).data)
    }

    @Test
    fun `server failure becomes retryable error`() = runTest {
        repo.isAgentResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals(AppError.Server, (state as UiState.Error).error)
        assertTrue(state.canRetry)
    }
}
