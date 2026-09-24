package ir.chardivari.feature.agent

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.UiState
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.RequirementInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLeadsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeAgentRepository
    private lateinit var tracker: RecordingTracker

    private fun viewModel() = AgentLeadsViewModel(
        agentRepository = repo,
        analytics = tracker,
    )

    private fun content(vm: AgentLeadsViewModel): AgentLeadsUi =
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
    fun `load success populates leads`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1")))
        val vm = viewModel()

        assertEquals(listOf("L1"), content(vm).leads.map { it.id })
        assertTrue(tracker.recorded.any { it.name == "agent_leads" })
    }

    @Test
    fun `load empty yields content with no rows not an error`() = runTest {
        repo.leadsResult = AppResult.Empty
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue(content(vm).leads.isEmpty())
    }

    @Test
    fun `stage change updates optimistically on success`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1", stage = "NEW")))
        val vm = viewModel()

        vm.setStage("L1", "CONTACTED")

        assertEquals("CONTACTED", content(vm).leads.first().stage)
        assertEquals(listOf(Triple("L1", "CONTACTED", null)), repo.updateLeadCalls)
        assertTrue(content(vm).busyIds.isEmpty())
    }

    @Test
    fun `stage change reverts and reports on failure`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1", stage = "NEW")))
        repo.updateLeadResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()

        vm.setStage("L1", "OFFER")

        assertEquals("NEW", content(vm).leads.first().stage)
        assertEquals("تغییر انجام نشد؛ دوباره تلاش کنید", content(vm).message)
        assertTrue(content(vm).busyIds.isEmpty())
    }

    @Test
    fun `requirement save validates budget range before calling server`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1")))
        val vm = viewModel()
        vm.openRequirementForm("L1")
        // Empty requirement → blank form prefilled with the listing's deal type.
        assertEquals(DealType.SALE, content(vm).form?.dealType)

        vm.updateForm {
            it.copy(budgetMin = "۹۰۰۰۰", budgetMax = "۱۰۰۰۰")
        }
        vm.saveRequirement()

        assertTrue(repo.upsertCalls.isEmpty())
        assertEquals("حداقل بودجه بیشتر از حداکثر است", content(vm).form?.error)
    }

    @Test
    fun `requirement save parses persian digits and refreshes matches`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1")))
        val vm = viewModel()
        vm.openRequirementForm("L1")
        vm.updateForm {
            it.copy(budgetMin = "۴۰۰۰۰۰۰۰۰۰", budgetMax = "60000000000")
        }
        vm.saveRequirement()

        assertEquals(1, repo.upsertCalls.size)
        val input = repo.upsertCalls.first()
        assertEquals(4_000_000_000L, input.budgetMinRial)
        assertEquals(60_000_000_000L, input.budgetMaxRial)
        assertEquals(listOf("L1"), repo.refreshCalls)
        assertNull(content(vm).form)
        assertTrue(content(vm).message?.contains("تطبیق") == true)
    }

    @Test
    fun `requirement save failure keeps form open with error`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1")))
        repo.upsertResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()
        vm.openRequirementForm("L1")
        vm.saveRequirement()

        assertTrue(content(vm).form != null)
        assertTrue(content(vm).form?.error != null)
        assertTrue(repo.refreshCalls.isEmpty())
    }

    @Test
    fun `notes save failure shows editor error`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1")))
        repo.updateLeadResult = AppResult.Failure(AppError.Server)
        val vm = viewModel()

        vm.openNotesEditor("L1")
        vm.setNotesText("تماس اول: علاقه‌مند به بازدید")
        vm.saveNotes()

        val editor = content(vm).notesEditor
        assertTrue(editor != null)
        assertTrue(editor?.busy == false)
        assertTrue(editor?.error != null)
    }

    @Test
    fun `matches load for expanded lead`() = runTest {
        repo.leadsResult = AppResult.Success(listOf(agentLead(id = "L1")))
        repo.matchesResult = AppResult.Success(
            listOf(
                ir.chardivari.core.marketplace.LeadMatch(
                    listingId = "listing-L1",
                    score = 87.5,
                    explanation = null,
                    address = "تهران، ولنجک",
                    priceRial = 5_000_000_000L,
                    dealType = DealType.SALE,
                ),
            ),
        )
        val vm = viewModel()

        vm.toggleExpanded("L1")

        assertEquals(1, content(vm).matches.size)
        assertEquals(87.5, content(vm).matches.first().score, 0.01)
        assertEquals("L1", content(vm).expandedId)
    }
}
