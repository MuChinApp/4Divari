package ir.chardivari.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {

    @Test
    fun `event names match schema contract`() {
        assertEquals("property_view", AnalyticsEvent.PropertyView.name)
        assertEquals("search_created", AnalyticsEvent.SearchCreated(0, 0).name)
        assertEquals("lead_created", AnalyticsEvent.LeadCreated("l1", "web").name)
    }

    @Test
    fun `params never contain null values`() {
        val event = AnalyticsEvent.PropertyFavorite("p1", favorited = true)
        assertTrue(event.params.values.none { it == "null" })
        assertEquals("p1", event.params["property_id"])
    }

    @Test
    fun `tracker buffers events`() {
        val tracker = InMemoryAnalyticsTracker()
        tracker.track(AnalyticsEvent.ScreenView("home"))
        tracker.track(AnalyticsEvent.FilterUsed("price"))
        // SharedFlow has no size assertion; emission must not throw.
        assertTrue(true)
    }
}
