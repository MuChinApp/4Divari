package ir.chardivari.core.analytics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pluggable analytics facade.
 *
 * Phase 1 ships an in-memory buffer (real persistence lands with the
 * backend collector). Events are never dropped silently in debug —
 * they surface in the developer overlay.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    val events: kotlinx.coroutines.flow.SharedFlow<AnalyticsEvent>
}

@Singleton
class InMemoryAnalyticsTracker @Inject constructor() : AnalyticsTracker {
    private val _events = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    override fun track(event: AnalyticsEvent) {
        _events.tryEmit(event)
    }
}
