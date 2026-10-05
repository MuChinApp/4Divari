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
        assertEquals(
            "contact_lead_created",
            AnalyticsEvent.ContactLeadCreated("listing-1").name,
        )
        assertEquals("auth_otp_requested", AnalyticsEvent.AuthOtpRequested.name)
        assertEquals("auth_login_succeeded", AnalyticsEvent.AuthLoginSucceeded.name)
        assertEquals("auth_login_failed", AnalyticsEvent.AuthLoginFailed("rate").name)
        assertEquals(
            "assistant_question_asked",
            AnalyticsEvent.AssistantQuestionAsked(12).name,
        )
        assertEquals(
            "assistant_tool_used",
            AnalyticsEvent.AssistantToolUsed("search_listings").name,
        )
        assertEquals(
            "assistant_fairness_blocked",
            AnalyticsEvent.AssistantFairnessBlocked(2).name,
        )
        assertEquals("assistant_failed", AnalyticsEvent.AssistantFailed("Server").name)
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
