package ir.chardivari.feature.home

import app.cash.turbine.test
import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class RecordingTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AnalyticsEvent> = flow

        override fun track(event: AnalyticsEvent) {
            recorded += event
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
    fun `load emits content where all sections are unimplemented`() = runTest {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(tracker)
        dispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            val loaded = expectMostRecentItem()
            val model = (loaded as UiState.Content).data
            assertEquals(7, model.sections.size)
            assertTrue(model.sections.none { it.isImplemented })
            assertTrue(model.sections.all { it.title.isNotBlank() })
            cancel()
        }
        assertTrue(tracker.recorded.any { it.name == "screen_view" })
    }

    @Test
    fun `blank search does not emit analytics`() = runTest {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(tracker)
        dispatcher.scheduler.advanceUntilIdle()
        tracker.recorded.clear()

        vm.onSearchSubmitted("   ")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(tracker.recorded.any { it.name == "search_created" })
    }

    @Test
    fun `non-blank search emits search_created`() = runTest {
        val tracker = RecordingTracker()
        val vm = HomeViewModel(tracker)
        dispatcher.scheduler.advanceUntilIdle()
        tracker.recorded.clear()

        vm.onSearchSubmitSafe("آپارتمان دوخوابه")
        assertTrue(tracker.recorded.any { it.name == "search_created" })
    }

    private fun HomeViewModel.onSearchSubmitSafe(q: String) = onSearchSubmitted(q)
}
