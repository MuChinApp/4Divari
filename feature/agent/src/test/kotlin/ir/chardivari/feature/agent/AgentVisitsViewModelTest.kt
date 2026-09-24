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
class AgentVisitsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeAgentRepository
    private lateinit var tracker: RecordingTracker

    private fun viewModel() = AgentVisitsViewModel(
        agentRepository = repo,
        analytics = tracker,
    )

    private fun content(vm: AgentVisitsViewModel): AgentVisitsUi =
        (vm.uiState.value as UiState.Content).data

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
    fun `confirming a requested visit updates status`() = runTest {
        repo.visitsResult = AppResult.Success(listOf(agentVisit(id = "V1", status = "REQUESTED")))
        val vm = viewModel()

        vm.setStatus("V1", "CONFIRMED")

        assertEquals("CONFIRMED", content(vm).visits.first().status)
        assertEquals(listOf("V1" to "CONFIRMED"), repo.visitStatusCalls)
        assertEquals("وضعیت بازدید به‌روزرسانی شد", content(vm).message)
    }

    @Test
    fun `illegal transition is blocked client-side without server call`() = runTest {
        repo.visitsResult = AppResult.Success(listOf(agentVisit(id = "V1", status = "COMPLETED")))
        val vm = viewModel()

        vm.setStatus("V1", "CONFIRMED")

        assertTrue(repo.visitStatusCalls.isEmpty())
        assertEquals("COMPLETED", content(vm).visits.first().status)
        assertEquals("این تغییر وضعیت مجاز نیست", content(vm).message)
    }

    @Test
    fun `server failure reverts nothing and reports`() = runTest {
        repo.visitsResult = AppResult.Success(listOf(agentVisit(id = "V1", status = "REQUESTED")))
        repo.visitStatusResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()

        vm.setStatus("V1", "CONFIRMED")

        assertEquals("REQUESTED", content(vm).visits.first().status)
        assertEquals("تغییر وضعیت انجام نشد", content(vm).message)
        assertTrue(content(vm).busyIds.isEmpty())
    }

    @Test
    fun `empty visits render content empty state not error`() = runTest {
        repo.visitsResult = AppResult.Empty
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue(content(vm).visits.isEmpty())
        assertTrue(tracker.recorded.any { it.name == "agent_visits" })
    }

    @Test
    fun `transition table matches stage semantics`() {
        assertTrue(VisitTransitions.canTransition("REQUESTED", "CONFIRMED"))
        assertTrue(VisitTransitions.canTransition("CONFIRMED", "COMPLETED"))
        assertTrue(!VisitTransitions.canTransition("REQUESTED", "COMPLETED"))
        assertTrue(!VisitTransitions.canTransition("CANCELLED", "CONFIRMED"))
        assertEquals(emptyList<String>(), VisitTransitions.available("COMPLETED"))
    }
}
