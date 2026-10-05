package ir.chardivari.feature.assistant

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import ir.chardivari.core.ai.AssistantEngine
import ir.chardivari.core.ai.AssistantSource
import ir.chardivari.core.ai.AssistantTurn
import ir.chardivari.core.ai.PriorTurn
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeEngine(
        var result: AppResult<AssistantTurn> = AppResult.Success(
            AssistantTurn(text = "پاسخ"),
        ),
        var gate: CompletableDeferred<Unit>? = null,
    ) : AssistantEngine {
        val calls = mutableListOf<Pair<List<PriorTurn>, String>>()

        override suspend fun ask(
            priorTurns: List<PriorTurn>,
            userText: String,
        ): AppResult<AssistantTurn> {
            calls += priorTurns to userText
            gate?.await()
            return result
        }
    }

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        override val events: SharedFlow<AnalyticsEvent> =
            MutableSharedFlow(extraBufferCapacity = 16)

        override fun track(event: AnalyticsEvent) {
            recorded += event
            events.tryEmit(event)
        }
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
    fun `send appends turns, clears draft and feeds history`() =
        runTest(dispatcher.scheduler) {
            val engine = FakeEngine(
                result = AppResult.Success(
                    AssistantTurn(
                        text = "۲ نتیجه",
                        toolsUsed = listOf("search_listings"),
                        sources = listOf(
                            AssistantSource("tool", "search_listings", "جست‌وجوی ملک"),
                        ),
                    ),
                ),
            )
            val vm = AssistantViewModel(engine, RecordingTracker())

            vm.onDraftChange("آپارتمان در تهران")
            vm.send()

            val state = vm.uiState.value
            assertThat(state.busy).isFalse()
            assertThat(state.draft).isEmpty()
            assertThat(state.turns).hasSize(2)
            assertThat((state.turns[0] as AssistantTurnUi.User).text)
                .isEqualTo("آپارتمان در تهران")
            assertThat((state.turns[1] as AssistantTurnUi.Answer).turn.text)
                .isEqualTo("۲ نتیجه")

            vm.onDraftChange("گران‌ترینش؟")
            vm.send()
            assertThat(engine.calls).hasSize(2)
            assertThat(engine.calls[1].first).hasSize(1)
            assertThat(engine.calls[1].first.single().userText)
                .isEqualTo("آپارتمان در تهران")
        }

    @Test
    fun `failure restores draft, shows error, retry re-sends`() =
        runTest(dispatcher.scheduler) {
            val engine = FakeEngine(result = AppResult.Failure(AppError.Server))
            val vm = AssistantViewModel(engine, RecordingTracker())

            vm.onDraftChange("سوال سخت")
            vm.send()
            var state = vm.uiState.value
            assertThat(state.busy).isFalse()
            assertThat(state.error).isNotNull()
            assertThat(state.draft).isEqualTo("سوال سخت")
            assertThat(state.turns).isEmpty()

            engine.result = AppResult.Success(AssistantTurn(text = "الان شد"))
            vm.retry()

            state = vm.uiState.value
            assertThat(state.error).isNull()
            assertThat(state.turns).hasSize(2)
            assertThat(engine.calls.last().second).isEqualTo("سوال سخت")
        }

    @Test
    fun `unauthorized maps to login prompt error`() = runTest(dispatcher.scheduler) {
        val engine = FakeEngine(result = AppResult.Failure(AppError.Unauthorized))
        val vm = AssistantViewModel(engine, RecordingTracker())

        vm.onDraftChange("سلام")
        vm.send()

        val error = vm.uiState.value.error
        assertThat(error?.kind).isEqualTo(AssistantErrorKind.LOGIN)
    }

    @Test
    fun `missing backend config maps to not-configured error`() =
        runTest(dispatcher.scheduler) {
            val engine = FakeEngine(
                result = AppResult.Failure(
                    AppError.Client(0, "AI assistant is not configured for this build"),
                ),
            )
            val vm = AssistantViewModel(engine, RecordingTracker())

            vm.onDraftChange("سلام")
            vm.send()

            assertThat(vm.uiState.value.error?.kind)
                .isEqualTo(AssistantErrorKind.NOT_CONFIGURED)
        }

    @Test
    fun `typing clears the error and cancels retry`() = runTest(dispatcher.scheduler) {
        val engine = FakeEngine(result = AppResult.Failure(AppError.Server))
        val vm = AssistantViewModel(engine, RecordingTracker())

        vm.onDraftChange("اولی")
        vm.send()
        assertThat(vm.uiState.value.error).isNotNull()

        vm.onDraftChange("متن جدید")
        assertThat(vm.uiState.value.error).isNull()

        engine.result = AppResult.Success(AssistantTurn(text = "ok"))
        vm.retry()
        assertThat(engine.calls).hasSize(1)
    }

    @Test
    fun `busy guard ignores overlapping sends`() = runTest(dispatcher.scheduler) {
        val engine = FakeEngine(gate = CompletableDeferred())
        val vm = AssistantViewModel(engine, RecordingTracker())

        vm.onDraftChange("اول")
        vm.send()
        assertThat(vm.uiState.value.busy).isTrue()

        vm.onDraftChange("دوم")
        vm.send()
        assertThat(engine.calls).hasSize(1)

        engine.gate!!.complete(Unit)
        // Let the first launch finish.
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.busy).isFalse()
    }

    @Test
    fun `analytics records question tools and fairness`() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val engine = FakeEngine(
            result = AppResult.Success(
                AssistantTurn(
                    text = "متوجه شدم",
                    toolsUsed = listOf("search_listings", "estimate_price_range"),
                    fairnessNotice = "چاردیواری … تمایز قائل نمی‌شود",
                ),
            ),
        )
        val vm = AssistantViewModel(engine, tracker)

        vm.onDraftChange("فقط به زنان")
        vm.send()

        val names = tracker.recorded.map { it.name }
        assertThat(names).contains("screen_view")
        assertThat(names).contains("assistant_question_asked")
        assertThat(names).containsExactly(
            "screen_view",
            "assistant_question_asked",
            "assistant_tool_used",
            "assistant_tool_used",
            "assistant_fairness_blocked",
        ).inOrder()
    }

    @Test
    fun `failure records assistant_failed analytics`() = runTest(dispatcher.scheduler) {
        val tracker = RecordingTracker()
        val engine = FakeEngine(result = AppResult.Failure(AppError.Offline))
        val vm = AssistantViewModel(engine, tracker)

        vm.onDraftChange("تست")
        vm.send()

        val failed = tracker.recorded.filterIsInstance<AnalyticsEvent.AssistantFailed>()
        assertThat(failed).hasSize(1)
        assertThat(failed.single().reason).isEqualTo("Offline")
    }

    @Test
    fun `blank draft does not call the engine`() = runTest(dispatcher.scheduler) {
        val engine = FakeEngine()
        val vm = AssistantViewModel(engine, RecordingTracker())

        vm.onDraftChange("   ")
        vm.send()

        assertThat(engine.calls).isEmpty()
        assertThat(vm.uiState.value.busy).isFalse()
    }
}
